package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import org.json.JSONObject

/** Complete firmware v1 command surface, composed from cohesive stateless command groups. */
class DeviceDosingV1Repository(
    gateway: DeviceRuntimeCommandGateway
) : DeviceDosingV1StatusCommands by DeviceDosingV1StatusRepository(gateway),
    DeviceDosingV1ConfigurationCommands by DeviceDosingV1ConfigurationRepository(gateway),
    DeviceDosingV1CalibrationCommands by DeviceDosingV1CalibrationRepository(gateway),
    DeviceDosingV1RuntimeCommands by DeviceDosingV1RuntimeRepository(gateway)

interface DeviceDosingV1StatusCommands {
    suspend fun requestGlobalStatus(deviceUid: DeviceUid):
        DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus>
    suspend fun requestChannelStatus(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        DeviceRuntimeCommandOutcome<DeviceDosingV1ChannelStatus>
    suspend fun requestProgress(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        DeviceRuntimeCommandOutcome<DeviceDosingV1ProgressStatus>
}

interface DeviceDosingV1ConfigurationCommands {
    suspend fun applyConfig(deviceUid: DeviceUid, request: DeviceDosingV1ConfigApplyRequest):
        DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult>
    suspend fun applyProgram(deviceUid: DeviceUid, request: DeviceDosingV1ProgramApplyRequest):
        DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult>
    suspend fun resetChannel(deviceUid: DeviceUid, request: DeviceDosingV1ChannelResetRequest):
        DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult>
}

interface DeviceDosingV1CalibrationCommands {
    suspend fun startPrime(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        DeviceRuntimeCommandOutcome<DeviceDosingV1PrimeStartResult>
    suspend fun stopPrime(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        DeviceRuntimeCommandOutcome<DeviceDosingV1SimpleStopResult>
    suspend fun startCalibration(deviceUid: DeviceUid, request: DeviceDosingV1CalibrationStartRequest):
        DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationStartResult>
    suspend fun finishCalibration(deviceUid: DeviceUid, request: DeviceDosingV1CalibrationFinishRequest):
        DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationFinishResult>
    suspend fun confirmCalibration(deviceUid: DeviceUid, request: DeviceDosingV1CalibrationConfirmRequest):
        DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationConfirmResult>
    suspend fun cancelCalibration(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        DeviceRuntimeCommandOutcome<DeviceDosingV1CalibrationCancelResult>
}

interface DeviceDosingV1RuntimeCommands {
    suspend fun doseNow(deviceUid: DeviceUid, request: DeviceDosingV1DoseNowRequest):
        DeviceRuntimeCommandOutcome<DeviceDosingV1DoseNowResult>
    suspend fun stopDose(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        DeviceRuntimeCommandOutcome<DeviceDosingV1SimpleStopResult>
    suspend fun refillReservoir(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        DeviceRuntimeCommandOutcome<DeviceDosingV1ReservoirRefillResult>
}

private class DeviceDosingV1StatusRepository(gateway: DeviceRuntimeCommandGateway) :
    DeviceDosingV1StatusCommands {
    private val executor = DeviceDosingV1CommandExecutor(gateway)

    override suspend fun requestGlobalStatus(deviceUid: DeviceUid) = executor.execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.STATUS_GET,
        parser = DeviceDosingV1StatusParser::parseGlobal
    )

    override suspend fun requestChannelStatus(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ) = executor.execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.STATUS_GET,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1StatusParser::parseChannel
    )

    override suspend fun requestProgress(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ) = executor.execute(
        deviceUid = deviceUid,
        action = DeviceDosingV1Contract.Action.PROGRESS_GET,
        dataFactory = { channelJson(channelKey) },
        parser = DeviceDosingV1StatusParser::parseProgress
    )
}

private class DeviceDosingV1ConfigurationRepository(gateway: DeviceRuntimeCommandGateway) :
    DeviceDosingV1ConfigurationCommands {
    private val executor = DeviceDosingV1CommandExecutor(gateway)

    override suspend fun applyConfig(deviceUid: DeviceUid, request: DeviceDosingV1ConfigApplyRequest) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.CONFIG_APPLY,
            request::toJson, DeviceDosingV1SavedMutationParser::parseConfigApply)

    override suspend fun applyProgram(deviceUid: DeviceUid, request: DeviceDosingV1ProgramApplyRequest) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.PROGRAM_APPLY,
            request::toJson, DeviceDosingV1SavedMutationParser::parseProgramApply)

    override suspend fun resetChannel(deviceUid: DeviceUid, request: DeviceDosingV1ChannelResetRequest) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.CHANNEL_RESET,
            request::toJson, DeviceDosingV1SavedMutationParser::parseChannelReset)
}

private class DeviceDosingV1CalibrationRepository(gateway: DeviceRuntimeCommandGateway) :
    DeviceDosingV1CalibrationCommands {
    private val executor = DeviceDosingV1CommandExecutor(gateway)

    override suspend fun startPrime(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.PRIME_START,
            { channelJson(channelKey) }, DeviceDosingV1MutationParser::parsePrimeStart)

    override suspend fun stopPrime(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.PRIME_STOP,
            { channelJson(channelKey) }, DeviceDosingV1MutationParser::parsePrimeStop)

    override suspend fun startCalibration(
        deviceUid: DeviceUid,
        request: DeviceDosingV1CalibrationStartRequest
    ) = executor.execute(deviceUid, DeviceDosingV1Contract.Action.CALIBRATION_START,
        request::toJson, DeviceDosingV1MutationParser::parseCalibrationStart)

    override suspend fun finishCalibration(
        deviceUid: DeviceUid,
        request: DeviceDosingV1CalibrationFinishRequest
    ) = executor.execute(deviceUid, DeviceDosingV1Contract.Action.CALIBRATION_FINISH,
        request::toJson, DeviceDosingV1MutationParser::parseCalibrationFinish)

    override suspend fun confirmCalibration(
        deviceUid: DeviceUid,
        request: DeviceDosingV1CalibrationConfirmRequest
    ) = executor.execute(deviceUid, DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM,
        request::toJson, DeviceDosingV1MutationParser::parseCalibrationConfirm)

    override suspend fun cancelCalibration(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.CALIBRATION_CANCEL,
            { channelJson(channelKey) }, DeviceDosingV1MutationParser::parseCalibrationCancel)
}

private class DeviceDosingV1RuntimeRepository(gateway: DeviceRuntimeCommandGateway) :
    DeviceDosingV1RuntimeCommands {
    private val executor = DeviceDosingV1CommandExecutor(gateway)

    override suspend fun doseNow(deviceUid: DeviceUid, request: DeviceDosingV1DoseNowRequest) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.DOSE_NOW,
            request::toJson, DeviceDosingV1MutationParser::parseDoseNow)

    override suspend fun stopDose(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.DOSE_STOP,
            { channelJson(channelKey) }, DeviceDosingV1MutationParser::parseDoseStop)

    override suspend fun refillReservoir(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey) =
        executor.execute(deviceUid, DeviceDosingV1Contract.Action.RESERVOIR_REFILL,
            { channelJson(channelKey) }, DeviceDosingV1MutationParser::parseReservoirRefill)
}

private class DeviceDosingV1CommandExecutor(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun <T> execute(
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
