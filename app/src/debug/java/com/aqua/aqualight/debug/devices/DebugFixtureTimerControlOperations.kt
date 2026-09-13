package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerCommandFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stateless debug adapter over one central fixture runtime; real UIDs retain production wiring. */
internal class DebugFixtureTimerControlOperations(
    private val delegate: DeviceTimerControlOperations,
    private val runtime: DebugTimerFixtureRuntime
) : DeviceTimerControlOperations {

    override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> =
        if (runtime.contains(deviceUid)) {
            runtime.observe(deviceUid).map { snapshot -> snapshot.toFixtureResult() }
        } else {
            delegate.observeControl(deviceUid)
        }

    override fun currentControl(deviceUid: String): DeviceTimerControlResult =
        if (runtime.contains(deviceUid)) {
            runtime.current(deviceUid).toFixtureResult()
        } else {
            delegate.currentControl(deviceUid)
        }

    override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult =
        if (runtime.contains(deviceUid)) {
            runtime.current(deviceUid).toFixtureResult()
        } else {
            delegate.refreshControl(deviceUid)
        }

    override suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult = if (runtime.contains(deviceUid)) {
        runtime.current(deviceUid).toFixtureChannelResult(slotId)
    } else {
        delegate.refreshChannel(deviceUid, slotId)
    }

    override suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult = if (runtime.contains(deviceUid)) {
        runtime.updateChannel(deviceUid, slotId) { channel, nowMillis ->
            channel.withFixtureRegime(regime, nowMillis)
        }.toFixtureResult()
    } else {
        delegate.setRegime(deviceUid, slotId, regime)
    }

    override suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult = when {
        !runtime.contains(deviceUid) ->
            delegate.setTemporaryOverride(deviceUid, slotId, regime, durationMillis)
        regime == DeviceTimerChannelRegime.AUTO || durationMillis <= 0L ->
            rejected(DeviceTimerCommandFailure.INVALID_REQUEST)
        else -> runtime.updateChannel(
            deviceUid = deviceUid,
            slotId = slotId,
            persistentChange = false
        ) { channel, _ ->
            channel.withFixtureTemporaryOverride(regime, durationMillis)
        }.toFixtureResult()
    }

    override suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult = if (!runtime.contains(deviceUid)) {
        delegate.setDisplayName(deviceUid, slotId, update)
    } else {
        val currentChannel = runtime.current(deviceUid)
            ?.channels
            ?.singleOrNull { channel -> channel.slotId == slotId.trim() }
        when {
            currentChannel == null -> failed(DeviceTimerControlFailure.Unsupported)
            !currentChannel.displayNameEditable ->
                rejected(DeviceTimerCommandFailure.INVALID_CONFIGURATION)
            else -> runtime.updateChannel(deviceUid, slotId) { channel, _ ->
                val displayName = when (update) {
                    DeviceTimerDisplayNameUpdate.ResetToDefault -> channel.defaultName
                    is DeviceTimerDisplayNameUpdate.Value -> update.displayName
                }
                channel.withFixtureDisplayName(displayName)
            }.toFixtureResult()
        }
    }

    override suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        expectedRevision: Long,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult = when {
        !runtime.contains(deviceUid) -> delegate.replaceSchedules(
            deviceUid,
            slotId,
            expectedRevision,
            schedules
        )
        expectedRevision != requireNotNull(runtime.current(deviceUid)).revision ->
            rejected(DeviceTimerCommandFailure.CONFLICT)
        schedules.size > requireNotNull(runtime.current(deviceUid)).maxSchedulesPerChannel ->
            rejected(DeviceTimerCommandFailure.INVALID_CONFIGURATION)
        else -> runtime.updateChannel(deviceUid, slotId) { channel, nowMillis ->
            channel.withFixtureSchedules(schedules, nowMillis)
        }.toFixtureResult()
    }
}

private fun DeviceTimerControlSnapshot?.toFixtureResult(): DeviceTimerControlResult =
    this?.let(DeviceTimerControlResult::Available)
        ?: failed(DeviceTimerControlFailure.Unsupported)

private fun DeviceTimerControlSnapshot?.toFixtureChannelResult(
    slotId: String
): DeviceTimerControlResult = this
    ?.takeIf { snapshot ->
        snapshot.channels.any { channel -> channel.slotId == slotId.trim() }
    }
    ?.let(DeviceTimerControlResult::Available)
    ?: failed(DeviceTimerControlFailure.Unsupported)

private fun failed(failure: DeviceTimerControlFailure): DeviceTimerControlResult =
    DeviceTimerControlResult.Failed(failure)

private fun rejected(reason: DeviceTimerCommandFailure): DeviceTimerControlResult =
    failed(DeviceTimerControlFailure.Rejected(reason))
