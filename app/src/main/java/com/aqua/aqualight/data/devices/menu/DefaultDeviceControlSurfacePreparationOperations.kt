package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceTimerChannelSlot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.light.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.matchesLightControlSurface
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.data.devices.timer.matchesTimerCatalog
import java.util.concurrent.ConcurrentHashMap

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
    private val coolingControlOperations: DeviceCoolingControlOperations,
    private val timerControlOperations: DeviceTimerControlOperations,
    private val lightControlOperations: DeviceLightControlOperations
) : DeviceControlSurfacePreparationOperations {

    private val freshlyPreparedSurfaces = ConcurrentHashMap.newKeySet<PreparedSurface>()

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult = when {
        request.deviceUid.trim().isBlank() ->
            unavailable(DeviceMenuUnavailableReason.INVALID_DEVICE_UID)
        request.family == OwnerDeviceFamily.DOSING -> prepareDosing(request.deviceUid.trim())
        request.family == OwnerDeviceFamily.COOLING -> prepareCooling(request.deviceUid.trim())
        request.family == OwnerDeviceFamily.TIMER -> prepareTimer(request.deviceUid.trim())
        request.family == OwnerDeviceFamily.LIGHT -> prepareLight(request.deviceUid.trim())
        else -> DeviceControlSurfacePreparationResult.Ready
    }

    private suspend fun prepareLight(
        deviceUid: String
    ): DeviceControlSurfacePreparationResult {
        val preparedSurface = PreparedSurface(deviceUid, OwnerDeviceFamily.LIGHT)
        freshlyPreparedSurfaces.remove(preparedSurface)
        val root = rootOperations.current(deviceUid)
        return when {
            root == null -> unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
            !root.matchesLightCatalog() ->
                unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
            else -> when (val control = lightControlOperations.refreshControl(deviceUid)) {
                is DeviceLightControlResult.Failed ->
                    unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
                is DeviceLightControlResult.Available -> {
                    if (control.snapshot.matchesLightControlSurface(deviceUid, root)) {
                        freshlyPreparedSurfaces += preparedSurface
                        DeviceControlSurfacePreparationResult.Ready
                    } else {
                        unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
                    }
                }
            }
        }
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
            coolingControlOperations.refreshControl(deviceUid) !is DeviceCoolingControlResult.Available ->
                unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
            else -> {
                freshlyPreparedSurfaces += preparedSurface
                DeviceControlSurfacePreparationResult.Ready
            }
        }
        return result
    }

    private suspend fun prepareTimer(
        deviceUid: String
    ): DeviceControlSurfacePreparationResult {
        val preparedSurface = PreparedSurface(deviceUid, OwnerDeviceFamily.TIMER)
        freshlyPreparedSurfaces.remove(preparedSurface)
        val root = rootOperations.current(deviceUid)
        val expectedSlots = root?.channelSlots?.timerChannels.orEmpty()
        val result = when {
            root == null -> unavailable(DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED)
            !root.matchesTimerCatalog(expectedSlots) ->
                unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
            else -> when (val control = timerControlOperations.refreshControl(deviceUid)) {
                is DeviceTimerControlResult.Failed ->
                    unavailable(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
                is DeviceTimerControlResult.Available -> {
                    if (control.snapshot.matchesTimerSurface(deviceUid, expectedSlots)) {
                        freshlyPreparedSurfaces += preparedSurface
                        DeviceControlSurfacePreparationResult.Ready
                    } else {
                        unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
                    }
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
        val PREPARED_FAMILIES = setOf(
            OwnerDeviceFamily.DOSING,
            OwnerDeviceFamily.COOLING,
            OwnerDeviceFamily.TIMER,
            OwnerDeviceFamily.LIGHT
        )
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

private fun DeviceRootSnapshot.matchesLightCatalog(): Boolean {
    if (catalogState != DeviceRootCatalogState.VALID) return false
    if (family != OwnerDeviceFamily.LIGHT || productKey.isBlank()) return false
    return channelSlots.lightChannels.isNotEmpty() &&
        lightChannelCount == channelSlots.lightChannels.size
}

private fun DeviceTimerControlSnapshot.matchesTimerSurface(
    deviceUid: String,
    expectedSlots: List<DeviceTimerChannelSlot>
): Boolean {
    if (this.deviceUid != deviceUid || channels.size != expectedSlots.size) return false
    return channels.zip(expectedSlots).all { (channel, slot) ->
        channel.slotId == slot.id.value &&
            channel.channelNumber == slot.index.position &&
            channel.defaultName == slot.defaultDisplayName &&
            channel.displayNameEditable == slot.displayNameEditable
    }
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
