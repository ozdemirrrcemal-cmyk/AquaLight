package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal data class DeviceRuntimeRouteContext(
    val previous: DeviceRuntimeState,
    val message: AqlWsIncomingMessage,
    val nowMillis: Long,
    val source: DeviceRuntimeValueSource
)

internal data class DeviceRuntimeRouteSpec<T>(
    val current: DeviceRuntimeValue<T>,
    val failureTarget: DeviceRuntimeRefreshTarget,
    val apply: (DeviceRuntimeState, DeviceRuntimeValue<T>) -> DeviceRuntimeState
)

internal object DeviceRuntimeParsedRouter {

    fun <T> route(
        context: DeviceRuntimeRouteContext,
        parsed: Result<T>,
        spec: DeviceRuntimeRouteSpec<T>
    ): DeviceRuntimeRouteResult = parsed.fold(
        onSuccess = { value ->
            DeviceRuntimeRouteResult(
                spec.apply(
                    context.previous.copy(lastFault = null),
                    spec.current.ready(
                        value,
                        context.source,
                        context.message.id,
                        context.nowMillis
                    )
                )
            )
        },
        onFailure = { error ->
            val fault = DeviceRuntimeFaultFactory.fromPayload(
                context.message,
                error,
                context.nowMillis
            )
            DeviceRuntimeRouteResult(
                state = spec.apply(
                    context.previous.copy(lastFault = fault),
                    spec.current.failed(fault)
                ),
                refreshTargets = setOf(spec.failureTarget)
            )
        }
    )
}
