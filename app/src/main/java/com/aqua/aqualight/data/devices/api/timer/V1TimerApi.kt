package com.aqua.aqualight.data.devices.api.timer

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient

class V1TimerApi(
    private val client: V1HttpClient
) : TimerApi {

    override suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<TimerStatus> {
        return ApiResult.notConnected()
    }

    override suspend fun readSchedules(
        connection: AquaDeviceConnection
    ): ApiResult<List<TimerSchedule>> {
        return ApiResult.notConnected()
    }

    override suspend fun writeSchedule(
        connection: AquaDeviceConnection,
        schedule: TimerSchedule
    ): ApiResult<Unit> {
        return ApiResult.notConnected()
    }
}
