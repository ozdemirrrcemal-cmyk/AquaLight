package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.repository

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorDraft

interface LightProgramEditorRepository {

    suspend fun getProgramEditorDraft(
        deviceId: Long,
        programId: String
    ): LightProgramEditorDraft

    suspend fun saveProgramEditorDraft(
        deviceId: Long,
        draft: LightProgramEditorDraft
    )
}