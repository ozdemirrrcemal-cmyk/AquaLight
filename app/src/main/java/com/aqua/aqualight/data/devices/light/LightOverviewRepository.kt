package com.aqua.aqualight.data.devices.light

import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot
import kotlinx.coroutines.flow.Flow

interface LightOverviewRepository {

    fun observeOverview(
        deviceId: Long
    ): Flow<LightOverviewSnapshot>

    suspend fun refresh(
        deviceId: Long
    )

    suspend fun setProgramEnabled(
        deviceId: Long,
        enabled: Boolean
    )

    suspend fun applyTemporaryScene(
        deviceId: Long,
        sceneName: String,
        outputPercent: Int,
        durationLabel: String,
        resumeLabel: String
    )

    suspend fun restoreAutoProgram(
        deviceId: Long
    )
}