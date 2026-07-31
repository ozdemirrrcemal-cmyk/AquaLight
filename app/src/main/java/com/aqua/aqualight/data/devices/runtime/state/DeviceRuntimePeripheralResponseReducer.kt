package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal object DeviceRuntimePeripheralResponseReducer {

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = when (message.module) {
        AqlWsContract.MODULE_COOLING -> reduceCooling(previous, message, nowMillis)
        AqlWsContract.MODULE_TIMER -> reduceTimer(previous, message, nowMillis)
        AqlWsContract.MODULE_DOSING -> reduceDosing(previous, message, nowMillis)
        else -> mutationResult(previous, message)
    }

    private fun reduceCooling(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = if (message.action == AqlWsContract.ACTION_COOLING_STATUS_GET) {
        DeviceRuntimeParsedRouter.route(
            context = responseContext(previous, message, nowMillis),
            parsed = runCatching { DeviceCoolingStatusParser.parse(message.data) },
            spec = DeviceRuntimeRouteSpec(
                current = previous.cooling,
                failureTarget = DeviceRuntimeRefreshTarget.COOLING,
                apply = { state, value -> state.copy(cooling = value) }
            )
        )
    } else {
        mutationResult(previous, message)
    }

    private fun reduceTimer(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = if (message.action == AqlWsContract.ACTION_TIMER_STATUS_GET) {
        DeviceRuntimeParsedRouter.route(
            context = responseContext(previous, message, nowMillis),
            parsed = runCatching { DeviceTimerStatusParser.parse(message.data) },
            spec = DeviceRuntimeRouteSpec(
                current = previous.timer,
                failureTarget = DeviceRuntimeRefreshTarget.TIMER,
                apply = { state, value -> state.copy(timer = value) }
            )
        )
    } else {
        mutationResult(previous, message)
    }

    private fun reduceDosing(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = if (message.action == AqlWsContract.ACTION_DOSING_STATUS_GET) {
        DeviceRuntimeParsedRouter.route(
            context = responseContext(previous, message, nowMillis),
            parsed = runCatching { DeviceDosingStatusParser.parse(message.data) },
            spec = DeviceRuntimeRouteSpec(
                current = previous.dosing,
                failureTarget = DeviceRuntimeRefreshTarget.DOSING,
                apply = { state, value -> state.copy(dosing = value) }
            )
        )
    } else {
        mutationResult(previous, message)
    }

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

    private fun mutationResult(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response
    ): DeviceRuntimeRouteResult = DeviceRuntimeRouteResult(
        state = previous.copy(lastFault = null),
        refreshTargets = DeviceRuntimeRefreshPolicy.mutationTargets(
            message.module,
            message.action
        )
    )
}
