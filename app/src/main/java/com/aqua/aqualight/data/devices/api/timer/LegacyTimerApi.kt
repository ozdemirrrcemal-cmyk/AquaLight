package com.aqua.aqualight.data.devices.api.timer

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.legacy.LegacyHttpClient
import com.aqua.aqualight.data.devices.api.model.ApiResult

class LegacyTimerApi(
    private val client: LegacyHttpClient
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
