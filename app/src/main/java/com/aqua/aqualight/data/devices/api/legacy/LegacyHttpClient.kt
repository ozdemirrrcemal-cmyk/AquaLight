package com.aqua.aqualight.data.devices.api.legacy

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface LegacyHttpClient {

    suspend fun get(
        connection: AquaDeviceConnection,
        endpoint: LegacyEndpoint,
        query: Map<String, String> = emptyMap()
    ): ApiResult<String>

    suspend fun set(
        connection: AquaDeviceConnection,
        command: String
    ): ApiResult<String>

    object NotConnected : LegacyHttpClient {
        override suspend fun get(
            connection: AquaDeviceConnection,
            endpoint: LegacyEndpoint,
            query: Map<String, String>
        ): ApiResult<String> {
            return ApiResult.notConnected()
        }

        override suspend fun set(
            connection: AquaDeviceConnection,
            command: String
        ): ApiResult<String> {
            return ApiResult.notConnected()
        }
    }
}
