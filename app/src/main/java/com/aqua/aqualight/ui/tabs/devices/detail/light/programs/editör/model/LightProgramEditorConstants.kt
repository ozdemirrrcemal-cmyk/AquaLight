package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

object LightProgramEditorConstants {
    const val DEFAULT_PROGRAM_NAME = "Every Day Program"

    const val POINT_STEP_MINUTES = 15
    const val MINUTES_IN_DAY = 24 * 60
    const val MAX_PERCENT = 100

    const val ACCLIMATION_MIN_START_PERCENT = 20
    const val ACCLIMATION_MAX_START_PERCENT = 80

    const val REQUIRED_DEFAULT_POINT_COUNT = 4

    const val POINT_ID_START = "start"
    const val POINT_ID_PEAK_START = "peak_start"
    const val POINT_ID_PEAK_END = "peak_end"
    const val POINT_ID_END = "end"

    const val EXTRA_CURVE_POINT_ROW_TAG = "extra_curve_point_row"

    const val PREVIEW_DURATION_MS = 60_000L
    const val PREVIEW_TICK_MS = 250L
    const val PREVIEW_PROGRESS_MAX = 1000

    const val DAY_MON = 1
    const val DAY_TUE = 2
    const val DAY_WED = 3
    const val DAY_THU = 4
    const val DAY_FRI = 5
    const val DAY_SAT = 6
    const val DAY_SUN = 7
}