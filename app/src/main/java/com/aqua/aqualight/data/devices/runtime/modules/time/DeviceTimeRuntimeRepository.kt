package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceTimeRuntimeRepository(
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceTimeCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimeRuntimeContract.Action.STATUS_GET
        )
    }

    fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceTimeConfigApplyPayload = DeviceSystemTimePayloadFactory.configFromSystem()
    ): DeviceTimeCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimeRuntimeContract.Action.CONFIG_APPLY,
            data = payload.toJson()
        )
    }

    fun syncPhoneNow(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceTimeCommandResult {
        return syncPhone(
            deviceUid = deviceUid,
            payload = DeviceSystemTimePayloadFactory.phoneSyncNow(save = save)
        )
    }

    fun syncPhone(
        deviceUid: DeviceUid,
        payload: DevicePhoneSyncPayload
    ): DeviceTimeCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
            data = payload.toJson()
        )
    }

    fun syncNtp(
        deviceUid: DeviceUid
    ): DeviceTimeCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimeRuntimeContract.Action.NTP_SYNC
        )
    }

    fun setRtc(
        deviceUid: DeviceUid,
        payload: DeviceManualRtcPayload
    ): DeviceTimeCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimeRuntimeContract.Action.RTC_SET,
            data = payload.toJson()
        )
    }

    private fun send(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceTimeCommandResult {
        val commandClient = commandClientProvider(deviceUid)

        if (commandClient == null) {
            return DeviceTimeCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )
        }

        val sent = commandClient.command(
            module = DeviceTimeRuntimeContract.MODULE,
            action = action,
            data = data
        )

        return DeviceTimeCommandResult(
            sent = sent,
            action = action,
            errorMessage = if (sent) "" else "WebSocket send failed"
        )
    }

    companion object {
        fun singleSession(
            commandClient: AqlWsCommandClient
        ): DeviceTimeRuntimeRepository {
            return DeviceTimeRuntimeRepository { commandClient }
        }
    }
}
