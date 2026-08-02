package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class DeviceTimerRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    private val stateStore: DeviceTimerRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess
) {
    val states: StateFlow<Map<DeviceUid, DeviceTimerRuntimeState>> = stateStore.states

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceTimerStatus> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.STATUS_GET)
        }
        return gateway.execute(
            deviceUid,
            timerJsonCommand(
                action = DeviceTimerRuntimeContract.Action.STATUS_GET,
                parser = { data ->
                    DeviceTimerStatusParser.parse(data).also { status ->
                        DeviceTimerCommandValidation.validateStatus(status, access)
                    }
                }
            )
        ).recordSuccess { status -> stateStore.recordStatus(deviceUid, status) }
    }

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceTimerConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
        val access = accessProvider(deviceUid)
        if (configUnsupported(payload, access)) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CONFIG_APPLY)
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            timerJsonCommand(
                action = DeviceTimerRuntimeContract.Action.CONFIG_APPLY,
                dataFactory = {
                    DeviceTimerCommandValidation.validateConfigRequest(payload, status, access)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceTimerMutationParser.parseConfigApply(data).also { result ->
                        DeviceTimerCommandValidation.validateConfigResult(
                            payload,
                            result,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordSuccess { result -> stateStore.recordConfig(deviceUid, result) }
    }

    suspend fun setChannel(
        deviceUid: DeviceUid,
        payload: DeviceTimerChannelSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsChannelState) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CHANNEL_SET)
        }
        val status = states.value[deviceUid]?.status
        return gateway.execute(
            deviceUid,
            timerJsonCommand(
                action = DeviceTimerRuntimeContract.Action.CHANNEL_SET,
                dataFactory = {
                    DeviceTimerCommandValidation.validateChannelRequest(payload, status, access)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceTimerMutationParser.parseChannelSet(data).also { result ->
                        DeviceTimerCommandValidation.validateChannelResult(
                            payload,
                            result,
                            status,
                            access
                        )
                    }
                }
            )
        ).recordSuccess { result -> stateStore.recordChannel(deviceUid, result) }
    }

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceTimerRegime,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = setChannel(
        deviceUid,
        DeviceTimerChannelSetPayload(channelKey, regime, save)
    )

    suspend fun setChannelDisplayName(
        deviceUid: DeviceUid,
        channelKey: String,
        displayName: String,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceTimerConfigApplyPayload(
            channels = listOf(DeviceTimerChannelConfig(channelKey, displayName = displayName)),
            save = save
        )
    )

    suspend fun createSchedule(
        deviceUid: DeviceUid,
        schedule: DeviceTimerScheduleConfig,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
        deviceUid = deviceUid,
        save = save
    ) { current ->
        require(current.size < DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES) {
            "Timer schedule capacity is full."
        }
        current + schedule
    }

    suspend fun updateSchedule(
        deviceUid: DeviceUid,
        scheduleIndex: Int,
        schedule: DeviceTimerScheduleConfig,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
        deviceUid = deviceUid,
        save = save
    ) { current ->
        require(scheduleIndex in current.indices) { "Unknown Timer schedule index: $scheduleIndex" }
        current.toMutableList().apply { this[scheduleIndex] = schedule }.toList()
    }

    suspend fun deleteSchedule(
        deviceUid: DeviceUid,
        scheduleIndex: Int,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
        deviceUid = deviceUid,
        save = save
    ) { current ->
        require(scheduleIndex in current.indices) { "Unknown Timer schedule index: $scheduleIndex" }
        current.filterIndexed { index, _ -> index != scheduleIndex }
    }

    private suspend fun mutateSchedules(
        deviceUid: DeviceUid,
        save: Boolean,
        transform: (List<DeviceTimerScheduleConfig>) -> List<DeviceTimerScheduleConfig>
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
        val baseline = when (val result = ensureConfigBaseline(deviceUid)) {
            is TimerConfigBaseline.Ready -> result.config
            is TimerConfigBaseline.Failed -> return result.outcome.asFailure()
        }
        val current = baseline.schedules.map(DeviceTimerScheduleConfigSnapshot::toPayload)
        return applyConfig(
            deviceUid,
            DeviceTimerConfigApplyPayload(
                schedules = transform(current),
                save = save
            )
        )
    }

    private suspend fun ensureConfigBaseline(deviceUid: DeviceUid): TimerConfigBaseline =
        states.value[deviceUid]?.config?.let(TimerConfigBaseline::Ready) ?: when (
            val status = requestStatus(deviceUid)
        ) {
            is DeviceRuntimeCommandOutcome.Success -> TimerConfigBaseline.Ready(
                requireNotNull(states.value[deviceUid]?.config) {
                    "Successful Timer status did not publish a config baseline."
                }
            )
            else -> TimerConfigBaseline.Failed(status)
        }

}

private sealed interface TimerConfigBaseline {
    data class Ready(val config: DeviceTimerConfigSnapshot) : TimerConfigBaseline
    data class Failed(
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : TimerConfigBaseline
}

private fun configUnsupported(
    payload: DeviceTimerConfigApplyPayload,
    access: DeviceTimerRuntimeAccess
): Boolean = !access.supportsApi ||
    (payload.schedules != null && !access.supportsSchedules) ||
    (
        payload.channels?.any { channel -> channel.displayName != null } == true &&
            !access.supportsChannelDisplayName
        )

private fun <T> timerJsonCommand(
    action: String,
    dataFactory: () -> JSONObject = ::JSONObject,
    parser: (JSONObject) -> T
): DeviceRuntimeJsonCommand<T> = DeviceRuntimeJsonCommand(
    module = DeviceTimerRuntimeContract.MODULE,
    action = action,
    dataFactory = dataFactory,
    successParser = parser
)

private fun timerUnsupported(
    deviceUid: DeviceUid,
    action: String
): DeviceRuntimeCommandOutcome.UnsupportedByDevice =
    DeviceRuntimeCommandOutcome.UnsupportedByDevice(
        deviceUid = deviceUid,
        module = DeviceTimerRuntimeContract.MODULE,
        action = action
    )

private fun <T> DeviceRuntimeCommandOutcome<T>.recordSuccess(
    recorder: (T) -> Unit
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success) recorder(outcome.value)
}

@Suppress("UNCHECKED_CAST")
private fun <T> DeviceRuntimeCommandOutcome<*>.asFailure(): DeviceRuntimeCommandOutcome<T> {
    check(this !is DeviceRuntimeCommandOutcome.Success<*>)
    return this as DeviceRuntimeCommandOutcome<T>
}
