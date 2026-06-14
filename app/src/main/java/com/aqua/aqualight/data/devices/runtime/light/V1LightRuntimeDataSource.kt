package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.light.LightApi
import com.aqua.aqualight.data.devices.api.model.ApiResult

class V1LightRuntimeDataSource(
    private val lightApi: LightApi
) : LightRuntimeDataSource {

    override suspend fun readSnapshot(
        connection: AquaDeviceConnection
    ): ApiResult<LightRuntimeSnapshot> {
        return when (val state = lightApi.readDeviceState(connection)) {
            is ApiResult.Success -> ApiResult.success(
                LightRuntimeSnapshot.fromDeviceState(
                    state = state.value,
                    source = LightRuntimeSource.V1
                )
            )
            is ApiResult.Error -> state
        }
    }
}
