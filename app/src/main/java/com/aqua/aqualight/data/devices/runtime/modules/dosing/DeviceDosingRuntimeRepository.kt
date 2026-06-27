package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceDosingRuntimeRepository(
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    fun requestStatus(deviceUid: DeviceUid): DeviceDosingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.STATUS_GET
        )
    }

    fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceDosingConfigApplyPayload
    ): DeviceDosingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
            data = payload.toJson()
        )
    }

    fun primeStart(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceDosingCommandResult {
        return sendChannelKey(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.PRIME_START,
            channelKey = channelKey
        )
    }

    fun primeStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceDosingCommandResult {
        return sendChannelKey(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.PRIME_STOP,
            channelKey = channelKey
        )
    }

    fun calibrationStart(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationStartPayload
    ): DeviceDosingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.CALIBRATION_START,
            data = payload.toJson()
        )
    }

    fun calibrationFinish(
        deviceUid: DeviceUid,
        payload: DeviceDosingCalibrationFinishPayload
    ): DeviceDosingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH,
            data = payload.toJson()
        )
    }

    fun calibrationConfirm(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceDosingCommandResult {
        return sendChannelKey(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
            channelKey = channelKey
        )
    }

    fun calibrationCancel(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceDosingCommandResult {
        return sendChannelKey(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL,
            channelKey = channelKey
        )
    }

    fun doseNow(
        deviceUid: DeviceUid,
        payload: DeviceDosingDoseNowPayload
    ): DeviceDosingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.DOSE_NOW,
            data = payload.toJson()
        )
    }

    fun doseStop(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceDosingCommandResult {
        return sendChannelKey(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.DOSE_STOP,
            channelKey = channelKey
        )
    }

    fun reservoirRefill(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceDosingCommandResult {
        return sendChannelKey(
            deviceUid = deviceUid,
            action = DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL,
            channelKey = channelKey
        )
    }

    private fun sendChannelKey(
        deviceUid: DeviceUid,
        action: String,
        channelKey: String
    ): DeviceDosingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = action,
            data = DeviceDosingChannelKeyPayload(channelKey = channelKey).toJson()
        )
    }

    private fun send(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceDosingCommandResult {
        val commandClient = commandClientProvider(deviceUid)

        if (commandClient == null) {
            return DeviceDosingCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )
        }

        val messageId = commandClient.command(
            module = DeviceDosingRuntimeContract.MODULE,
            action = action,
            data = data
        )

        return DeviceDosingCommandResult(
            sent = messageId != null,
            action = action,
            messageId = messageId.orEmpty(),
            errorMessage = if (messageId != null) "" else "WebSocket send failed"
        )
    }

    companion object {
        fun singleSession(commandClient: AqlWsCommandClient): DeviceDosingRuntimeRepository {
            return DeviceDosingRuntimeRepository { commandClient }
        }
    }
}
