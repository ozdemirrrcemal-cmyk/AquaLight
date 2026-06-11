package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft

data class SavedLightProgram(
    val id: String,
    val deviceId: Long,
    val name: String,
    val draft: LightProgramDraft,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)