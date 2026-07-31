package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal object DeviceRuntimeEventReducer {

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Event,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        val specialized = routeOtaOrNull(previous, message, nowMillis)
            ?: routeLightProtectionOrNull(previous, message, nowMillis)
        return specialized ?: DeviceRuntimeRouteResult(
            state = previous.copy(lastFault = null),
            refreshTargets = DeviceRuntimeRefreshPolicy.eventTargets(message.action)
        )
    }

    private fun routeOtaOrNull(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Event,
        nowMillis: Long
    ): DeviceRuntimeRouteResult? {
        val isOtaEvent = message.action == AqlWsContract.Event.FIRMWARE_OTA_PROGRESS ||
            message.action == AqlWsContract.Event.FIRMWARE_OTA_COMPLETED
        return if (isOtaEvent) {
            DeviceRuntimeParsedRouter.route(
                context = eventContext(previous, message, nowMillis),
                parsed = DeviceFirmwareStatusParser.parseOtaProgressEventExact(message.data),
                spec = DeviceRuntimeRouteSpec(
                    current = previous.ota,
                    failureTarget = DeviceRuntimeRefreshTarget.OTA,
                    apply = { state, value -> state.copy(ota = value) }
                )
            )
        } else {
            null
        }
    }

    private fun routeLightProtectionOrNull(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Event,
        nowMillis: Long
    ): DeviceRuntimeRouteResult? {
        val isLightChange = message.module == AqlWsContract.MODULE_LIGHT &&
            message.action == AqlWsContract.Event.LIGHT_STATUS_CHANGED
        val status = if (isLightChange) {
            message.data.optJSONObject("result")?.optJSONObject("status")
        } else {
            null
        }
        return status?.let { payload ->
            DeviceRuntimeParsedRouter.route(
                context = eventContext(previous, message, nowMillis),
                parsed = DeviceLightTemperatureProtectionParser.parseStatus(payload),
                spec = DeviceRuntimeRouteSpec(
                    current = previous.lightTemperatureProtection,
                    failureTarget = DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION,
                    apply = { state, value -> state.copy(lightTemperatureProtection = value) }
                )
            )
        }
    }

    private fun eventContext(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Event,
        nowMillis: Long
    ): DeviceRuntimeRouteContext = DeviceRuntimeRouteContext(
        previous = previous,
        message = message,
        nowMillis = nowMillis,
        source = DeviceRuntimeValueSource.EVENT
    )
}
