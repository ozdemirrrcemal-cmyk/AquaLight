package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

object DeviceFirmwareOtaEventMapper {

    fun parse(message: AqlWsIncomingMessage?): DeviceFirmwareOtaSnapshot? {
        val event = message as? AqlWsIncomingMessage.Event ?: return null
        if (event.action != DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS &&
            event.action != DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
        ) {
            return null
        }

        val data = event.data
        return DeviceFirmwareStatusParser.parseOtaProgressEvent(data)
    }

    fun isOtaEvent(message: AqlWsIncomingMessage?): Boolean {
        val event = message as? AqlWsIncomingMessage.Event ?: return false
        return event.action == DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS ||
            event.action == DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
    }
}
