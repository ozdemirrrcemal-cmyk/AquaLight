package com.aqua.aqualight.data.devices.api.cooling

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient

class V1CoolingApi(
    private val client: V1HttpClient
) : CoolingApi {

    override suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<CoolingStatus> {
        return ApiResult.notConnected()
    }

    override suspend fun writeSettings(
        connection: AquaDeviceConnection,
        settings: CoolingSettings
    ): ApiResult<Unit> {
        return ApiResult.notConnected()
    }
}
