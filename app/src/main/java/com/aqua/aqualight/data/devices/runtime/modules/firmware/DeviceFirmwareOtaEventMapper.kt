package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

object DeviceFirmwareOtaEventMapper {

    fun parse(message: AqlWsIncomingMessage?): DeviceFirmwareOtaSnapshot? {
        val event = message as? AqlWsIncomingMessage.Event ?: return null
        if (event.event != DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS &&
            event.event != DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
        ) {
            return null
        }

        val data = event.json.optJSONObject("data") ?: JSONObject()
        return DeviceFirmwareStatusParser.parseOtaProgressEvent(data)
    }

    fun isOtaEvent(message: AqlWsIncomingMessage?): Boolean {
        val event = message as? AqlWsIncomingMessage.Event ?: return false
        return event.event == DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS ||
            event.event == DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
    }
}
