package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.repository

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.mapper.LightProgramEditorMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote.LightProgramEditorRemoteDataSource
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.repository.LightProgramEditorRepository

class EspLightProgramEditorRepository(
    private val remoteDataSource: LightProgramEditorRemoteDataSource,
    private val mapper: LightProgramEditorMapper
) : LightProgramEditorRepository {

    override suspend fun getProgramEditorDraft(
        deviceId: Long,
        programId: String
    ): LightProgramEditorDraft {
        val response = remoteDataSource.getProgram(
            deviceId = deviceId,
            programId = programId
        )

        return mapper.fromRemote(
            response = response
        )
    }

    override suspend fun saveProgramEditorDraft(
        deviceId: Long,
        draft: LightProgramEditorDraft
    ) {
        remoteDataSource.saveProgram(
            deviceId = deviceId,
            request = mapper.toSaveRequest(
                draft = draft
            )
        )
    }
}