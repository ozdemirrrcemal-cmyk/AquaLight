package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

object DeviceFirmwareOtaEventMapper {

    fun parse(message: AqlWsIncomingMessage?): DeviceFirmwareOtaSnapshot? {
        val event = message as? AqlWsIncomingMessage.Event
        return if (event != null && isOtaEvent(event)) {
            runCatching { DeviceFirmwareCommandParsers.parseOtaEvent(event.data) }.getOrNull()
        } else {
            null
        }
    }

    fun isOtaEvent(message: AqlWsIncomingMessage?): Boolean {
        val event = message as? AqlWsIncomingMessage.Event
        return event != null && (
            event.action == DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS ||
                event.action == DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
            )
    }
}
