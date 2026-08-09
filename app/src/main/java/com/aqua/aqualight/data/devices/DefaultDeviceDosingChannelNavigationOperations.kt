package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus

/** Resolves navigation only after a fresh authenticated Dosing status response. */
internal class DefaultDeviceDosingChannelNavigationOperations(
    private val runtimePort: DeviceDosingChannelNavigationRuntimePort
) : DeviceDosingChannelNavigationOperations {

    constructor(devicesRepository: DevicesRepository) : this(
        RepositoryDeviceDosingChannelNavigationRuntimePort(
            devicesRepository = devicesRepository,
            menuAccessOperations = DefaultDeviceMenuAccessOperations.create(devicesRepository)
        )
    )

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? {
        val request = navigationRequest(deviceUid, slotId)
        return if (request == null || !runtimePort.prepareRuntime(request.uid)) {
            null
        } else {
            navigationContext(request)
                ?.let { context -> requestTarget(context) }
        }
    }

    private fun navigationRequest(
        deviceUid: String,
        slotId: String
    ): DosingChannelNavigationRequest? {
        val normalizedDeviceUid = deviceUid.trim()
        val normalizedSlotId = slotId.trim()
        return normalizedDeviceUid
            .takeIf { uid -> uid.isNotBlank() && normalizedSlotId.isNotBlank() }
            ?.let(::DeviceUid)
            ?.let { uid -> DosingChannelNavigationRequest(uid, normalizedSlotId) }
    }

    private fun navigationContext(
        request: DosingChannelNavigationRequest
    ): DosingChannelNavigationContext? {
        val root = runtimePort.currentRootSnapshot(request.uid)
        val slot = root?.authorizedDosingSlot(request.slotId)
        return slot?.let { DosingChannelNavigationContext(request.uid, root, it) }
    }

    private suspend fun requestTarget(
        context: DosingChannelNavigationContext
    ): DeviceDosingChannelNavigationTarget? = runtimePort
        .requestStatus(context.uid)
        ?.toNavigationTarget(context)

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

    private data class DosingChannelNavigationRequest(
        val uid: DeviceUid,
        val slotId: String
    )

    private data class DosingChannelNavigationContext(
        val uid: DeviceUid,
        val root: DeviceRootSnapshot,
        val slot: DeviceDosingChannelSlot
    )
}

internal interface DeviceDosingChannelNavigationRuntimePort {
    suspend fun prepareRuntime(deviceUid: DeviceUid): Boolean
    fun currentRootSnapshot(deviceUid: DeviceUid): DeviceRootSnapshot?
    suspend fun requestStatus(deviceUid: DeviceUid): DeviceDosingStatus?
}

private class RepositoryDeviceDosingChannelNavigationRuntimePort(
    private val devicesRepository: DevicesRepository,
    private val menuAccessOperations: DeviceMenuAccessOperations
) : DeviceDosingChannelNavigationRuntimePort {

    override suspend fun prepareRuntime(deviceUid: DeviceUid): Boolean =
        when (val access = menuAccessOperations.resolve(deviceUid.value)) {
            is DeviceMenuAccessResult.Available ->
                access.deviceUid == deviceUid.value && access.family == OwnerDeviceFamily.DOSING
            is DeviceMenuAccessResult.Unavailable -> false
        }

    override fun currentRootSnapshot(deviceUid: DeviceUid): DeviceRootSnapshot? =
        devicesRepository.currentDevice(deviceUid)?.toDeviceRootSnapshot()

    override suspend fun requestStatus(deviceUid: DeviceUid): DeviceDosingStatus? {
        val runtime = devicesRepository.runtimeModules()?.dosing
        return when (val outcome = runtime?.requestStatus(deviceUid)) {
            is DeviceRuntimeCommandOutcome.Success -> outcome.value
            else -> null
        }
    }
}
