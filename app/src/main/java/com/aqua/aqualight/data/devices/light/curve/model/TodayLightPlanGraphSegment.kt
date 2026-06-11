package com.aqua.aqualight.data.devices.light.curve.model

data class TodayLightPlanGraphSegment(
    val id: String,
    val name: String,
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val outputPercent: Int,
    val transitionMode: LightCurveTransitionMode = LightCurveTransitionMode.LINEAR,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false,
    val type: TodayLightPlanGraphSegmentType = TodayLightPlanGraphSegmentType.MAIN_PROGRAM,

    /**
     * Render minutes are intentionally separated from LightCurvePoint.
     * This allows future timeline phases such as Moonlight 18:00 → 06:00
     * to be split into visible-day graph parts like 18:00 → 24:00 and 00:00 → 06:00.
     */
    val startMinute: Int = start.totalMinutes,
    val peakStartMinute: Int = peakStart.totalMinutes,
    val peakEndMinute: Int = peakEnd.totalMinutes,
    val endMinute: Int = if (end.hour == 0 && end.minute == 0) {
        MINUTES_PER_DAY
    } else {
        end.totalMinutes
    }
) {
    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

enum class TodayLightPlanGraphSegmentType {
    MAIN_PROGRAM,
    MOONLIGHT,
    CLOUD_OVERLAY
}