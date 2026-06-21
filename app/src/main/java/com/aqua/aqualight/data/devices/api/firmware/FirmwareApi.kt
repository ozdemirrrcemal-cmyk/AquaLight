package com.aqua.aqualight.data.devices.api.firmware

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface FirmwareApi {

    suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<FirmwareStatus>

    /**
     * Starts a secure firmware OTA request. Commercial firmware protects this
     * endpoint with the pairing token collected during device setup.
     */
    suspend fun startOta(
        connection: AquaDeviceConnection,
        request: FirmwareOtaRequest
    ): ApiResult<FirmwareOtaResult>
}
