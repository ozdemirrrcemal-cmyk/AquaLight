package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode

/**
 * UI-safe timeline contract for the dashboard graph.
 *
 * The dashboard graph must be fed by the controller runtime/schedule data,
 * not by editor drafts or local DataStore programs. Device/firmware layers
 * can map their Time + schedule + automation state into this small contract
 * without the graph knowing whether the source is legacy /get or the future API.
 */
data class LightDashboardTimelineSnapshot(
    val currentTimeMinute: Int? = null,
    val mainSegments: List<LightDashboardTimelineSegment> = emptyList(),
    val moonlightSegments: List<LightDashboardTimelineSegment> = emptyList(),
    val cloudOverlays: List<LightDashboardTimelineSegment> = emptyList(),
    val override: LightDashboardTimelineOverride? = null,
    val statusText: String,
    val nextEventText: String,
    val emptyMessage: String? = null
)

data class LightDashboardTimelineSegment(
    val id: String,
    val name: String,
    val startMinute: Int,
    val peakStartMinute: Int = startMinute,
    val peakEndMinute: Int = peakStartMinute,
    val endMinute: Int,
    val outputPercent: Int,
    val transitionMode: LightCurveTransitionMode = LightCurveTransitionMode.LINEAR
)

data class LightDashboardTimelineOverride(
    val title: String,
    val subtitle: String
)
