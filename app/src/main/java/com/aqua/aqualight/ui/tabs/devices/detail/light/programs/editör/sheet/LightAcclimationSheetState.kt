package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

data class LightAcclimationSheetState(
    val enabled: Boolean = false,
    val durationDays: Int = DEFAULT_DURATION_DAYS,
    val startIntensityPercent: Int = DEFAULT_START_INTENSITY_PERCENT
) {
    companion object {
        const val DEFAULT_DURATION_DAYS = 7
        const val DEFAULT_START_INTENSITY_PERCENT = 40
        const val MIN_START_INTENSITY_PERCENT = 20
        const val MAX_START_INTENSITY_PERCENT = 80
    }
}