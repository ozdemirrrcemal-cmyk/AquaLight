package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceTimerChannelSlot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.data.devices.timer.matchesTimerCatalog
import java.util.concurrent.ConcurrentHashMap

/** Fixture-aware preparation decorator that keeps production preparation intact for real UIDs. */
internal class DebugFixtureControlSurfacePreparationOperations(
    private val delegate: DeviceControlSurfacePreparationOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val timerControlOperations: DeviceTimerControlOperations
) : DeviceControlSurfacePreparationOperations {

    private val freshlyPreparedTimers = ConcurrentHashMap.newKeySet<String>()

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult {
        val deviceUid = request.deviceUid.trim()
        val root = fixtures.rootSnapshot(deviceUid)
        return when {
            root == null -> delegate.prepare(request)
            request.family != root.family -> unavailable(
                DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
            )
            request.family == OwnerDeviceFamily.TIMER -> prepareTimer(deviceUid, root)
            else -> delegate.prepare(request)
        }
    }

    override fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean = if (isFixtureTimer(deviceUid, family)) {
        freshlyPreparedTimers.remove(deviceUid.trim())
    } else {
        delegate.consumeFreshPreparation(deviceUid, family)
    }

    override fun discardFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ) {
        if (isFixtureTimer(deviceUid, family)) {
            freshlyPreparedTimers.remove(deviceUid.trim())
        } else {
            delegate.discardFreshPreparation(deviceUid, family)
        }
    }

    private suspend fun prepareTimer(
        deviceUid: String,
        root: DeviceRootSnapshot
    ): DeviceControlSurfacePreparationResult {
        freshlyPreparedTimers.remove(deviceUid)
        val expectedSlots = root.channelSlots.timerChannels
        return when {
            !root.matchesTimerCatalog(expectedSlots) -> unavailable(
                DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
            )
            else -> when (val control = timerControlOperations.refreshControl(deviceUid)) {
                is DeviceTimerControlResult.Failed -> unavailable(
                    DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                )
                is DeviceTimerControlResult.Available -> {
                    if (control.snapshot.matchesFixtureTimerSurface(deviceUid, expectedSlots)) {
                        freshlyPreparedTimers += deviceUid
                        DeviceControlSurfacePreparationResult.Ready
                    } else {
                        unavailable(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH)
                    }
                }
            }
        }
    }

    private fun isFixtureTimer(deviceUid: String, family: OwnerDeviceFamily): Boolean =
        family == OwnerDeviceFamily.TIMER &&
            fixtures.rootSnapshot(deviceUid)?.family == OwnerDeviceFamily.TIMER
}

private fun DeviceTimerControlSnapshot.matchesFixtureTimerSurface(
    deviceUid: String,
    expectedSlots: List<DeviceTimerChannelSlot>
): Boolean = this.deviceUid == deviceUid &&
    channels.size == expectedSlots.size &&
    channels.zip(expectedSlots).all { (channel, slot) ->
        channel.slotId == slot.id.value &&
            channel.channelNumber == slot.index.position &&
            channel.defaultName == slot.defaultDisplayName &&
            channel.displayNameEditable == slot.displayNameEditable
    }

private fun unavailable(
    reason: DeviceMenuUnavailableReason
): DeviceControlSurfacePreparationResult =
    DeviceControlSurfacePreparationResult.Unavailable(reason)
