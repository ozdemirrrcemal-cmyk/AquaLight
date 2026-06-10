package com.aqua.aqualight.data.devices.light.curve.model

data class TodayLightPlanGraphState(
    val currentTime: LightCurvePoint,
    val segments: List<TodayLightPlanGraphSegment> = emptyList(),
    val showPausedOverlay: Boolean = false,
    val pausedOverlayTitle: String = "Auto paused",
    val pausedOverlaySubtitle: String = "Manual scene is active"
) {
    companion object {
        fun empty(
            currentTime: LightCurvePoint
        ): TodayLightPlanGraphState {
            return TodayLightPlanGraphState(
                currentTime = currentTime,
                segments = emptyList(),
                showPausedOverlay = false
            )
        }
    }
}