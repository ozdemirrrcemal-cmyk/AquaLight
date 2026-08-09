package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus

/** Resolves navigation only after a fresh authenticated Dosing status response. */
internal class DefaultDeviceDosingChannelNavigationOperations(
    private val devicesRepository: DevicesRepository
) : DeviceDosingChannelNavigationOperations {

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = navigationContext(deviceUid, slotId)
        ?.let { context -> requestTarget(context) }

    private fun navigationContext(
        deviceUid: String,
        slotId: String
    ): DosingChannelNavigationContext? {
        val normalizedDeviceUid = deviceUid.trim()
        val normalizedSlotId = slotId.trim()
        val uid = normalizedDeviceUid
            .takeIf { uid -> uid.isNotBlank() && normalizedSlotId.isNotBlank() }
            ?.let(::DeviceUid)
        val root = uid?.let(devicesRepository::currentDevice)?.toDeviceRootSnapshot()
        val slot = root?.authorizedDosingSlot(normalizedSlotId)
        return if (uid == null || root == null || slot == null) {
            null
        } else {
            DosingChannelNavigationContext(uid = uid, root = root, slot = slot)
        }
    }

    private suspend fun requestTarget(
        context: DosingChannelNavigationContext
    ): DeviceDosingChannelNavigationTarget? {
        val runtime = devicesRepository.runtimeModules()?.dosing
        return when (val outcome = runtime?.requestStatus(context.uid)) {
            is DeviceRuntimeCommandOutcome.Success ->
                outcome.value.toNavigationTarget(context)
            else -> null
        }
    }

    private fun DeviceRootSnapshot.authorizedDosingSlot(slotId: String) =
        takeIf { snapshot ->
            snapshot.catalogState == DeviceRootCatalogState.VALID &&
                snapshot.family == OwnerDeviceFamily.DOSING
        }
            ?.channelSlots
            ?.dosingChannels
            ?.singleOrNull { slot -> slot.id.value == slotId }

    private fun DeviceDosingStatus.toNavigationTarget(
        context: DosingChannelNavigationContext
    ): DeviceDosingChannelNavigationTarget? = channels
        .singleOrNull { channel ->
            channel.index == context.slot.index.zeroBased &&
                channel.key == context.slot.wireKey.value
        }
        ?.takeIf { channel ->
            channel.editable.displayName == context.slot.displayNameEditable
        }
        ?.let { channel ->
            DeviceDosingChannelDestinationPolicy.resolve(
                calibrated = channel.dosing.calibrated,
                allowedRoutes = context.root.allowedRoutes
            )?.let { destination ->
                DeviceDosingChannelNavigationTarget(
                    deviceUid = context.uid.value,
                    slotId = context.slot.id.value,
                    channelTitle = channel.displayName.ifBlank {
                        context.slot.defaultDisplayName
                    },
                    destination = destination
                )
            }
        }

    private data class DosingChannelNavigationContext(
        val uid: DeviceUid,
        val root: DeviceRootSnapshot,
        val slot: DeviceDosingChannelSlot
    )
}
