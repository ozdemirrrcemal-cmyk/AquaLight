package com.aqua.aqualight.data.devices.runtime.modules.common

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

internal class DeviceRuntimeJsonCommand<T>(
    override val module: String,
    override val action: String,
    private val dataFactory: () -> JSONObject = ::JSONObject,
    private val successParser: (JSONObject) -> T
) : DeviceRuntimeCommand<T> {
    override fun encodeData(): JSONObject = dataFactory()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): T =
        successParser(response.data)
}
