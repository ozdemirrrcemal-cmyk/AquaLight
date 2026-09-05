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
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owner-scoped control-surface preparation orchestrator.
 *
 * This class owns no feature state. It coordinates family-specific application boundaries and
 * declares a surface ready only after the central runtime owner exposes an authoritative first
 * frame that matches the validated commercial catalog.
 */
internal class DefaultDeviceControlSurfacePreparationOperations(
    private val rootOperations: DeviceRootOperations,
    private val dosingChannelOperations: DeviceDosingChannelOperations,
    private val coolingControlOperations: DeviceCoolingControlOperations
) : DeviceControlSurfacePreparationOperations {

    private val freshlyPreparedSurfaces = ConcurrentHashMap.newKeySet<PreparedSurface>()

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
        val preparedSurface = PreparedSurface(deviceUid, OwnerDeviceFamily.DOSING)
        freshlyPreparedSurfaces.remove(preparedSurface)
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
                freshlyPreparedSurfaces += preparedSurface
                DeviceControlSurfacePreparationResult.Ready
            }
        }
        return result
    }

    private suspend fun prepareCooling(
        deviceUid: String
    ): DeviceControlSurfacePreparationResult {
        val preparedSurface = PreparedSurface(deviceUid, OwnerDeviceFamily.COOLING)
        freshlyPreparedSurfaces.remove(preparedSurface)
        val root = rootOperations.current(deviceUid)
        val result = when {
            root == null -> unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
            !root.matchesCoolingCatalog() ->
                unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
            else -> {
                val authoritative = withTimeoutOrNull(COOLING_SURFACE_PREPARATION_TIMEOUT_MS) {
                    coolingControlOperations.observeControl(deviceUid)
                        .filterIsInstance<DeviceCoolingControlResult.Available>()
                        .first()
                }
                if (authoritative == null) {
                    unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
                } else {
                    freshlyPreparedSurfaces += preparedSurface
                    DeviceControlSurfacePreparationResult.Ready
                }
            }
        }
        return result
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
        if (family !in PREPARED_FAMILIES) return false
        val requestedSurface = PreparedSurface(deviceUid.trim(), family)
        val storedSurface = freshlyPreparedSurfaces.firstOrNull { surface ->
            surface == requestedSurface
        }
        return if (storedSurface == null) {
            false
        } else {
            freshlyPreparedSurfaces.remove(storedSurface)
        }
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

    private companion object {
        const val COOLING_SURFACE_PREPARATION_TIMEOUT_MS = 8_000L
        val PREPARED_FAMILIES = setOf(OwnerDeviceFamily.DOSING, OwnerDeviceFamily.COOLING)
    }
}

private data class PreparedSurface(
    val deviceUid: String,
    val family: OwnerDeviceFamily
)

private fun DeviceRootSnapshot.matchesDosingCatalog(
    expectedSlots: List<DeviceDosingChannelSlot>
): Boolean = catalogState == DeviceRootCatalogState.VALID &&
    family == OwnerDeviceFamily.DOSING &&
    dosingChannelCount == expectedSlots.size

private fun DeviceRootSnapshot.matchesCoolingCatalog(): Boolean =
    catalogState == DeviceRootCatalogState.VALID &&
        family == OwnerDeviceFamily.COOLING &&
        channelSlots.fanOutputs.isNotEmpty() &&
        channelSlots.temperatureSensors.isNotEmpty() &&
        fanOutputCount == channelSlots.fanOutputs.size &&
        temperatureSensorCount == channelSlots.temperatureSensors.size

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
