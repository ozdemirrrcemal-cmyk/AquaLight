package com.aqua.aqualight.data.devices.api.dosing

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiResult

interface DosingApi {

    suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<DosingStatus>

    suspend fun readChannels(
        connection: AquaDeviceConnection
    ): ApiResult<List<DosingChannelStatus>>

    suspend fun readSchedules(
        connection: AquaDeviceConnection
    ): ApiResult<List<DosingSchedule>>

    suspend fun writeSchedule(
        connection: AquaDeviceConnection,
        schedule: DosingSchedule
    ): ApiResult<Unit>

    suspend fun runCalibration(
        connection: AquaDeviceConnection,
        channelIndex: Int
    ): ApiResult<Unit>
}
