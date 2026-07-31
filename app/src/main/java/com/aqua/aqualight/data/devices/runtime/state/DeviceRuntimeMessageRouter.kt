package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

enum class DeviceRuntimeRefreshTarget {
    DEVICE,
    SECURITY,
    NETWORK,
    TIME,
    FIRMWARE,
    OTA,
    LIGHT,
    LIGHT_TEMPERATURE_PROTECTION,
    COOLING,
    TIMER,
    DOSING
}

data class DeviceRuntimeRouteResult(
    val state: DeviceRuntimeState,
    val refreshTargets: Set<DeviceRuntimeRefreshTarget> = emptySet()
)

object DeviceRuntimeMessageRouter {

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        val recorded = DeviceRuntimeWireRecorder.record(previous, message, nowMillis)
        return when (message) {
            is AqlWsIncomingMessage.Response -> DeviceRuntimeResponseReducer.reduce(
                recorded,
                message,
                nowMillis
            )
            is AqlWsIncomingMessage.Event -> DeviceRuntimeEventReducer.reduce(
                recorded,
                message,
                nowMillis
            )
            is AqlWsIncomingMessage.Error -> DeviceRuntimeRouteResult(
                state = recorded.copy(
                    lastFault = DeviceRuntimeFaultFactory.fromError(message, nowMillis)
                )
            )
        }
    }
}
