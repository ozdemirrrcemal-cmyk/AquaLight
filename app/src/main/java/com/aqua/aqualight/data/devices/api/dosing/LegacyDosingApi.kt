package com.aqua.aqualight.data.devices.api.dosing

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.legacy.LegacyHttpClient
import com.aqua.aqualight.data.devices.api.model.ApiResult

class LegacyDosingApi(
    private val client: LegacyHttpClient
) : DosingApi {

    override suspend fun readStatus(connection: AquaDeviceConnection): ApiResult<DosingStatus> =
        ApiResult.notConnected()

    override suspend fun readChannels(connection: AquaDeviceConnection): ApiResult<List<DosingChannelStatus>> =
        ApiResult.notConnected()

    override suspend fun readSchedules(connection: AquaDeviceConnection): ApiResult<List<DosingSchedule>> =
        ApiResult.notConnected()

    override suspend fun writeSchedule(
        connection: AquaDeviceConnection,
        schedule: DosingSchedule
    ): ApiResult<Unit> = ApiResult.notConnected()

    override suspend fun runCalibration(
        connection: AquaDeviceConnection,
        channelIndex: Int
    ): ApiResult<Unit> = ApiResult.notConnected()
}
