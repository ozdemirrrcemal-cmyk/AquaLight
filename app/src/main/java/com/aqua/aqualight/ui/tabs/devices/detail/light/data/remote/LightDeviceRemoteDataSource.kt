package com.aqua.aqualight.ui.tabs.devices.detail.light.data.remote

interface LightDeviceRemoteDataSource {

    suspend fun getLightOverview(
        deviceId: Long
    ): LightOverviewRemoteResponse

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