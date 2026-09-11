package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

@Suppress("TooManyFunctions")
class DeviceTimerRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    internal val stateStore: DeviceTimerRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess
) {
    private val eventReducer = DeviceTimerTypedEventReducer(stateStore, accessProvider)
    private val mutationGate = DeviceTimerMutationGate()

    val states: StateFlow<Map<DeviceUid, DeviceTimerRuntimeState>> = stateStore.states

    internal fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = stateStore.beginGeneration(deviceUid, generation)

    internal fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = stateStore.invalidate(deviceUid, generation)

    /** Typed firmware events reconcile through this facade and the single central state owner. */
    internal suspend fun consume(event: DeviceRuntimeTypedEvent) {
        when (val result = eventReducer.apply(event)) {
            DeviceTimerEventApplyResult.Applied,
            DeviceTimerEventApplyResult.Ignored -> Unit
            is DeviceTimerEventApplyResult.RefreshRequired ->
                reconcileEvent(event.deviceUid, result.channelKey)
            is DeviceTimerEventApplyResult.Malformed -> {
                stateStore.requireStatusRefresh(event.deviceUid, event.generation)
                requestStatus(event.deviceUid)
            }
        }
    }

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
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = mutationGate.withDevice(
        deviceUid
    ) {
        val access = accessProvider(deviceUid)
        if (configUnsupported(payload, access)) {
            timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CONFIG_APPLY)
        } else when (val result = ensureGlobalStatus(deviceUid)) {
            is TimerStatusBaseline.Failed -> result.outcome.asFailure()
            is TimerStatusBaseline.Ready -> {
                val baseline = result.status
                val outcome = gateway.execute(
                    deviceUid,
                    timerJsonCommand(
                        action = DeviceTimerRuntimeContract.Action.CONFIG_APPLY,
                        dataFactory = {
                            DeviceTimerCommandValidation.validateConfigRequest(
                                payload,
                                baseline,
                                access
                            )
                            payload.toJson()
                        },
                        parser = { data ->
                            DeviceTimerMutationParser.parseConfigApply(data).also { mutation ->
                                DeviceTimerCommandValidation.validateConfigResult(
                                    payload,
                                    mutation,
                                    baseline,
                                    access
                                )
                            }
                        }
                    )
                )
                reconcileConfigMutation(deviceUid, payload, outcome)
            }
        }
    }

    suspend fun setChannel(
        deviceUid: DeviceUid,
        payload: DeviceTimerChannelSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = mutationGate.withDevice(
        deviceUid
    ) {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsChannelState) {
            timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CHANNEL_SET)
        } else when (val result = ensureGlobalStatus(deviceUid)) {
            is TimerStatusBaseline.Failed -> result.outcome.asFailure()
            is TimerStatusBaseline.Ready -> {
                val baseline = result.status
                val outcome = gateway.execute(
                    deviceUid,
                    timerJsonCommand(
                        action = DeviceTimerRuntimeContract.Action.CHANNEL_SET,
                        dataFactory = {
                            DeviceTimerCommandValidation.validateChannelRequest(
                                payload,
                                baseline,
                                access
                            )
                            payload.toJson()
                        },
                        parser = { data ->
                            DeviceTimerMutationParser.parseChannelSet(data).also { mutation ->
                                DeviceTimerCommandValidation.validateChannelResult(
                                    payload,
                                    mutation,
                                    baseline,
                                    access
                                )
                            }
                        }
                    )
                )
                reconcileChannelMutation(deviceUid, payload, outcome)
            }
        }
    }

    private suspend fun reconcileConfigMutation(
        deviceUid: DeviceUid,
        payload: DeviceTimerConfigApplyPayload,
        outcome: DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult>
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = reconcileMutation(
        deviceUid = deviceUid,
        channelKey = payload.normalizedChannelKey,
        outcome = outcome,
        channel = DeviceTimerConfigApplyResult::channel,
        revision = DeviceTimerConfigApplyResult::revision
    ) { _, status ->
        val channel = status.channels.singleOrNull()
        val displayNameConfirmed = when (val update = payload.displayName) {
            DeviceTimerDisplayNameUpdate.Omitted -> true
            DeviceTimerDisplayNameUpdate.Clear -> channel?.displayName == channel?.name
            is DeviceTimerDisplayNameUpdate.Value ->
                channel?.displayName == update.normalizedDisplayName
        }
        val schedulesConfirmed = payload.schedules?.matches(status.schedules) ?: true
        displayNameConfirmed && schedulesConfirmed
    }

    private suspend fun reconcileChannelMutation(
        deviceUid: DeviceUid,
        payload: DeviceTimerChannelSetPayload,
        outcome: DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult>
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = reconcileMutation(
        deviceUid = deviceUid,
        channelKey = payload.normalizedChannelKey,
        outcome = outcome,
        channel = DeviceTimerChannelSetResult::channel,
        revision = DeviceTimerChannelSetResult::revision
    ) { _, status ->
        val channel = status.channels.singleOrNull()
        if (payload.durationMs == null) {
            channel?.regime == payload.regime
        } else {
            channel?.temporaryOverrideActive == true &&
                channel.operatingState == payload.regime.toOperatingStateOrNull()
        }
    }

    /**
     * ACK and rejection reconciliation stays inside the central Timer facade. There is no command
     * replay: a successful mutation must be confirmed by a same-generation scoped readback, while
     * failures that can leave cached state uncertain revoke reads until one fresh snapshot lands.
     */
    private suspend fun <T> reconcileMutation(
        deviceUid: DeviceUid,
        channelKey: String,
        outcome: DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceTimerChannelStatus,
        revision: (T) -> Long,
        confirms: (T, DeviceTimerStatus) -> Boolean
    ): DeviceRuntimeCommandOutcome<T> {
        if (outcome !is DeviceRuntimeCommandOutcome.Success) {
            reconcileRejectedMutation(deviceUid, channelKey, outcome)
            return outcome
        }
        val mutation = outcome
        val value = mutation.value
        stateStore.recordMutationChannel(
            deviceUid,
            mutation.generation,
            channel(value),
            revision(value)
        )
        return when (val readback = requestStatus(deviceUid, channelKey)) {
            is DeviceRuntimeCommandOutcome.Success -> if (
                mutation.isConfirmedBy(deviceUid, channelKey, readback, revision, confirms)
            ) {
                mutation
            } else {
                stateStore.invalidate(deviceUid, mutation.generation)
                mutation.reconciliationFailure()
            }
            else -> {
                stateStore.invalidate(deviceUid, mutation.generation)
                readback.asFailure()
            }
        }
    }

    private suspend fun reconcileRejectedMutation(
        deviceUid: DeviceUid,
        channelKey: String,
        outcome: DeviceRuntimeCommandOutcome<*>
    ) {
        val failure = outcome as? DeviceRuntimeCommandOutcome.FirmwareError ?: return
        if (!failure.requiresTimerReconciliation()) return
        stateStore.requireStatusRefresh(deviceUid, failure.generation)
        val global = requestStatus(deviceUid)
        if (
            global is DeviceRuntimeCommandOutcome.Success &&
            global.generation == failure.generation &&
            global.value.channels.any { channel -> channel.key == channelKey }
        ) {
            requestStatus(deviceUid, channelKey)
        }
    }

    private fun <T> DeviceRuntimeCommandOutcome.Success<T>.isConfirmedBy(
        deviceUid: DeviceUid,
        channelKey: String,
        readback: DeviceRuntimeCommandOutcome.Success<DeviceTimerStatus>,
        revision: (T) -> Long,
        confirms: (T, DeviceTimerStatus) -> Boolean
    ): Boolean {
        val accepted = stateStore.currentAuthoritativeState(deviceUid)
        return readback.generation == generation &&
            readback.value.channelScoped &&
            readback.value.selectedChannelKey == channelKey &&
            readback.value.revision == revision(value) &&
            accepted?.connectionGeneration == readback.generation &&
            accepted.channelDetails[channelKey] == readback.value &&
            confirms(value, readback.value)
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
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsChannelDisplayName) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CONFIG_APPLY)
        }
        return withGlobalStatus(deviceUid) { status ->
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
    }

    suspend fun clearChannelDisplayName(
        deviceUid: DeviceUid,
        channelKey: String,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi || !access.supportsChannelDisplayName) {
            return timerUnsupported(deviceUid, DeviceTimerRuntimeContract.Action.CONFIG_APPLY)
        }
        return withGlobalStatus(deviceUid) { status ->
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
    }

    suspend fun replaceSchedules(
        deviceUid: DeviceUid,
        channelKey: String,
        expectedRevision: Long,
        schedules: List<DeviceTimerScheduleConfig>,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = applyConfig(
        deviceUid,
        DeviceTimerConfigApplyPayload(
            channelKey = channelKey,
            expectedRevision = expectedRevision,
            schedules = schedules,
            save = save
        )
    )

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

internal fun DeviceTimerRuntimeRepository.currentAuthoritativeState(
    deviceUid: DeviceUid
): DeviceTimerRuntimeState? = stateStore.currentAuthoritativeState(deviceUid)

private suspend fun DeviceTimerRuntimeRepository.reconcileEvent(
    deviceUid: DeviceUid,
    channelKey: String
) {
    val currentStatus = states.value[deviceUid]?.status
    if (currentStatus == null || currentStatus.channelScoped) {
        requestStatus(deviceUid)
    } else {
        requestStatus(deviceUid, channelKey)
    }
}

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

private fun List<DeviceTimerScheduleConfig>.matches(
    statuses: List<DeviceTimerScheduleStatus>
): Boolean {
    if (size != statuses.size) return false
    val bySlotId = statuses.associateBy(DeviceTimerScheduleStatus::slotId)
    return all { schedule ->
        val status = bySlotId[schedule.slotId] ?: return@all false
        status.enabled == schedule.enabled &&
            status.name == schedule.normalizedName &&
            status.weekdays == schedule.weekdays &&
            status.startTimeMs == schedule.startTimeMs &&
            status.endTimeMs == schedule.endTimeMs &&
            status.spansMidnight == schedule.spansMidnight
    }
}

private fun DeviceTimerRegime.toOperatingStateOrNull(): DeviceTimerOperatingState? = when (this) {
    DeviceTimerRegime.ON -> DeviceTimerOperatingState.ON
    DeviceTimerRegime.OFF -> DeviceTimerOperatingState.OFF
    DeviceTimerRegime.AUTO -> null
}

private fun DeviceRuntimeCommandOutcome.FirmwareError.requiresTimerReconciliation(): Boolean =
    code == DeviceTimerRuntimeContract.Error.CONFLICT ||
        code == DeviceTimerRuntimeContract.Error.NOT_FOUND ||
        code == DeviceTimerRuntimeContract.Error.HARDWARE_ERROR ||
        code == DeviceTimerRuntimeContract.Error.STORAGE_ERROR

private fun <T> DeviceRuntimeCommandOutcome.Success<T>.reconciliationFailure():
    DeviceRuntimeCommandOutcome.ProtocolError = DeviceRuntimeCommandOutcome.ProtocolError(
        deviceUid = deviceUid,
        module = module,
        action = action,
        messageId = messageId,
        generation = generation,
        reason = "Timer mutation ACK was not confirmed by authoritative channel readback."
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
