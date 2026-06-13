package com.aqua.aqualight.data.devices.api.light

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface LightApi {

    suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<LightStatus>

    suspend fun readPrograms(
        connection: AquaDeviceConnection
    ): ApiResult<List<LightProgram>>

    suspend fun writeProgram(
        connection: AquaDeviceConnection,
        program: LightProgram
    ): ApiResult<Unit>

    suspend fun setManual(
        connection: AquaDeviceConnection,
        request: LightManualRequest
    ): ApiResult<Unit>

    suspend fun setAutomation(
        connection: AquaDeviceConnection,
        request: LightAutomationRequest
    ): ApiResult<Unit>
}
