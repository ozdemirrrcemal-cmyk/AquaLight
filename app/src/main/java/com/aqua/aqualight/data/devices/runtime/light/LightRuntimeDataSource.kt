package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface LightRuntimeDataSource {

    suspend fun readSnapshot(
        connection: AquaDeviceConnection
    ): ApiResult<LightRuntimeSnapshot>
}
