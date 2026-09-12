package com.aqua.aqualight.ui.tabs.devices.detail.timer

import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState

internal sealed interface DeviceTimerPowerMutation {
    val regime: DeviceTimerChannelRegime

    data class Persistent(
        override val regime: DeviceTimerChannelRegime
    ) : DeviceTimerPowerMutation

    data class ProgramPreservingOverride(
        override val regime: DeviceTimerChannelRegime,
        val durationMillis: Long
    ) : DeviceTimerPowerMutation
}

internal fun DeviceTimerChannelUiState.powerMutation(
    currentEpochMillis: Long
): DeviceTimerPowerMutation {
    val targetRegime = when (operatingState) {
        DeviceTimerOperatingState.ON -> DeviceTimerChannelRegime.OFF
        DeviceTimerOperatingState.OFF -> DeviceTimerChannelRegime.ON
    }
    return if (regime == DeviceTimerChannelRegime.AUTO) {
        DeviceTimerPowerMutation.ProgramPreservingOverride(
            regime = targetRegime,
            durationMillis = programOverrideDurationMillis(currentEpochMillis)
        )
    } else {
        DeviceTimerPowerMutation.Persistent(targetRegime)
    }
}

internal fun DeviceTimerChannelUiState.isPowerWriteEnabled(
    persistentWriteEnabled: Boolean,
    temporaryOverrideWriteEnabled: Boolean
): Boolean = if (regime == DeviceTimerChannelRegime.AUTO) {
    temporaryOverrideWriteEnabled
} else {
    persistentWriteEnabled
}

internal suspend fun DeviceTimerControlOperations.executePowerMutation(
    deviceUid: String,
    slotId: String,
    mutation: DeviceTimerPowerMutation
): DeviceTimerControlResult = when (mutation) {
    is DeviceTimerPowerMutation.Persistent -> setRegime(
        deviceUid = deviceUid,
        slotId = slotId,
        regime = mutation.regime
    )
    is DeviceTimerPowerMutation.ProgramPreservingOverride -> setTemporaryOverride(
        deviceUid = deviceUid,
        slotId = slotId,
        regime = mutation.regime,
        durationMillis = mutation.durationMillis
    )
}

private fun DeviceTimerChannelUiState.programOverrideDurationMillis(
    currentEpochMillis: Long
): Long {
    // Firmware owns the next transition. The client only converts that authoritative
    // deadline to the bounded uptime duration required by timer.channel.set.
    val transitionAt = nextTransitionAtEpochMillis
        ?: return MAX_TEMPORARY_OVERRIDE_DURATION_MILLIS
    return (transitionAt - currentEpochMillis).coerceIn(
        MIN_TEMPORARY_OVERRIDE_DURATION_MILLIS,
        MAX_TEMPORARY_OVERRIDE_DURATION_MILLIS
    )
}

private const val MIN_TEMPORARY_OVERRIDE_DURATION_MILLIS = 1L
private const val MAX_TEMPORARY_OVERRIDE_DURATION_MILLIS = 86_400_000L
