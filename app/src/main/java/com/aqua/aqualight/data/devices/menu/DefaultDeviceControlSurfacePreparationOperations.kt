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
        // A fresh marker belongs only to the immediately preceding cold preparation/navigation
        // handoff. A later menu attempt must not inherit it after a cancelled or abandoned route.
        freshlyPreparedDeviceUids.remove(deviceUid)
        val root = rootOperations.current(deviceUid)
        return root?.let { snapshot -> prepareDosingRoot(deviceUid, snapshot) }
            ?: unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
    }

    private suspend fun prepareDosingRoot(
        deviceUid: String,
        root: DeviceRootSnapshot
    ): DeviceControlSurfacePreparationResult {
        val expectedSlots = root.channelSlots.dosingChannels
        return when {
            expectedSlots.isEmpty() ->
                unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
            !root.matchesDosingCatalog(expectedSlots) ->
                unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
            hasAuthoritativeSurface(deviceUid, expectedSlots) -> ready(deviceUid)
            !dosingChannelOperations.refreshAll(deviceUid) ->
                unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
            hasAuthoritativeSurface(deviceUid, expectedSlots) -> ready(deviceUid)
            else -> unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
        }
    }

    private fun hasAuthoritativeSurface(
        deviceUid: String,
        expectedSlots: List<DeviceDosingChannelSlot>
    ): Boolean = currentAuthoritative(deviceUid, expectedSlots).matches(deviceUid, expectedSlots)

    private fun ready(deviceUid: String): DeviceControlSurfacePreparationResult {
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
