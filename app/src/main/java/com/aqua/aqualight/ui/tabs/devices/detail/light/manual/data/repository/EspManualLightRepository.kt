package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.data.repository

import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.data.remote.ManualLightRemoteDataSource
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.model.ManualLightOutput
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.repository.ManualLightRepository

class EspManualLightRepository(
    private val remoteDataSource: ManualLightRemoteDataSource
) : ManualLightRepository {

    override suspend fun getCurrentOutput(
        deviceId: Long
    ): ManualLightOutput? {
        return remoteDataSource.getCurrentOutput(
            deviceId = deviceId
        )
    }

    override suspend fun applyTemporaryOutput(
        deviceId: Long,
        output: ManualLightOutput
    ) {
        remoteDataSource.applyTemporaryOutput(
            deviceId = deviceId,
            output = output
        )
    }

    override suspend fun restoreProgramOutput(
        deviceId: Long
    ) {
        remoteDataSource.restoreProgramOutput(
            deviceId = deviceId
        )
    }
}