package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote

interface LightProgramEditorRemoteDataSource {

    suspend fun getProgram(
        deviceId: Long,
        programId: String
    ): LightProgramEditorRemoteResponse

    suspend fun saveProgram(
        deviceId: Long,
        request: LightProgramEditorSaveRequest
    )
}