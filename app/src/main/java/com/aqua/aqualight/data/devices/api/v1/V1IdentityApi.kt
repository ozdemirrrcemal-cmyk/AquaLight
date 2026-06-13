package com.aqua.aqualight.data.devices.api.v1

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity

class V1IdentityApi(
    private val client: V1HttpClient = V1HttpClient.NotConnected,
    private val parser: V1JsonParser = V1JsonParser()
) {

    suspend fun readIdentity(
        connection: AquaDeviceConnection
    ): ApiResult<DeviceIdentity> {
        return when (val result = client.get(connection, V1Endpoint.IDENTITY)) {
            is ApiResult.Success -> parser.parseIdentity(result.value)
            is ApiResult.Error -> result
        }
    }
}
