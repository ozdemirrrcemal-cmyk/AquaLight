package com.aqua.aqualight.ui.tabs.devices.detail.light.domain.repository

import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model.LightOverview

interface LightDeviceRepository {

    suspend fun getLightOverview(
        deviceId: Long
    ): LightOverview

    suspend fun setProgramEnabled(
        deviceId: Long,
        programId: String?,
        enabled: Boolean
    )

    suspend fun applyTemporaryMode(
        deviceId: Long,
        sceneKey: String,
        durationMinutes: Int?,
        untilNextEvent: Boolean
    )

    suspend fun restoreAutoProgram(
        deviceId: Long
    )
}