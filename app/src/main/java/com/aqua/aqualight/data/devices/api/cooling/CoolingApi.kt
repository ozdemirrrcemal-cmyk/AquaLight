package com.aqua.aqualight.data.devices.api.cooling

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface CoolingApi {

    suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<CoolingStatus>

    suspend fun writeSettings(
        connection: AquaDeviceConnection,
        settings: CoolingSettings
    ): ApiResult<Unit>
}
