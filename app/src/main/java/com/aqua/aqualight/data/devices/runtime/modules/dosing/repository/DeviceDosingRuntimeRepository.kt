package com.aqua.aqualight.data.devices.runtime.modules.dosing.repository

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingCommandValidation
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationCancelResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationConfirmResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelConfigApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelConfigPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelKeyPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelResetPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelResetResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDisplayNameMutation
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingGlobalStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingMutationResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingPumpCommandResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirRefillResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingStatusChange
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingMutationParser
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.dosing.state.DeviceDosingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.dosing.state.DeviceDosingRuntimeStateStore
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Central correlated facade for the final `aqualight.dosing.v1` firmware API. */
@Suppress("TooManyFunctions")
class DeviceDosingRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    private val stateStore: DeviceDosingRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceDosingRuntimeAccess
) {
    val states: StateFlow<Map<DeviceUid, DeviceDosingRuntimeState>> = stateStore.states

    suspend fun requestStatus(deviceUid: DeviceUid): DeviceRuntimeCommandOutcome<DeviceDosingGlobalStatus> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi) return dosingUnsupported(deviceUid, DeviceDosingRuntimeContract.Action.STATUS_GET)
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.STATUS_GET,
                parser = { data ->
                    DeviceDosingStatusParser.parseGlobal(data).also { status ->
                        DeviceDosingCommandValidation.validateGlobalStatus(status, access)
                    }
                }
            )
        ).recordGlobalStatus(deviceUid, stateStore)
    }

    suspend fun requestChannelStatus(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelStatus> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi) return dosingUnsupported(deviceUid, DeviceDosingRuntimeContract.Action.STATUS_GET)
        val key = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey
        return gateway.execute(
            deviceUid,
            dosingJsonCommand(
                action = DeviceDosingRuntimeContract.Action.STATUS_GET,
                dataFactory = { DeviceDosingChannelKeyPayload(key).toJson() },
                parser = { data ->
                    DeviceDosingStatusParser.parseChannel(data).also { status ->
                        DeviceDosingCommandValidation.validateChannelStatus(status, key, access)
                    }
                }
            )
        ).recordChannelStatus(deviceUid, stateStore)
    }

    suspend fun applyChannelConfig(
        deviceUid: DeviceUid,
        payload: DeviceDosingChannelConfigPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelConfigApplyResult> = withChannelBaseline(
        deviceUid,
        payload.normalizedChannelKey,
        DeviceDosingRuntimeContract.Action.CONFIG_APPLY
    ) { current, access ->
        DeviceDosingCommandValidation.validateChannelConfigRequest(payload, current, access)
        executeMutation(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
            dataFactory = payload::toJson,
            parser = DeviceDosingMutationParser::parseChannelConfigApply,
            expectedChannelKey = payload.normalizedChannelKey,
            access = access
        )
    }

    suspend fun setChannelDisplayName(
        deviceUid: DeviceUid,
        channelKey: String,
        displayName: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelConfigApplyResult> = withChannelBaseline(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.CONFIG_APPLY
    ) { current, access ->
        applyChannelConfig(
            deviceUid,
            DeviceDosingChannelConfigPayload(
                channelKey = current.channel.channelKey,
                expectedRevision = current.channel.revision,
                displayName = DeviceDosingDisplayNameMutation.Set(displayName)
            )
        )
    }

    suspend fun clearChannelDisplayName(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelConfigApplyResult> = withChannelBaseline(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.CONFIG_APPLY
    ) { current, _ ->
        applyChannelConfig(
            deviceUid,
            DeviceDosingChannelConfigPayload(
                channelKey = current.channel.channelKey,
                expectedRevision = current.channel.revision,
                displayName = DeviceDosingDisplayNameMutation.Clear
            )
        )
    }

    suspend fun configureReservoir(
        deviceUid: DeviceUid,
        channelKey: String,
        reservoir: DeviceDosingReservoirConfig
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelConfigApplyResult> = withChannelBaseline(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.CONFIG_APPLY
    ) { current, _ ->
        applyChannelConfig(
            deviceUid,
            DeviceDosingChannelConfigPayload(
                channelKey = current.channel.channelKey,
                expectedRevision = current.channel.revision,
                reservoir = reservoir
            )
        )
    }

    suspend fun applyProgram(
        deviceUid: DeviceUid,
        payload: DeviceDosingProgramApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingProgramApplyResult> = withChannelBaseline(
        deviceUid,
        payload.normalizedChannelKey,
        DeviceDosingRuntimeContract.Action.PROGRAM_APPLY
    ) { current, access ->
        DeviceDosingCommandValidation.validateProgramRequest(payload, current, access)
        executeMutation(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.PROGRAM_APPLY,
            dataFactory = payload::toJson,
            parser = DeviceDosingMutationParser::parseProgramApply,
            expectedChannelKey = payload.normalizedChannelKey,
            access = access
        )
    }

    suspend fun saveProgram(
        deviceUid: DeviceUid,
        channelKey: String,
        program: DeviceDosingProgram
    ): DeviceRuntimeCommandOutcome<DeviceDosingProgramApplyResult> = withChannelBaseline(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.PROGRAM_APPLY
    ) { current, _ ->
        applyProgram(
            deviceUid,
            DeviceDosingProgramApplyPayload(
                channelKey = current.channel.channelKey,
                expectedRevision = current.channel.revision,
                program = program
            )
        )
    }

    suspend fun resetChannel(
        deviceUid: DeviceUid,
        payload: DeviceDosingChannelResetPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelResetResult> = withChannelBaseline(
        deviceUid,
        payload.normalizedChannelKey,
        DeviceDosingRuntimeContract.Action.CHANNEL_RESET
    ) { current, access ->
        DeviceDosingCommandValidation.validateChannelResetRequest(payload, current, access)
        executeMutation(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.CHANNEL_RESET,
            dataFactory = payload::toJson,
            parser = DeviceDosingMutationParser::parseChannelReset,
            expectedChannelKey = payload.normalizedChannelKey,
            access = access
        )
    }

    suspend fun resetChannel(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelResetResult> = withChannelBaseline(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.CHANNEL_RESET
    ) { current, _ ->
        resetChannel(
            deviceUid,
            DeviceDosingChannelResetPayload(
                channelKey = current.channel.channelKey,
                expectedRevision = current.channel.revision
            )
        )
    }

    suspend fun primeStart(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> = executePumpCommand(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.PRIME_START,
        DeviceDosingMutationParser::parsePrimeStart
    )

    suspend fun primeStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> = executePumpCommand(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.PRIME_STOP,
        DeviceDosingMutationParser::parsePrimeStop
    )

    suspend fun doseStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> = executePumpCommand(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.DOSE_STOP,
        DeviceDosingMutationParser::parseDoseStop
    )

    suspend fun doseNow(
        deviceUid: DeviceUid,
        payload: DeviceDosingDoseNowPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingDoseNowResult> = withChannelBaseline(
        deviceUid,
        payload.normalizedChannelKey,
        DeviceDosingRuntimeContract.Action.DOSE_NOW
    ) { current, access ->
        DeviceDosingCommandValidation.validateDoseRequest(payload, current, access)
        executeMutation(
            deviceUid,
            DeviceDosingRuntimeContract.Action.DOSE_NOW,
            payload::toJson,
            DeviceDosingMutationParser::parseDoseNow,
            payload.normalizedChannelKey,
            access
        )
    }

    suspend fun calibrationStart(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationStartPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationStartResult> = withChannelBaseline(
        deviceUid,
        payload.normalizedChannelKey,
        DeviceDosingRuntimeContract.Action.CALIBRATION_START
    ) { current, access ->
        DeviceDosingCommandValidation.validateCalibrationStartRequest(payload, current, access)
        executeMutation(
            deviceUid,
            DeviceDosingRuntimeContract.Action.CALIBRATION_START,
            payload::toJson,
            DeviceDosingMutationParser::parseCalibrationStart,
            payload.normalizedChannelKey,
            access
        )
    }

    suspend fun calibrationFinish(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationFinishPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationFinishResult> = withChannelBaseline(
        deviceUid,
        payload.normalizedChannelKey,
        DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH
    ) { current, access ->
        DeviceDosingCommandValidation.validateCalibrationFinishRequest(payload, current, access)
        executeMutation(
            deviceUid,
            DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH,
            payload::toJson,
            DeviceDosingMutationParser::parseCalibrationFinish,
            payload.normalizedChannelKey,
            access
        )
    }

    suspend fun calibrationConfirm(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationConfirmResult> = executeCalibrationChannelCommand(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
        requireVerificationComplete = true,
        parser = DeviceDosingMutationParser::parseCalibrationConfirm
    )

    suspend fun calibrationCancel(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationCancelResult> = executeCalibrationChannelCommand(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL,
        requireVerificationComplete = false,
        parser = DeviceDosingMutationParser::parseCalibrationCancel
    )

    suspend fun reservoirRefill(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingReservoirRefillResult> = withChannelBaseline(
        deviceUid,
        channelKey,
        DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL
    ) { current, access ->
        DeviceDosingCommandValidation.validateReservoirRequest(channelKey, current, access)
        val key = current.channel.channelKey
        executeMutation(
            deviceUid,
            DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL,
            DeviceDosingChannelKeyPayload(key)::toJson,
            DeviceDosingMutationParser::parseReservoirRefill,
            key,
            access
        )
    }

    internal suspend fun acceptStatusChange(
        deviceUid: DeviceUid,
        data: JSONObject
    ): Boolean {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi) return false
        val change = DeviceDosingStatusParser.parseStatusChange(data)
        if (!stateStore.recordStatusChange(deviceUid, change)) return false
        val currentRevision = states.value[deviceUid]?.channels
            ?.get(change.channelKey)
            ?.channel
            ?.revision
        if (currentRevision != null && currentRevision > change.revision) return false
        return requestChannelStatus(deviceUid, change.channelKey) is DeviceRuntimeCommandOutcome.Success
    }

    private suspend fun executePumpCommand(
        deviceUid: DeviceUid,
        channelKey: String,
        action: String,
        parser: (JSONObject) -> DeviceDosingPumpCommandResult
    ): DeviceRuntimeCommandOutcome<DeviceDosingPumpCommandResult> = withChannelBaseline(
        deviceUid,
        channelKey,
        action
    ) { current, access ->
        val supported = when (action) {
            DeviceDosingRuntimeContract.Action.PRIME_START,
            DeviceDosingRuntimeContract.Action.PRIME_STOP -> access.supportsPrime
            DeviceDosingRuntimeContract.Action.DOSE_STOP -> access.supportsManualDose
            else -> false
        }
        if (!supported) return@withChannelBaseline dosingUnsupported(deviceUid, action)
        DeviceDosingCommandValidation.validatePrimeRequest(channelKey, current, access)
        val key = current.channel.channelKey
        executeMutation(
            deviceUid,
            action,
            DeviceDosingChannelKeyPayload(key)::toJson,
            parser,
            key,
            access
        )
    }

    private suspend fun <T : DeviceDosingMutationResult> executeCalibrationChannelCommand(
        deviceUid: DeviceUid,
        channelKey: String,
        action: String,
        requireVerificationComplete: Boolean,
        parser: (JSONObject) -> T
    ): DeviceRuntimeCommandOutcome<T> = withChannelBaseline(deviceUid, channelKey, action) { current, access ->
        if (!access.supportsCalibrationWorkflow) return@withChannelBaseline dosingUnsupported(deviceUid, action)
        DeviceDosingCommandValidation.validateCalibrationChannelRequest(
            channelKey,
            current,
            access,
            requireVerificationComplete
        )
        val key = current.channel.channelKey
        executeMutation(
            deviceUid,
            action,
            DeviceDosingChannelKeyPayload(key)::toJson,
            parser,
            key,
            access
        )
    }

    private suspend fun <T : DeviceDosingMutationResult> executeMutation(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject,
        parser: (JSONObject) -> T,
        expectedChannelKey: String,
        access: DeviceDosingRuntimeAccess
    ): DeviceRuntimeCommandOutcome<T> = gateway.execute(
        deviceUid,
        dosingJsonCommand(
            action = action,
            dataFactory = dataFactory,
            parser = { data ->
                parser(data).also { result ->
                    DeviceDosingCommandValidation.validateMutation(expectedChannelKey, result, access)
                }
            }
        )
    ).recordMutation(deviceUid, stateStore)

    private suspend fun <T> withChannelBaseline(
        deviceUid: DeviceUid,
        channelKey: String,
        action: String,
        block: suspend (
            current: DeviceDosingChannelStatus,
            access: DeviceDosingRuntimeAccess
        ) -> DeviceRuntimeCommandOutcome<T>
    ): DeviceRuntimeCommandOutcome<T> {
        val access = accessProvider(deviceUid)
        if (!access.supportsApi) return dosingUnsupported(deviceUid, action)
        val key = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey
        val state = states.value[deviceUid]
        val current = state?.channels?.get(key)
        val baseline = if (current != null && state.requiresStatusRefresh.not()) {
            current
        } else {
            when (val refreshed = requestChannelStatus(deviceUid, key)) {
                is DeviceRuntimeCommandOutcome.Success -> refreshed.value
                else -> return refreshed.retargetAction(action)
            }
        }
        return block(baseline, access)
    }
}

private fun <T> dosingJsonCommand(
    action: String,
    dataFactory: () -> JSONObject = ::JSONObject,
    parser: (JSONObject) -> T
) = DeviceRuntimeJsonCommand(
    module = DeviceDosingRuntimeContract.MODULE,
    action = action,
    dataFactory = dataFactory,
    successParser = parser
)

private fun <T> DeviceRuntimeCommandOutcome<T>.recordGlobalStatus(
    deviceUid: DeviceUid,
    store: DeviceDosingRuntimeStateStore
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success && outcome.value is DeviceDosingGlobalStatus) {
        store.recordGlobalStatus(deviceUid, outcome.value)
    }
}

private fun <T> DeviceRuntimeCommandOutcome<T>.recordChannelStatus(
    deviceUid: DeviceUid,
    store: DeviceDosingRuntimeStateStore
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success && outcome.value is DeviceDosingChannelStatus) {
        store.recordChannelStatus(deviceUid, outcome.value)
    }
}

private fun <T> DeviceRuntimeCommandOutcome<T>.recordMutation(
    deviceUid: DeviceUid,
    store: DeviceDosingRuntimeStateStore
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success && outcome.value is DeviceDosingMutationResult) {
        store.recordMutation(deviceUid, outcome.value)
    }
}

private fun <T> dosingUnsupported(
    deviceUid: DeviceUid,
    action: String
): DeviceRuntimeCommandOutcome<T> = DeviceRuntimeCommandOutcome.UnsupportedByDevice(
    deviceUid = deviceUid,
    module = DeviceDosingRuntimeContract.MODULE,
    action = action
)

private fun DeviceRuntimeCommandOutcome<*>.retargetAction(
    action: String
): DeviceRuntimeCommandOutcome<Nothing> = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected -> copy(action = action)
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> copy(action = action)
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> copy(action = action)
    is DeviceRuntimeCommandOutcome.SendFailed -> copy(action = action)
    is DeviceRuntimeCommandOutcome.Timeout -> copy(action = action)
    is DeviceRuntimeCommandOutcome.FirmwareError -> copy(action = action)
    is DeviceRuntimeCommandOutcome.ProtocolError -> copy(action = action)
    is DeviceRuntimeCommandOutcome.Cancelled -> copy(action = action)
    is DeviceRuntimeCommandOutcome.Success<*> -> error("Successful baseline cannot be retargeted as failure.")
}
