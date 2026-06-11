package com.aqua.aqualight.data.devices.light.programs.model

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft

data class SavedLightProgram(
    val id: String,
    val deviceId: Long,
    val name: String,
    val draft: LightProgramDraft,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)