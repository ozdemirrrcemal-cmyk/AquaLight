package com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.LightProgramDraft

data class SavedLightProgram(
    val id: String,
    val ownerUid: String = "",
    val deviceId: Long,
    val name: String,
    val draft: LightProgramDraft,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)