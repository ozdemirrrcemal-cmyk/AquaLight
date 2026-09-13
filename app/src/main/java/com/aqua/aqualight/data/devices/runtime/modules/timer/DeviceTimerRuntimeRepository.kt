package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class DeviceTimerRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    internal val stateStore: DeviceTimerRuntimeStateStore,
    internal val accessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess
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

    private suspend fun reconcileEvent(deviceUid: DeviceUid, channelKey: String) {
        val currentStatus = states.value[deviceUid]?.status
        if (currentStatus == null || currentStatus.channelScoped) {
            requestStatus(deviceUid)
        } else {
            requestStatus(deviceUid, channelKey)
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
            is TimerStatusBaseline.Failed -> result.outcome.asTimerFailure()
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
            is TimerStatusBaseline.Failed -> result.outcome.asTimerFailure()
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
}

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

internal fun timerUnsupported(
    deviceUid: DeviceUid,
    action: String
): DeviceRuntimeCommandOutcome.UnsupportedByDevice =
    DeviceRuntimeCommandOutcome.UnsupportedByDevice(
        deviceUid = deviceUid,
        module = DeviceTimerRuntimeContract.MODULE,
        action = action
    )
