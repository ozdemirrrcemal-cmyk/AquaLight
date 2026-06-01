package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model

data class LightProgramAcclimationDraft(
    val enabled: Boolean = false,
    val durationDays: Int? = null,
    val startIntensityPercent: Int? = null
)