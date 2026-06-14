package com.aqua.aqualight.data.devices.api.light

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient

class V1LightApi(
    private val client: V1HttpClient
) : LightApi {

    override suspend fun readDeviceState(
        connection: AquaDeviceConnection
    ): ApiResult<LightDeviceState> {
        return ApiResult.notConnected()
    }

    override suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<LightStatus> {
        return ApiResult.notConnected()
    }

    override suspend fun readPrograms(
        connection: AquaDeviceConnection
    ): ApiResult<List<LightProgram>> {
        return ApiResult.notConnected()
    }

    override suspend fun writeProgram(
        connection: AquaDeviceConnection,
        program: LightProgram
    ): ApiResult<Unit> {
        return ApiResult.notConnected()
    }

    override suspend fun setManual(
        connection: AquaDeviceConnection,
        request: LightManualRequest
    ): ApiResult<Unit> {
        return ApiResult.notConnected()
    }

    override suspend fun resumeAuto(
        connection: AquaDeviceConnection
    ): ApiResult<Unit> {
        return ApiResult.notConnected()
    }

    override suspend fun setAutomation(
        connection: AquaDeviceConnection,
        request: LightAutomationRequest
    ): ApiResult<Unit> {
        return ApiResult.notConnected()
    }
}
