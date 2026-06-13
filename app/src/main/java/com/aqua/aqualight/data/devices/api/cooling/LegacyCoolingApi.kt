package com.aqua.aqualight.data.devices.api.cooling

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.legacy.LegacyHttpClient
import com.aqua.aqualight.data.devices.api.model.ApiResult

class LegacyCoolingApi(
    private val client: LegacyHttpClient
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
