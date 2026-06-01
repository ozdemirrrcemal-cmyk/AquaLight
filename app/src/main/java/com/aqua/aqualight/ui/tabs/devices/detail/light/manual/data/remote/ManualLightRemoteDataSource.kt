package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.data.remote

import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.model.ManualLightOutput

interface ManualLightRemoteDataSource {

    suspend fun getCurrentOutput(
        deviceId: Long
    ): ManualLightOutput?

    suspend fun applyTemporaryOutput(
        deviceId: Long,
        output: ManualLightOutput
    )

    suspend fun restoreProgramOutput(
        deviceId: Long
    )
}