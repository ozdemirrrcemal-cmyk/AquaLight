package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal object DeviceRuntimeResponseReducer {

    fun reduce(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult {
        val rejected = !message.ok
        return if (rejected) {
            DeviceRuntimeRouteResult(
                previous.copy(
                    lastFault = DeviceRuntimeFaultFactory.fromRejectedResponse(
                        message,
                        nowMillis
                    )
                )
            )
        } else {
            reduceSuccessful(previous, message, nowMillis)
        }
    }

    private fun reduceSuccessful(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeRouteResult = when (message.module) {
        AqlWsContract.MODULE_DEVICE,
        AqlWsContract.MODULE_SECURITY,
        AqlWsContract.MODULE_NETWORK,
        AqlWsContract.MODULE_TIME -> DeviceRuntimeCoreResponseReducer.reduce(
            previous,
            message,
            nowMillis
        )
        AqlWsContract.MODULE_FIRMWARE -> DeviceRuntimeFirmwareResponseReducer.reduce(
            previous,
            message,
            nowMillis
        )
        AqlWsContract.MODULE_LIGHT -> DeviceRuntimeLightResponseReducer.reduce(
            previous,
            message,
            nowMillis
        )
        AqlWsContract.MODULE_COOLING,
        AqlWsContract.MODULE_TIMER,
        AqlWsContract.MODULE_DOSING -> DeviceRuntimePeripheralResponseReducer.reduce(
            previous,
            message,
            nowMillis
        )
        else -> DeviceRuntimeRouteResult(
            state = previous.copy(lastFault = null),
            refreshTargets = DeviceRuntimeRefreshPolicy.mutationTargets(
                message.module,
                message.action
            )
        )
    }
}
