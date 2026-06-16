package com.aqua.aqualight.data.devices.light.curve.model

data class TodayLightPlanGraphSegment(
    val id: String,
    val name: String,
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val outputPercent: Int,

    /**
     * Optional controller/runtime LP points already received from the device.
     * Dashboard graph rendering uses these points directly instead of rebuilding
     * a local editor curve. This keeps Linear/Smooth/Natural programs visually
     * identical to what is stored on the controller.
     */
    val runtimePoints: List<TodayLightPlanGraphRuntimePoint> = emptyList(),
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

data class TodayLightPlanGraphRuntimePoint(
    val minute: Int,
    val percent: Int
)

enum class TodayLightPlanGraphSegmentType {
    MAIN_PROGRAM,
    MOONLIGHT,
    CLOUD_OVERLAY
}