package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.repository

import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.model.ManualLightOutput

interface ManualLightRepository {

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