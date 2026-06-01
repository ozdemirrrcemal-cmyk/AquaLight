package com.aqua.aqualight.ui.tabs.devices.detail.light.data.repository

import com.aqua.aqualight.ui.tabs.devices.detail.light.data.mapper.LightOverviewMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.data.remote.LightDeviceRemoteDataSource
import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model.LightOverview
import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.repository.LightDeviceRepository

class EspLightDeviceRepository(
    private val remoteDataSource: LightDeviceRemoteDataSource,
    private val mapper: LightOverviewMapper
) : LightDeviceRepository {

    override suspend fun getLightOverview(
        deviceId: Long
    ): LightOverview {
        val response = remoteDataSource.getLightOverview(
            deviceId = deviceId
        )

        return mapper.map(
            response = response
        )
    }

    override suspend fun setProgramEnabled(
        deviceId: Long,
        programId: String?,
        enabled: Boolean
    ) {
        remoteDataSource.setProgramEnabled(
            deviceId = deviceId,
            programId = programId,
            enabled = enabled
        )
    }

    override suspend fun applyTemporaryMode(
        deviceId: Long,
        sceneKey: String,
        durationMinutes: Int?,
        untilNextEvent: Boolean
    ) {
        remoteDataSource.applyTemporaryMode(
            deviceId = deviceId,
            sceneKey = sceneKey,
            durationMinutes = durationMinutes,
            untilNextEvent = untilNextEvent
        )
    }

    override suspend fun restoreAutoProgram(
        deviceId: Long
    ) {
        remoteDataSource.restoreAutoProgram(
            deviceId = deviceId
        )
    }
}