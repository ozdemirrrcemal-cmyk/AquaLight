package com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model

data class TodayLightPlanGraphState(
    val currentTime: LightCurvePoint,
    val segments: List<TodayLightPlanGraphSegment> = emptyList(),
    val showCurrentTimeMarker: Boolean = true,
    val emptyMessage: String? = null,
    val showPausedOverlay: Boolean = false,
    val pausedOverlayTitle: String = "Auto paused",
    val pausedOverlaySubtitle: String = "Manual scene is active"
) {
    companion object {
        fun empty(
            currentTime: LightCurvePoint,
            emptyMessage: String? = null,
            showCurrentTimeMarker: Boolean = false
        ): TodayLightPlanGraphState {
            return TodayLightPlanGraphState(
                currentTime = currentTime,
                segments = emptyList(),
                showCurrentTimeMarker = showCurrentTimeMarker,
                emptyMessage = emptyMessage,
                showPausedOverlay = false
            )
        }
    }
}