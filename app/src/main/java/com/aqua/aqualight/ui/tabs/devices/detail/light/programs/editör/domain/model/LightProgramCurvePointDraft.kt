package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model

data class LightProgramCurvePointDraft(
    val id: String,
    val role: LightProgramPointRole,
    val label: String,
    val minuteOfDay: Int,
    val intensityPercent: Int,
    val canRename: Boolean,
    val canDelete: Boolean
)