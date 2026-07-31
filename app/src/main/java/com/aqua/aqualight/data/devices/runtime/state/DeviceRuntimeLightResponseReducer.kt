package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal object DeviceRuntimeLightResponseReducer {

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = when (message.action) {
        AqlWsContract.ACTION_LIGHT_STATUS_GET -> routeLight(previous, message, nowMillis)
        AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET -> routeProtection(
            previous,
            message,
            nowMillis,
            DeviceLightTemperatureProtectionParser.parseStatus(message.data)
        )
        AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_SET -> routeProtection(
            previous,
            message,
            nowMillis,
            DeviceLightTemperatureProtectionParser.parseSetResult(message.data).map { it.status }
        )
        else -> DeviceRuntimeRouteResult(
            state = previous.copy(lastFault = null),
            refreshTargets = DeviceRuntimeRefreshPolicy.mutationTargets(
                message.module,
                message.action
            )
        )
    }

    private fun routeLight(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = DeviceRuntimeParsedRouter.route(
        context = responseContext(previous, message, nowMillis),
        parsed = runCatching { DeviceLightStatusParser.parse(message.data) },
        spec = DeviceRuntimeRouteSpec(
            current = previous.light,
            failureTarget = DeviceRuntimeRefreshTarget.LIGHT,
            apply = { state, value -> state.copy(light = value) }
        )
    )

    private fun routeProtection(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long,
        parsed: Result<DeviceLightTemperatureProtectionStatus>
    ): DeviceRuntimeRouteResult = DeviceRuntimeParsedRouter.route(
        context = responseContext(previous, message, nowMillis),
        parsed = parsed,
        spec = DeviceRuntimeRouteSpec(
            current = previous.lightTemperatureProtection,
            failureTarget = DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION,
            apply = { state, value -> state.copy(lightTemperatureProtection = value) }
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
