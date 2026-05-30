package com.aqua.aqualight.data.devices.light

import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot
import kotlinx.coroutines.flow.StateFlow

interface LightOverviewRepository {

    fun observeOverview(
        deviceId: Long
    ): StateFlow<LightOverviewSnapshot>

    fun refresh(
        deviceId: Long
    )

    fun setProgramEnabled(
        deviceId: Long,
        enabled: Boolean
    )

    fun applyTemporaryScene(
        deviceId: Long,
        sceneName: String,
        outputPercent: Int,
        durationLabel: String,
        resumeLabel: String
    )

    fun restoreAutoProgram(
        deviceId: Long
    )
}