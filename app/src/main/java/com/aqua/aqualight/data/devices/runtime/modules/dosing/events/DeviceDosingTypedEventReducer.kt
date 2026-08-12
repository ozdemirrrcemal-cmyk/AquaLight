package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Applies validated `dosing.status.changed` payloads to device-isolated Dosing state. */
internal class DeviceDosingTypedEventReducer(
    private val stateStore: DeviceDosingRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceDosingRuntimeAccess
) {
    fun apply(event: DeviceRuntimeTypedEvent): DeviceDosingEventApplyResult {
        val access = accessProvider(event.deviceUid)
        return when {
            event.type != DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED ->
                DeviceDosingEventApplyResult.Ignored
            !access.supportsApi -> DeviceDosingEventApplyResult.Ignored
            else -> applyValidatedEvent(event, access)
        }
    }

    private fun applyValidatedEvent(
        event: DeviceRuntimeTypedEvent,
        access: DeviceDosingRuntimeAccess
    ): DeviceDosingEventApplyResult = runCatching {
        applyPayload(event.deviceUid, event.payload, access)
    }.fold(
        onSuccess = { applied ->
            if (applied) DeviceDosingEventApplyResult.Applied
            else DeviceDosingEventApplyResult.Ignored
        },
        onFailure = { error ->
            DeviceDosingEventApplyResult.Malformed(error.message.orEmpty())
        }
    )

    private fun applyPayload(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload,
        access: DeviceDosingRuntimeAccess
    ): Boolean = when (payload) {
        is DeviceRuntimeEventPayload.Snapshot -> {
            val status = DeviceDosingStatusParser.parse(payload.data)
            DeviceDosingCommandValidation.validateStatus(status, access)
            stateStore.recordStatus(deviceUid, status)
        }
        is DeviceRuntimeEventPayload.CommandResult -> applyCommandResult(
            deviceUid,
            payload,
            access
        )
    }

    private fun applyCommandResult(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload.CommandResult,
        access: DeviceDosingRuntimeAccess
    ): Boolean {
        require(payload.commandModule == DeviceDosingRuntimeContract.MODULE) {
            "Dosing event command module differs from the event module."
        }
        val result = parseMutation(payload.commandAction, payload.result) ?: return false
        validateMutation(deviceUid, result, access)
        return stateStore.recordMutation(deviceUid, result)
    }

    private fun parseMutation(
        action: String,
        result: org.json.JSONObject
    ): DeviceDosingMutationResult? = when (action) {
        DeviceDosingRuntimeContract.Action.CONFIG_APPLY ->
            DeviceDosingMutationParser.parseConfigApply(result)
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

    private fun validateMutation(
        deviceUid: DeviceUid,
        result: DeviceDosingMutationResult,
        access: DeviceDosingRuntimeAccess
    ) {
        val status = stateStore.states.value[deviceUid]?.status
        when (result) {
            is DeviceDosingConfigApplyResult ->
                DeviceDosingCommandValidation.validateConfigSnapshot(
                    result.config,
                    status,
                    access
                )
            is DeviceDosingCalibrationStartResult ->
                DeviceDosingCommandValidation.validateCalibrationRequest(
                    result.channelKey,
                    status,
                    access
                )
            is DeviceDosingPumpCommandResult ->
                validateChannel(result.channel, status, access)
            is DeviceDosingDoseNowResult ->
                validateChannel(result.channel, status, access)
            is DeviceDosingCalibrationFinishResult ->
                validateChannel(result.channel, status, access)
            is DeviceDosingCalibrationConfirmResult ->
                validateChannel(result.channel, status, access)
            is DeviceDosingCalibrationCancelResult ->
                validateChannel(result.channel, status, access)
            is DeviceDosingReservoirRefillResult ->
                validateChannel(result.channel, status, access)
        }
    }

    private fun validateChannel(
        channel: DeviceDosingChannelStatusSnapshot,
        status: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        DeviceDosingCommandValidation.validateChannelSnapshot(channel, status, access)
    }
}

internal sealed interface DeviceDosingEventApplyResult {
    data object Applied : DeviceDosingEventApplyResult
    data object Ignored : DeviceDosingEventApplyResult
    data class Malformed(val reason: String) : DeviceDosingEventApplyResult
}
