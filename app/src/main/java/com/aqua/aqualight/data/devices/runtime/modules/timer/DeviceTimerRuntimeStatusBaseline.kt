package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

internal fun DeviceTimerRuntimeRepository.currentAuthoritativeState(
    deviceUid: DeviceUid
): DeviceTimerRuntimeState? = stateStore.currentAuthoritativeState(deviceUid)

internal suspend fun DeviceTimerRuntimeRepository.ensureGlobalStatus(
    deviceUid: DeviceUid
): TimerStatusBaseline {
    val current = stateStore.currentAuthoritativeState(deviceUid)?.status
    if (current != null && !current.channelScoped) return TimerStatusBaseline.Ready(current)
    return when (val outcome = requestStatus(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> TimerStatusBaseline.Ready(outcome.value)
        else -> TimerStatusBaseline.Failed(outcome)
    }
}

internal suspend fun DeviceTimerRuntimeRepository.ensureChannelDetail(
    deviceUid: DeviceUid,
    channelKey: String
): TimerStatusBaseline = when (val baseline = ensureGlobalStatus(deviceUid)) {
    is TimerStatusBaseline.Failed -> baseline
    is TimerStatusBaseline.Ready -> {
        val normalizedChannelKey = normalizeTimerChannelKey(channelKey)
        val cached = stateStore.currentAuthoritativeState(deviceUid)
            ?.channelDetails
            ?.get(normalizedChannelKey)
            ?.takeIf { detail -> detail.revision == baseline.status.revision }
        cached?.let(TimerStatusBaseline::Ready) ?: requestStatus(
            deviceUid,
            normalizedChannelKey
        ).toTimerStatusBaseline()
    }
}

internal suspend fun <T> DeviceTimerRuntimeRepository.withGlobalStatus(
    deviceUid: DeviceUid,
    block: suspend (DeviceTimerStatus) -> DeviceRuntimeCommandOutcome<T>
): DeviceRuntimeCommandOutcome<T> = when (val baseline = ensureGlobalStatus(deviceUid)) {
    is TimerStatusBaseline.Ready -> block(baseline.status)
    is TimerStatusBaseline.Failed -> baseline.outcome.asTimerFailure()
}

private fun DeviceRuntimeCommandOutcome<DeviceTimerStatus>.toTimerStatusBaseline() = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> TimerStatusBaseline.Ready(value)
    else -> TimerStatusBaseline.Failed(this)
}

internal fun <T> DeviceRuntimeCommandOutcome<*>.asTimerFailure(): DeviceRuntimeCommandOutcome<T> =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success<*> -> error("A successful outcome is not a failure.")
        is DeviceRuntimeCommandOutcome.NotConnected -> this
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> this
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> this
        is DeviceRuntimeCommandOutcome.SendFailed -> this
        is DeviceRuntimeCommandOutcome.Timeout -> this
        is DeviceRuntimeCommandOutcome.FirmwareError -> this
        is DeviceRuntimeCommandOutcome.ProtocolError -> this
        is DeviceRuntimeCommandOutcome.Cancelled -> this
    }

internal sealed interface TimerStatusBaseline {
    data class Ready(val status: DeviceTimerStatus) : TimerStatusBaseline
    data class Failed(val outcome: DeviceRuntimeCommandOutcome<*>) : TimerStatusBaseline
}
