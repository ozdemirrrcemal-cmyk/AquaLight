package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first

/**
 * Owner-scoped preparation orchestrator. It owns no device state: Dosing presentation continues to
 * live exclusively in the central Dosing state owner behind [DeviceDosingChannelOperations].
 */
internal class DefaultDeviceControlSurfacePreparationOperations(
    private val rootOperations: DeviceRootOperations,
    private val dosingChannelOperations: DeviceDosingChannelOperations
) : DeviceControlSurfacePreparationOperations {

    private val freshlyPreparedDeviceUids = ConcurrentHashMap.newKeySet<String>()

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult {
        val deviceUid = request.deviceUid.trim()
        if (deviceUid.isBlank()) {
            return unavailable(DeviceMenuUnavailableReason.INVALID_DEVICE_UID)
        }
        if (request.family != OwnerDeviceFamily.DOSING) {
            return DeviceControlSurfacePreparationResult.Ready
        }

        val root = rootOperations.current(deviceUid)
            ?: return unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
        val expectedSlots = root.channelSlots.dosingChannels
        if (
            root.catalogState != DeviceRootCatalogState.VALID ||
            root.family != OwnerDeviceFamily.DOSING ||
            expectedSlots.isEmpty() ||
            root.dosingChannelCount != expectedSlots.size
        ) {
            return unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
        }

        if (currentPresentation(deviceUid).matches(deviceUid, expectedSlots)) {
            return DeviceControlSurfacePreparationResult.Ready
        }

        freshlyPreparedDeviceUids.remove(deviceUid)
        if (!dosingChannelOperations.refreshAll(deviceUid)) {
            return unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
        }
        if (!currentPresentation(deviceUid).matches(deviceUid, expectedSlots)) {
            return unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
        }

        freshlyPreparedDeviceUids += deviceUid
        return DeviceControlSurfacePreparationResult.Ready
    }

    override fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean {
        if (family != OwnerDeviceFamily.DOSING) return false
        return freshlyPreparedDeviceUids.remove(deviceUid.trim())
    }

    private suspend fun currentPresentation(deviceUid: String): List<DeviceDosingChannelSnapshot> =
        dosingChannelOperations.observeAll(deviceUid).first()

    private fun unavailable(
        reason: DeviceMenuUnavailableReason
    ): DeviceControlSurfacePreparationResult.Unavailable =
        DeviceControlSurfacePreparationResult.Unavailable(reason)
}

private fun List<DeviceDosingChannelSnapshot>.matches(
    deviceUid: String,
    expectedSlots: List<DeviceDosingChannelSlot>
): Boolean {
    if (size != expectedSlots.size) return false
    val orderedChannels = sortedBy(DeviceDosingChannelSnapshot::channelNumber)
    val orderedSlots = expectedSlots.sortedBy { slot -> slot.index.position }
    return orderedChannels.zip(orderedSlots).all { (channel, slot) ->
        channel.deviceUid == deviceUid &&
            channel.slotId == slot.id.value &&
            channel.channelNumber == slot.index.position &&
            channel.pumpCount == expectedSlots.size
    }
}
