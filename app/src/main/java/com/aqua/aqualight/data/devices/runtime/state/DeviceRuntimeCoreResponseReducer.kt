package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal object DeviceRuntimeCoreResponseReducer {

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = when (message.module) {
        AqlWsContract.MODULE_DEVICE -> reduceDevice(previous, message, nowMillis)
        AqlWsContract.MODULE_SECURITY -> reduceSecurity(previous, message, nowMillis)
        AqlWsContract.MODULE_NETWORK -> reduceNetwork(previous, message, nowMillis)
        AqlWsContract.MODULE_TIME -> reduceTime(previous, message, nowMillis)
        else -> mutationResult(previous, message)
    }

    private fun reduceDevice(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = when (message.action) {
        AqlWsContract.ACTION_DEVICE_STATUS_GET -> reduceDeviceStatus(previous, message, nowMillis)
        AqlWsContract.ACTION_DEVICE_NAME_SET -> reduceDeviceNameSet(previous, message, nowMillis)
        else -> mutationResult(previous, message)
    }

    private fun reduceDeviceStatus(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = DeviceRuntimeParsedRouter.route(
        context = responseContext(previous, message, nowMillis),
        parsed = DeviceRuntimeCoreStatusParser.parseDeviceStatus(message.data),
        spec = DeviceRuntimeRouteSpec(
            current = previous.device,
            failureTarget = DeviceRuntimeRefreshTarget.DEVICE,
            apply = { state, value -> state.copy(device = value) }
        )
    )

    private fun reduceDeviceNameSet(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        val statusJson = message.data.optJSONObject("status")
        val current = previous.device.value
        return if (statusJson == null || current == null) {
            DeviceRuntimeRouteResult(
                state = previous,
                refreshTargets = setOf(DeviceRuntimeRefreshTarget.DEVICE)
            )
        } else {
            DeviceRuntimeParsedRouter.route(
                context = responseContext(previous, message, nowMillis),
                parsed = DeviceRuntimeCoreStatusParser.parseNameStatus(statusJson).map { name ->
                    current.copy(device = name)
                },
                spec = DeviceRuntimeRouteSpec(
                    current = previous.device,
                    failureTarget = DeviceRuntimeRefreshTarget.DEVICE,
                    apply = { state, value -> state.copy(device = value) }
                )
            )
        }
    }

    private fun reduceSecurity(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = if (message.action == AqlWsContract.ACTION_SECURITY_STATUS_GET) {
        DeviceRuntimeParsedRouter.route(
            context = responseContext(previous, message, nowMillis),
            parsed = DeviceRuntimeCoreStatusParser.parseSecurityStatus(message.data),
            spec = DeviceRuntimeRouteSpec(
                current = previous.security,
                failureTarget = DeviceRuntimeRefreshTarget.SECURITY,
                apply = { state, value -> state.copy(security = value) }
            )
        )
    } else {
        mutationResult(previous, message)
    }

    private fun reduceNetwork(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = if (message.action == AqlWsContract.ACTION_NETWORK_STATUS_GET) {
        DeviceRuntimeParsedRouter.route(
            context = responseContext(previous, message, nowMillis),
            parsed = DeviceNetworkStatusParser.parse(message.data),
            spec = DeviceRuntimeRouteSpec(
                current = previous.network,
                failureTarget = DeviceRuntimeRefreshTarget.NETWORK,
                apply = { state, value -> state.copy(network = value) }
            )
        )
    } else {
        mutationResult(previous, message)
    }

    private fun reduceTime(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = if (message.action == AqlWsContract.ACTION_TIME_STATUS_GET) {
        DeviceRuntimeParsedRouter.route(
            context = responseContext(previous, message, nowMillis),
            parsed = runCatching { DeviceTimeStatusParser.parse(message.data) },
            spec = DeviceRuntimeRouteSpec(
                current = previous.time,
                failureTarget = DeviceRuntimeRefreshTarget.TIME,
                apply = { state, value -> state.copy(time = value) }
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
