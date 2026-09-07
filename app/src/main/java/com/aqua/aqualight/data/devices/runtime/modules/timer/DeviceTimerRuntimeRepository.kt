package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

@Suppress("TooManyFunctions")
class DeviceTimerRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    internal val stateStore: DeviceTimerRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess
) {
    val states: StateFlow<Map<DeviceUid, DeviceTimerRuntimeState>> = stateStore.states

    internal fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = stateStore.beginGeneration(deviceUid, generation)

    internal fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = stateStore.invalidate(deviceUid, generation)

    suspend fun requestStatus(
        deviceUid: DeviceUid,
        channelKey: String? = null
    ): DeviceRuntimeCommandOutcome<DeviceTimerStatus> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.STATUS_GET)
        }
        val payload = DeviceTimerStatusGetPayload(channelKey)
        val outcome = gateway.execute(
            deviceUid,
            timerJsonCommand(
                action = DeviceTimerRuntimeContract.Action.STATUS_GET,
                dataFactory = payload::toJson,
                parser = { data ->
                    DeviceTimerStatusParser.parse(data).also { status ->
                        DeviceTimerCommandValidation.validateStatus(
                            status,
                            payload.normalizedChannelKey,
                            access
                        )
                    }
                }
            )
        )
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateStore.recordStatus(deviceUid, outcome.generation, outcome.value)
        }
        return outcome
    }

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceTimerConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
        val access = accessProvider(deviceUid)
        if (configUnsupported(payload, access)) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CONFIG_APPLY)
        }
        val baseline = when (val result = ensureGlobalStatus(deviceUid)) {
            is TimerStatusBaseline.Ready -> result.status
            is TimerStatusBaseline.Failed -> return result.outcome.asFailure()
        }
        val outcome = gateway.execute(
            deviceUid,
            timerJsonCommand(
                action = DeviceTimerRuntimeContract.Action.CONFIG_APPLY,
                dataFactory = {
                    DeviceTimerCommandValidation.validateConfigRequest(payload, baseline, access)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceTimerMutationParser.parseConfigApply(data).also { result ->
                        DeviceTimerCommandValidation.validateConfigResult(
                            payload,
                            result,
                            baseline,
                            access
                        )
                    }
                }
            )
        )
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateStore.recordMutationChannel(
                deviceUid,
                outcome.generation,
                outcome.value.channel,
                outcome.value.revision
            )
            requestStatus(deviceUid, outcome.value.channelKey)
        }
        return outcome
    }

    suspend fun setChannel(
        deviceUid: DeviceUid,
        payload: DeviceTimerChannelSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsChannelState) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CHANNEL_SET)
        }
        val baseline = when (val result = ensureGlobalStatus(deviceUid)) {
            is TimerStatusBaseline.Ready -> result.status
            is TimerStatusBaseline.Failed -> return result.outcome.asFailure()
        }
        val outcome = gateway.execute(
            deviceUid,
            timerJsonCommand(
                action = DeviceTimerRuntimeContract.Action.CHANNEL_SET,
                dataFactory = {
                    DeviceTimerCommandValidation.validateChannelRequest(payload, baseline, access)
                    payload.toJson()
                },
                parser = { data ->
                    DeviceTimerMutationParser.parseChannelSet(data).also { result ->
                        DeviceTimerCommandValidation.validateChannelResult(
                            payload,
                            result,
                            baseline,
                            access
                        )
                    }
                }
            )
        )
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateStore.recordMutationChannel(
                deviceUid,
                outcome.generation,
                outcome.value.channel,
                outcome.value.revision
            )
            requestStatus(deviceUid, outcome.value.channelKey)
        }
        return outcome
    }

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceTimerRegime,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = withGlobalStatus(
        deviceUid
    ) { status ->
        setChannel(
            deviceUid,
            DeviceTimerChannelSetPayload(
                channelKey = channelKey,
                expectedRevision = status.revision,
                regime = regime,
                save = save
            )
        )
    }

    suspend fun setTemporaryOverride(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceTimerRegime,
        durationMs: Long
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = withGlobalStatus(
        deviceUid
    ) { status ->
        setChannel(
            deviceUid,
            DeviceTimerChannelSetPayload(
                channelKey = channelKey,
                expectedRevision = status.revision,
                regime = regime,
                durationMs = durationMs,
                save = false
            )
        )
    }

    suspend fun setChannelDisplayName(
        deviceUid: DeviceUid,
        channelKey: String,
        displayName: String,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = withGlobalStatus(
        deviceUid
    ) { status ->
        applyConfig(
            deviceUid,
            DeviceTimerConfigApplyPayload(
                channelKey = channelKey,
                expectedRevision = status.revision,
                displayName = DeviceTimerDisplayNameUpdate.Value(displayName),
                save = save
            )
        )
    }

    suspend fun clearChannelDisplayName(
        deviceUid: DeviceUid,
        channelKey: String,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = withGlobalStatus(
        deviceUid
    ) { status ->
        applyConfig(
            deviceUid,
            DeviceTimerConfigApplyPayload(
                channelKey = channelKey,
                expectedRevision = status.revision,
                displayName = DeviceTimerDisplayNameUpdate.Clear,
                save = save
            )
        )
    }

    suspend fun replaceSchedules(
        deviceUid: DeviceUid,
        channelKey: String,
        schedules: List<DeviceTimerScheduleConfig>,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = withGlobalStatus(
        deviceUid
    ) { status ->
        applyConfig(
            deviceUid,
            DeviceTimerConfigApplyPayload(
                channelKey = channelKey,
                expectedRevision = status.revision,
                schedules = schedules,
                save = save
            )
        )
    }

    suspend fun createSchedule(
        deviceUid: DeviceUid,
        channelKey: String,
        schedule: DeviceTimerScheduleConfig,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
        deviceUid,
        channelKey,
        save
    ) { current ->
        require(current.none { it.slotId == schedule.slotId }) {
            "Timer slotId already exists for this channel: ${schedule.slotId}"
        }
        current + schedule
    }

    suspend fun updateSchedule(
        deviceUid: DeviceUid,
        channelKey: String,
        slotId: Int,
        schedule: DeviceTimerScheduleConfig,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
        deviceUid,
        channelKey,
        save
    ) { current ->
        require(schedule.slotId == slotId) { "Timer schedule slotId is immutable." }
        require(current.any { it.slotId == slotId }) { "Unknown Timer slotId: $slotId" }
        current.map { existing -> if (existing.slotId == slotId) schedule else existing }
    }

    suspend fun deleteSchedule(
        deviceUid: DeviceUid,
        channelKey: String,
        slotId: Int,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutateSchedules(
        deviceUid,
        channelKey,
        save
    ) { current ->
        require(current.any { it.slotId == slotId }) { "Unknown Timer slotId: $slotId" }
        current.filterNot { it.slotId == slotId }
    }
}

internal fun DeviceTimerRuntimeRepository.isAuthoritative(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean = stateStore.isAuthoritative(deviceUid, generation)

private suspend fun DeviceTimerRuntimeRepository.mutateSchedules(
    deviceUid: DeviceUid,
    channelKey: String,
    save: Boolean,
    transform: (List<DeviceTimerScheduleConfig>) -> List<DeviceTimerScheduleConfig>
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
    val detail = when (val result = ensureChannelDetail(deviceUid, channelKey)) {
        is TimerStatusBaseline.Ready -> result.status
        is TimerStatusBaseline.Failed -> return result.outcome.asFailure()
    }
    val current = detail.schedules.map(DeviceTimerScheduleStatus::toPayload)
    return applyConfig(
        deviceUid,
        DeviceTimerConfigApplyPayload(
            channelKey = channelKey,
            expectedRevision = detail.revision,
            schedules = transform(current),
            save = save
        )
    )
}

private suspend fun DeviceTimerRuntimeRepository.ensureGlobalStatus(
    deviceUid: DeviceUid
): TimerStatusBaseline {
    val current = stateStore.currentAuthoritativeState(deviceUid)?.status
    if (current != null && !current.channelScoped) return TimerStatusBaseline.Ready(current)
    return when (val outcome = requestStatus(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> TimerStatusBaseline.Ready(outcome.value)
        else -> TimerStatusBaseline.Failed(outcome)
    }
}

@Suppress("ReturnCount")
private suspend fun DeviceTimerRuntimeRepository.ensureChannelDetail(
    deviceUid: DeviceUid,
    channelKey: String
): TimerStatusBaseline {
    val normalizedChannelKey = normalizeTimerChannelKey(channelKey)
    val global = when (val result = ensureGlobalStatus(deviceUid)) {
        is TimerStatusBaseline.Ready -> result.status
        is TimerStatusBaseline.Failed -> return result
    }
    val cached = stateStore.currentAuthoritativeState(deviceUid)
        ?.channelDetails
        ?.get(normalizedChannelKey)
        ?.takeIf { detail -> detail.revision == global.revision }
    if (cached != null) return TimerStatusBaseline.Ready(cached)
    return when (val outcome = requestStatus(deviceUid, normalizedChannelKey)) {
        is DeviceRuntimeCommandOutcome.Success -> TimerStatusBaseline.Ready(outcome.value)
        else -> TimerStatusBaseline.Failed(outcome)
    }
}

private suspend fun <T> DeviceTimerRuntimeRepository.withGlobalStatus(
    deviceUid: DeviceUid,
    block: suspend (DeviceTimerStatus) -> DeviceRuntimeCommandOutcome<T>
): DeviceRuntimeCommandOutcome<T> = when (val baseline = ensureGlobalStatus(deviceUid)) {
    is TimerStatusBaseline.Ready -> block(baseline.status)
    is TimerStatusBaseline.Failed -> baseline.outcome.asFailure()
}

private sealed interface TimerStatusBaseline {
    data class Ready(val status: DeviceTimerStatus) : TimerStatusBaseline
    data class Failed(val outcome: DeviceRuntimeCommandOutcome<*>) : TimerStatusBaseline
}

private fun DeviceTimerScheduleStatus.toPayload(): DeviceTimerScheduleConfig =
    DeviceTimerScheduleConfig(
        slotId = slotId,
        enabled = enabled,
        name = name,
        weekdays = weekdays,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        spansMidnight = spansMidnight
    )

private fun configUnsupported(
    payload: DeviceTimerConfigApplyPayload,
    access: DeviceTimerRuntimeAccess
): Boolean = !access.supportsApi ||
    (payload.schedules != null && !access.supportsSchedules) ||
    (
        payload.displayName != DeviceTimerDisplayNameUpdate.Omitted &&
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

@Suppress("UNCHECKED_CAST")
private fun <T> DeviceRuntimeCommandOutcome<*>.asFailure(): DeviceRuntimeCommandOutcome<T> {
    check(this !is DeviceRuntimeCommandOutcome.Success<*>)
    return this as DeviceRuntimeCommandOutcome<T>
}
