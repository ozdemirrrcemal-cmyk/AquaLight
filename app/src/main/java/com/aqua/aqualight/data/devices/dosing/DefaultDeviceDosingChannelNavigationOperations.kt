package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import kotlinx.coroutines.flow.first

/** Resolves Dosing navigation from the same authoritative channel boundary used by the screens. */
internal class DefaultDeviceDosingChannelNavigationOperations(
    private val rootOperations: DeviceRootOperations,
    private val channelOperations: DeviceDosingChannelOperations
) : DeviceDosingChannelNavigationOperations {

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? {
        val normalizedDeviceUid = deviceUid.trim()
        val normalizedSlotId = slotId.trim()
        if (normalizedDeviceUid.isEmpty() || normalizedSlotId.isEmpty()) return null
        val snapshot = channelOperations.observe(normalizedDeviceUid, normalizedSlotId).first()
            ?: return null
        return resolveSnapshot(normalizedDeviceUid, normalizedSlotId, snapshot)
    }

    override suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? {
        val normalizedDeviceUid = deviceUid.trim()
        val normalizedSlotId = slotId.trim()
        if (normalizedDeviceUid.isEmpty() || normalizedSlotId.isEmpty()) return null
        val refreshed = channelOperations.refresh(normalizedDeviceUid, normalizedSlotId)
        val snapshot = (refreshed as? DeviceDosingChannelOperationResult.Success)?.snapshot
            ?: return null
        return resolveSnapshot(normalizedDeviceUid, normalizedSlotId, snapshot)
    }

    private fun resolveSnapshot(
        deviceUid: String,
        slotId: String,
        channel: DeviceDosingChannelSnapshot
    ): DeviceDosingChannelNavigationTarget? {
        val root = rootOperations.current(deviceUid)?.authorizedDosingRoot(deviceUid) ?: return null
        val catalogChannels = root.channelSlots.dosingChannels
        val pumpCount = catalogChannels.size.takeIf(::isSupportedDosingPumpCount) ?: return null
        val slot = catalogChannels.singleOrNull { candidate -> candidate.id.value == slotId }
            ?: return null
        if (
            channel.deviceUid != deviceUid ||
            channel.slotId != slot.id.value ||
            channel.pumpCount != pumpCount ||
            channel.channelNumber != slot.index.position
        ) {
            return null
        }
        val destination = DeviceDosingChannelDestinationPolicy.resolve(
            calibrated = channel.calibrated,
            allowedRoutes = root.allowedRoutes
        ) ?: return null
        return DeviceDosingChannelNavigationTarget(
            deviceUid = channel.deviceUid,
            slotId = channel.slotId,
            pumpCount = channel.pumpCount,
            channelNumber = channel.channelNumber,
            lastCalibratedAtEpochSeconds = channel.lastCalibratedAtEpochSeconds,
            destination = destination
        )
    }
}

private fun DeviceRootSnapshot.authorizedDosingRoot(deviceUid: String): DeviceRootSnapshot? =
    takeIf { root ->
        root.deviceUid == deviceUid &&
            root.catalogState == DeviceRootCatalogState.VALID &&
            root.family == OwnerDeviceFamily.DOSING
    }

private fun isSupportedDosingPumpCount(count: Int): Boolean = count == 2 || count == 4
