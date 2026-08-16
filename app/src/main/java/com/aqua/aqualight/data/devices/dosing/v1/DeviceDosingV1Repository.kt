package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import org.json.JSONObject

/**
 * Complete firmware v1 command surface over the shared correlated device-runtime gateway.
 *
 * The repository performs transport serialization and strict response parsing only. It does not
 * derive occurrence progress, reservoir state, revisions, runtime reasons, or percentages.
 */
@Suppress("TooManyFunctions")
class DeviceDosingV1Repository(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestGlobalStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.STATUS_GET,
        parser = DeviceDosingV1StatusParser::parseGlobal
    )

    suspend fun requestChannelStatus(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1ChannelStatus> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.STATUS_GET,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1StatusParser::parseChannel
    )

    suspend fun requestProgress(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1ProgressStatus> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.PROGRESS_GET,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1StatusParser::parseProgress
    )

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        request: DeviceDosingV1ConfigApplyRequest
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.CONFIG_APPLY,
        dataFactory = request::toJson,
        parser = DeviceDosingV1MutationParser::parseConfigApply
    )

    suspend fun applyProgram(
        deviceUid: DeviceUid,
        request: DeviceDosingV1ProgramApplyRequest
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.PROGRAM_APPLY,
        dataFactory = request::toJson,
        parser = DeviceDosingV1MutationParser::parseProgramApply
    )

    suspend fun resetChannel(
        deviceUid: DeviceUid,
        request: DeviceDosingV1ChannelResetRequest
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.CHANNEL_RESET,
        dataFactory = request::toJson,
        parser = DeviceDosingV1MutationParser::parseChannelReset
    )

    suspend fun startPrime(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1PrimeStartResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.PRIME_START,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1MutationParser::parsePrimeStart
    )

    suspend fun stopPrime(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1SimpleStopResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.PRIME_STOP,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1MutationParser::parsePrimeStop
    )

    suspend fun startCalibration(
        deviceUid: DeviceUid,
        request: DeviceDosingV1CalibrationStartRequest
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationStartResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.CALIBRATION_START,
        dataFactory = request::toJson,
        parser = DeviceDosingV1MutationParser::parseCalibrationStart
    )

    suspend fun finishCalibration(
        deviceUid: DeviceUid,
        request: DeviceDosingV1CalibrationFinishRequest
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationFinishResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.CALIBRATION_FINISH,
        dataFactory = request::toJson,
        parser = DeviceDosingV1MutationParser::parseCalibrationFinish
    )

    suspend fun confirmCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationConfirmResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1MutationParser::parseCalibrationConfirm
    )

    suspend fun cancelCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationCancelResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.CALIBRATION_CANCEL,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1MutationParser::parseCalibrationCancel
    )

    suspend fun doseNow(
        deviceUid: DeviceUid,
        request: DeviceDosingV1DoseNowRequest
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1DoseNowResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.DOSE_NOW,
        dataFactory = request::toJson,
        parser = DeviceDosingV1MutationParser::parseDoseNow
    )

    suspend fun stopDose(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1SimpleStopResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.DOSE_STOP,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1MutationParser::parseDoseStop
    )

    suspend fun refillReservoir(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1ReservoirRefillResult> = execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.RESERVOIR_REFILL,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1MutationParser::parseReservoirRefill
    )

    private suspend fun <T> execute(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject) -> T
    ): DeviceRuntimeCommandOutcome<T> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceDosingV1Contract.MODULE,
            action = action,
            dataFactory = dataFactory,
            successParser = parser
        )
    )
}
