package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
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

    private val freshlyPreparedSurfaces = ConcurrentHashMap.newKeySet<PreparedSurfaceKey>()

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult = when {
        request.deviceUid.trim().isBlank() ->
            unavailable(DeviceMenuUnavailableReason.INVALID_DEVICE_UID)
        request.family == OwnerDeviceFamily.DOSING -> prepareDosing(request.deviceUid.trim())
        request.family == OwnerDeviceFamily.COOLING -> prepareCooling(request.deviceUid.trim())
        else -> DeviceControlSurfacePreparationResult.Ready
    }

    private suspend fun prepareDosing(
        deviceUid: String
    ): DeviceControlSurfacePreparationResult {
        val preparationKey = PreparedSurfaceKey(deviceUid, OwnerDeviceFamily.DOSING)
        freshlyPreparedSurfaces.remove(preparationKey)
        val root = rootOperations.current(deviceUid)
        val result = when {
            root == null ->
                unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
            root.channelSlots.dosingChannels.isEmpty() ||
                !root.matchesDosingCatalog(root.channelSlots.dosingChannels) ->
                unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
            // A menu open is a freshness boundary: liveness/catalog validation is followed by one
            // authoritative Dosing refresh before navigation, even when an older complete projection
            // is still available for presentation continuity.
            !dosingChannelOperations.refreshAll(deviceUid) ->
                unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
            !hasAuthoritativeSurface(deviceUid, root.channelSlots.dosingChannels) ->
                unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
            else -> {
                freshlyPreparedSurfaces += preparationKey
                DeviceControlSurfacePreparationResult.Ready
            }
        }
        return result
    }

    private fun prepareCooling(deviceUid: String): DeviceControlSurfacePreparationResult {
        val preparationKey = PreparedSurfaceKey(deviceUid, OwnerDeviceFamily.COOLING)
        freshlyPreparedSurfaces.remove(preparationKey)
        val root = rootOperations.current(deviceUid)
        return when {
            root == null -> unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
            root.family != OwnerDeviceFamily.COOLING ||
                root.catalogState != DeviceRootCatalogState.VALID ||
                root.fanOutputCount !in MIN_COOLING_FAN_COUNT..MAX_COOLING_FAN_COUNT ||
                root.temperatureSensorCount != COOLING_TEMPERATURE_SENSOR_COUNT ->
                unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
            root.availability != OwnerDeviceAvailability.REACHABLE ->
                unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
            else -> {
                freshlyPreparedSurfaces += preparationKey
                DeviceControlSurfacePreparationResult.Ready
            }
        }
    }

    override fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean = removeFreshPreparation(deviceUid, family)

    override fun discardFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ) {
        removeFreshPreparation(deviceUid, family)
    }

    private fun removeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean {
        if (family != OwnerDeviceFamily.DOSING && family != OwnerDeviceFamily.COOLING) return false
        return freshlyPreparedSurfaces.remove(PreparedSurfaceKey(deviceUid.trim(), family))
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

private data class PreparedSurfaceKey(
    val deviceUid: String,
    val family: OwnerDeviceFamily
)

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

private const val MIN_COOLING_FAN_COUNT = 1
private const val MAX_COOLING_FAN_COUNT = 3
private const val COOLING_TEMPERATURE_SENSOR_COUNT = 1
