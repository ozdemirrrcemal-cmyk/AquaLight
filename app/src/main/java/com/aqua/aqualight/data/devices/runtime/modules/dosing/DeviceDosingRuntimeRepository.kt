package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceDosingRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceDosingStatus> =
        commandGateway.execute(deviceUid, DeviceDosingStatusGetCommand)

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceDosingConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingConfigApplyResult> =
        commandGateway.execute(
            deviceUid,
            DeviceDosingCommand(
                action = AqlWsContract.ACTION_DOSING_CONFIG_APPLY,
                encode = payload::toJson,
                parse = DeviceDosingStatusParser::parseConfigApply
            )
        )

    suspend fun primeStart(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingManualPumpResult> =
        commandGateway.execute(
            deviceUid,
            channelCommand(
                action = AqlWsContract.ACTION_DOSING_PRIME_START,
                channelKey = channelKey,
                parse = DeviceDosingStatusParser::parsePrimeStart
            )
        )

    suspend fun primeStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingManualPumpResult> =
        commandGateway.execute(
            deviceUid,
            channelCommand(
                action = AqlWsContract.ACTION_DOSING_PRIME_STOP,
                channelKey = channelKey,
                parse = DeviceDosingStatusParser::parsePrimeStop
            )
        )

    suspend fun calibrationStart(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationStartPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationStartResult> =
        commandGateway.execute(
            deviceUid,
            DeviceDosingCommand(
                action = AqlWsContract.ACTION_DOSING_CALIBRATION_START,
                encode = payload::toJson,
                parse = DeviceDosingStatusParser::parseCalibrationStart
            )
        )

    suspend fun calibrationFinish(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationFinishPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationFinishResult> =
        commandGateway.execute(
            deviceUid,
            DeviceDosingCommand(
                action = AqlWsContract.ACTION_DOSING_CALIBRATION_FINISH,
                encode = payload::toJson,
                parse = DeviceDosingStatusParser::parseCalibrationFinish
            )
        )

    suspend fun calibrationConfirm(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationConfirmResult> =
        commandGateway.execute(
            deviceUid,
            channelCommand(
                action = AqlWsContract.ACTION_DOSING_CALIBRATION_CONFIRM,
                channelKey = channelKey,
                parse = DeviceDosingStatusParser::parseCalibrationConfirm
            )
        )

    suspend fun calibrationCancel(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingCalibrationCancelResult> =
        commandGateway.execute(
            deviceUid,
            channelCommand(
                action = AqlWsContract.ACTION_DOSING_CALIBRATION_CANCEL,
                channelKey = channelKey,
                parse = DeviceDosingStatusParser::parseCalibrationCancel
            )
        )

    suspend fun doseNow(
        deviceUid: DeviceUid,
        payload: DeviceDosingDoseNowPayload
    ): DeviceRuntimeCommandOutcome<DeviceDosingDoseNowResult> =
        commandGateway.execute(
            deviceUid,
            DeviceDosingCommand(
                action = AqlWsContract.ACTION_DOSING_DOSE_NOW,
                encode = payload::toJson,
                parse = DeviceDosingStatusParser::parseDoseNow
            )
        )

    suspend fun doseStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingManualPumpResult> =
        commandGateway.execute(
            deviceUid,
            channelCommand(
                action = AqlWsContract.ACTION_DOSING_DOSE_STOP,
                channelKey = channelKey,
                parse = DeviceDosingStatusParser::parseDoseStop
            )
        )

    suspend fun reservoirRefill(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingReservoirRefillResult> =
        commandGateway.execute(
            deviceUid,
            channelCommand(
                action = AqlWsContract.ACTION_DOSING_RESERVOIR_REFILL,
                channelKey = channelKey,
                parse = DeviceDosingStatusParser::parseReservoirRefill
            )
        )
}

private data object DeviceDosingStatusGetCommand : DeviceRuntimeCommand<DeviceDosingStatus> {
    override val module: String = AqlWsContract.MODULE_DOSING
    override val action: String = AqlWsContract.ACTION_DOSING_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceDosingStatus {
        require(response.statusCode == HTTP_OK)
        return DeviceDosingStatusParser.parse(response.data)
    }
}

private class DeviceDosingCommand<T>(
    override val action: String,
    private val encode: () -> JSONObject,
    private val parse: (JSONObject) -> T
) : DeviceRuntimeCommand<T> {
    override val module: String = AqlWsContract.MODULE_DOSING

    override fun encodeData(): JSONObject = encode()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): T {
        require(response.statusCode == HTTP_OK)
        return parse(response.data)
    }
}

private fun <T> channelCommand(
    action: String,
    channelKey: String,
    parse: (JSONObject) -> T
): DeviceRuntimeCommand<T> {
    val payload = DeviceDosingChannelKeyPayload(channelKey = channelKey)
    return DeviceDosingCommand(
        action = action,
        encode = payload::toJson,
        parse = parse
    )
}

private const val HTTP_OK = 200
