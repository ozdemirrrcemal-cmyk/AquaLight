package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal object DeviceRuntimeFirmwareResponseReducer {

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = when (message.action) {
        AqlWsContract.ACTION_FIRMWARE_STATUS_GET -> routeFirmware(previous, message, nowMillis)
        AqlWsContract.ACTION_FIRMWARE_OTA_STATUS -> routeOta(
            previous,
            message,
            nowMillis,
            DeviceFirmwareStatusParser.parseOtaStatusResponseExact(message.data)
        )
        AqlWsContract.ACTION_FIRMWARE_OTA_START -> routeOta(
            previous,
            message,
            nowMillis,
            DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(message.data).map { it.ota }
        )
        AqlWsContract.ACTION_FIRMWARE_OTA_CLEAR -> routeOta(
            previous,
            message,
            nowMillis,
            DeviceFirmwareStatusParser.parseOtaClearResultExact(message.data).map { it.ota }
        )
        else -> DeviceRuntimeRouteResult(
            state = previous.copy(lastFault = null),
            refreshTargets = DeviceRuntimeRefreshPolicy.mutationTargets(
                message.module,
                message.action
            )
        )
    }

    private fun routeFirmware(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = DeviceRuntimeParsedRouter.route(
        context = responseContext(previous, message, nowMillis),
        parsed = DeviceRuntimeFirmwareStatusParser.parse(message.data),
        spec = DeviceRuntimeRouteSpec(
            current = previous.firmware,
            failureTarget = DeviceRuntimeRefreshTarget.FIRMWARE,
            apply = { state, value -> state.copy(firmware = value) }
        )
    )

    private fun routeOta(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long,
        parsed: Result<DeviceFirmwareOtaSnapshot>
    ): DeviceRuntimeRouteResult = DeviceRuntimeParsedRouter.route(
        context = responseContext(previous, message, nowMillis),
        parsed = parsed,
        spec = DeviceRuntimeRouteSpec(
            current = previous.ota,
            failureTarget = DeviceRuntimeRefreshTarget.OTA,
            apply = { state, value -> state.copy(ota = value) }
        )
    )

    private fun responseContext(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteContext = DeviceRuntimeRouteContext(
        previous = previous,
        message = message,
        nowMillis = nowMillis,
        source = DeviceRuntimeValueSource.RESPONSE
    )
}
