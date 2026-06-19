package com.aqua.aqualight.data.devices.api.v1

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface V1HttpClient {

    suspend fun get(
        connection: AquaDeviceConnection,
        endpoint: V1Endpoint
    ): ApiResult<String>

    suspend fun post(
        connection: AquaDeviceConnection,
        endpoint: V1Endpoint,
        body: String
    ): ApiResult<String>

    suspend fun put(
        connection: AquaDeviceConnection,
        endpoint: V1Endpoint,
        body: String
    ): ApiResult<String>

    object NotConnected : V1HttpClient {
        override suspend fun get(connection: AquaDeviceConnection, endpoint: V1Endpoint): ApiResult<String> =
            ApiResult.notConnected()

        override suspend fun post(connection: AquaDeviceConnection, endpoint: V1Endpoint, body: String): ApiResult<String> =
            ApiResult.notConnected()

        override suspend fun put(connection: AquaDeviceConnection, endpoint: V1Endpoint, body: String): ApiResult<String> =
            ApiResult.notConnected()
    }
}
