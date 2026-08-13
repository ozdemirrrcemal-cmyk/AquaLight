package com.aqua.aqualight.data.devices.runtime.modules.dosing.events

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingCommandValidation
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingMutationResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingMutationParser
import com.aqua.aqualight.data.devices.runtime.modules.dosing.state.DeviceDosingRuntimeStateStore

/** Applies command-result events and resolves slim status-change notifications authoritatively. */
internal class DeviceDosingTypedEventReducer(
    private val stateStore: DeviceDosingRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceDosingRuntimeAccess,
    private val refreshStatusChange: suspend (DeviceUid, org.json.JSONObject) -> Boolean
) {
    suspend fun apply(event: DeviceRuntimeTypedEvent): DeviceDosingEventApplyResult {
        val access = accessProvider(event.deviceUid)
        return when {
            event.type != DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED ->
                DeviceDosingEventApplyResult.Ignored
            !access.supportsApi -> DeviceDosingEventApplyResult.Ignored
            else -> runCatching {
                when (val payload = event.payload) {
                    is DeviceRuntimeEventPayload.Snapshot ->
                        refreshStatusChange(event.deviceUid, payload.data)
                    is DeviceRuntimeEventPayload.CommandResult ->
                        applyCommandResult(event.deviceUid, payload, access)
                }
            }.fold(
                onSuccess = { applied ->
                    if (applied) DeviceDosingEventApplyResult.Applied
                    else DeviceDosingEventApplyResult.Ignored
                },
                onFailure = { error ->
                    DeviceDosingEventApplyResult.Malformed(error.message.orEmpty())
                }
            )
        }
    }

    private fun applyCommandResult(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload.CommandResult,
        access: DeviceDosingRuntimeAccess
    ): Boolean {
        require(payload.commandModule == DeviceDosingRuntimeContract.MODULE)
        val result = parseMutation(payload.commandAction, payload.result) ?: return false
        DeviceDosingCommandValidation.validateMutation(result.channelKey, result, access)
        return stateStore.recordMutation(deviceUid, result)
    }

    private fun parseMutation(
        action: String,
        result: org.json.JSONObject
    ): DeviceDosingMutationResult? = when (action) {
        DeviceDosingRuntimeContract.Action.CONFIG_APPLY ->
            DeviceDosingMutationParser.parseChannelConfigApply(result)
        DeviceDosingRuntimeContract.Action.PROGRAM_APPLY ->
            DeviceDosingMutationParser.parseProgramApply(result)
        DeviceDosingRuntimeContract.Action.CHANNEL_RESET ->
            DeviceDosingMutationParser.parseChannelReset(result)
        DeviceDosingRuntimeContract.Action.PRIME_START ->
            DeviceDosingMutationParser.parsePrimeStart(result)
        DeviceDosingRuntimeContract.Action.PRIME_STOP ->
            DeviceDosingMutationParser.parsePrimeStop(result)
        DeviceDosingRuntimeContract.Action.CALIBRATION_START ->
            DeviceDosingMutationParser.parseCalibrationStart(result)
        DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH ->
            DeviceDosingMutationParser.parseCalibrationFinish(result)
        DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM ->
            DeviceDosingMutationParser.parseCalibrationConfirm(result)
        DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL ->
            DeviceDosingMutationParser.parseCalibrationCancel(result)
        DeviceDosingRuntimeContract.Action.DOSE_NOW ->
            DeviceDosingMutationParser.parseDoseNow(result)
        DeviceDosingRuntimeContract.Action.DOSE_STOP ->
            DeviceDosingMutationParser.parseDoseStop(result)
        DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL ->
            DeviceDosingMutationParser.parseReservoirRefill(result)
        else -> null
    }
}

internal sealed interface DeviceDosingEventApplyResult {
    data object Applied : DeviceDosingEventApplyResult
    data object Ignored : DeviceDosingEventApplyResult
    data class Malformed(val reason: String) : DeviceDosingEventApplyResult
}
