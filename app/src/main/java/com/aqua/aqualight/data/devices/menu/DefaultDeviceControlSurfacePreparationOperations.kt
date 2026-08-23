package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * Owner-scoped control-surface preparation orchestrator.
 *
 * This class owns no Dosing state. It forces a fresh device-wide read through the existing central
 * Dosing operations and declares the surface ready only when those same operations expose the exact
 * authoritative channel set expected by the validated commercial catalog.
 */
internal class DefaultDeviceControlSurfacePreparationOperations(
    private val rootOperations: DeviceRootOperations,
    private val dosingChannelOperations: DeviceDosingChannelOperations
) : DeviceControlSurfacePreparationOperations {

    private val freshlyPreparedDeviceUids = ConcurrentHashMap.newKeySet<String>()

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult = when {
        request.deviceUid.trim().isBlank() ->
            unavailable(DeviceMenuUnavailableReason.INVALID_DEVICE_UID)
        request.family != OwnerDeviceFamily.DOSING ->
            DeviceControlSurfacePreparationResult.Ready
        else -> prepareDosing(request.deviceUid.trim())
    }

    private suspend fun prepareDosing(
        deviceUid: String
    ): DeviceControlSurfacePreparationResult {
        freshlyPreparedDeviceUids.remove(deviceUid)
        val root = rootOperations.current(deviceUid)
            ?: return unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
        val expectedSlots = root.channelSlots.dosingChannels

        if (
            expectedSlots.isEmpty() ||
            !root.matchesDosingCatalog(expectedSlots)
        ) {
            return unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
        }

        // A menu open is a freshness boundary: liveness/catalog validation is followed by one
        // authoritative Dosing refresh before navigation, even when an older complete projection
        // is still available for presentation continuity.
        if (!dosingChannelOperations.refreshAll(deviceUid)) {
            return unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
        }
        if (!hasAuthoritativeSurface(deviceUid, expectedSlots)) {
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

    private fun hasAuthoritativeSurface(
        deviceUid: String,
        expectedSlots: List<DeviceDosingChannelSlot>
    ): Boolean = currentAuthoritative(deviceUid, expectedSlots).matches(deviceUid, expectedSlots)

    private fun currentAuthoritative(
        deviceUid: String,
        expectedSlots: List<DeviceDosingChannelSlot>
    ): List<DeviceDosingChannelSnapshot> = expectedSlots.mapNotNull { slot ->
        dosingChannelOperations.current(deviceUid, slot.id.value)
    }

    private fun unavailable(
        reason: DeviceMenuUnavailableReason
    ): DeviceControlSurfacePreparationResult.Unavailable =
        DeviceControlSurfacePreparationResult.Unavailable(reason)
}

private fun DeviceRootSnapshot.matchesDosingCatalog(
    expectedSlots: List<DeviceDosingChannelSlot>
): Boolean = catalogState == DeviceRootCatalogState.VALID &&
    family == OwnerDeviceFamily.DOSING &&
    dosingChannelCount == expectedSlots.size

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
