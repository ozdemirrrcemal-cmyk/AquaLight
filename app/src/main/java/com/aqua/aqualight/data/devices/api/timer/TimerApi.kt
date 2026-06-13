package com.aqua.aqualight.data.devices.api.timer

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface TimerApi {

    suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<TimerStatus>

    suspend fun readSchedules(
        connection: AquaDeviceConnection
    ): ApiResult<List<TimerSchedule>>

    suspend fun writeSchedule(
        connection: AquaDeviceConnection,
        schedule: TimerSchedule
    ): ApiResult<Unit>
}
