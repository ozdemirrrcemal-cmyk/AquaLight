package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline

import com.aqua.aqualight.data.devices.light.programs.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphSegment
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphSegmentType
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphState

object LightDashboardTimelineMapper {

    fun noData(
        statusText: String,
        nextEventText: String,
        emptyMessage: String = statusText
    ): LightDashboardTimelineRenderResult {
        return map(
            snapshot = LightDashboardTimelineSnapshot(
                currentTimeMinute = null,
                statusText = statusText,
                nextEventText = nextEventText,
                emptyMessage = emptyMessage
            )
        )
    }

    fun noActivePlan(
        currentTimeMinute: Int?,
        statusText: String,
        nextEventText: String,
        emptyMessage: String
    ): LightDashboardTimelineRenderResult {
        return map(
            snapshot = LightDashboardTimelineSnapshot(
                currentTimeMinute = currentTimeMinute,
                statusText = statusText,
                nextEventText = nextEventText,
                emptyMessage = emptyMessage
            )
        )
    }

    fun activeAuto(
        currentTimeMinute: Int?,
        mainSegments: List<LightDashboardTimelineSegment>,
        moonlightSegments: List<LightDashboardTimelineSegment> = emptyList(),
        cloudOverlays: List<LightDashboardTimelineSegment> = emptyList(),
        statusText: String,
        nextEventText: String,
        emptyMessage: String? = null
    ): LightDashboardTimelineRenderResult {
        return map(
            snapshot = LightDashboardTimelineSnapshot(
                currentTimeMinute = currentTimeMinute,
                mainSegments = mainSegments,
                moonlightSegments = moonlightSegments,
                cloudOverlays = cloudOverlays,
                statusText = statusText,
                nextEventText = nextEventText,
                emptyMessage = emptyMessage
            )
        )
    }

    fun moonlightActive(
        currentTimeMinute: Int?,
        mainSegments: List<LightDashboardTimelineSegment>,
        moonlightSegments: List<LightDashboardTimelineSegment>,
        cloudOverlays: List<LightDashboardTimelineSegment> = emptyList(),
        statusText: String,
        nextEventText: String,
        emptyMessage: String? = null
    ): LightDashboardTimelineRenderResult {
        return activeAuto(
            currentTimeMinute = currentTimeMinute,
            mainSegments = mainSegments,
            moonlightSegments = moonlightSegments,
            cloudOverlays = cloudOverlays,
            statusText = statusText,
            nextEventText = nextEventText,
            emptyMessage = emptyMessage
        )
    }

    fun manualOverride(
        currentTimeMinute: Int?,
        mainSegments: List<LightDashboardTimelineSegment>,
        moonlightSegments: List<LightDashboardTimelineSegment> = emptyList(),
        cloudOverlays: List<LightDashboardTimelineSegment> = emptyList(),
        statusText: String,
        nextEventText: String,
        title: String = "Manual override active",
        subtitle: String = "Resume Auto to follow schedule",
        emptyMessage: String? = null
    ): LightDashboardTimelineRenderResult {
        return map(
            snapshot = LightDashboardTimelineSnapshot(
                currentTimeMinute = currentTimeMinute,
                mainSegments = mainSegments,
                moonlightSegments = moonlightSegments,
                cloudOverlays = cloudOverlays,
                override = LightDashboardTimelineOverride(
                    title = title,
                    subtitle = subtitle
                ),
                statusText = statusText,
                nextEventText = nextEventText,
                emptyMessage = emptyMessage
            )
        )
    }

    fun sceneOverride(
        currentTimeMinute: Int?,
        sceneName: String?,
        mainSegments: List<LightDashboardTimelineSegment>,
        moonlightSegments: List<LightDashboardTimelineSegment> = emptyList(),
        cloudOverlays: List<LightDashboardTimelineSegment> = emptyList(),
        statusText: String,
        nextEventText: String,
        emptyMessage: String? = null
    ): LightDashboardTimelineRenderResult {
        val safeSceneName = sceneName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return map(
            snapshot = LightDashboardTimelineSnapshot(
                currentTimeMinute = currentTimeMinute,
                mainSegments = mainSegments,
                moonlightSegments = moonlightSegments,
                cloudOverlays = cloudOverlays,
                override = LightDashboardTimelineOverride(
                    title = if (safeSceneName == null) {
                        "Scene active"
                    } else {
                        "Scene active · $safeSceneName"
                    },
                    subtitle = "Resume Auto to follow schedule"
                ),
                statusText = statusText,
                nextEventText = nextEventText,
                emptyMessage = emptyMessage
            )
        )
    }

    fun map(
        snapshot: LightDashboardTimelineSnapshot
    ): LightDashboardTimelineRenderResult {
        val segments = buildGraphSegments(snapshot)
        val currentMinute = snapshot.currentTimeMinute
            ?.coerceIn(0, MINUTES_PER_DAY)

        return LightDashboardTimelineRenderResult(
            statusText = snapshot.statusText,
            nextEventText = snapshot.nextEventText,
            graphState = TodayLightPlanGraphState(
                currentTime = pointForMinute(currentMinute ?: 0),
                segments = segments,
                showCurrentTimeMarker = currentMinute != null && segments.isNotEmpty(),
                emptyMessage = snapshot.emptyMessage,
                showPausedOverlay = snapshot.override != null,
                pausedOverlayTitle = snapshot.override?.title ?: "Auto paused",
                pausedOverlaySubtitle = snapshot.override?.subtitle ?: "Resume Auto to follow schedule"
            )
        )
    }

    private fun buildGraphSegments(
        snapshot: LightDashboardTimelineSnapshot
    ): List<TodayLightPlanGraphSegment> {
        val currentMinute = snapshot.currentTimeMinute
            ?.coerceIn(0, MINUTES_PER_DAY)

        val rawSegments = buildList {
            addAll(
                snapshot.mainSegments.flatMap { segment ->
                    segment.toVisibleGraphSegments(
                        type = TodayLightPlanGraphSegmentType.MAIN_PROGRAM
                    )
                }
            )
            addAll(
                snapshot.moonlightSegments.flatMap { segment ->
                    segment.toVisibleGraphSegments(
                        type = TodayLightPlanGraphSegmentType.MOONLIGHT
                    )
                }
            )
            addAll(
                snapshot.cloudOverlays.flatMap { segment ->
                    segment.toVisibleGraphSegments(
                        type = TodayLightPlanGraphSegmentType.CLOUD_OVERLAY
                    )
                }
            )
        }.sortedWith(
            compareBy<TodayLightPlanGraphSegment> {
                it.startMinute
            }.thenBy {
                it.type.ordinal
            }
        )

        if (rawSegments.isEmpty()) {
            return rawSegments
        }

        val currentId = currentMinute?.let { minute ->
            rawSegments.firstOrNull { segment ->
                minute >= segment.startMinute && minute < segment.endMinute
            }?.id
        }

        val nextId = currentMinute?.let { minute ->
            rawSegments.firstOrNull { segment ->
                segment.startMinute > minute
            }?.id ?: rawSegments.firstOrNull()?.id
        }

        return rawSegments.map { segment ->
            segment.copy(
                isCurrent = segment.id == currentId,
                isNext = segment.id != currentId && segment.id == nextId
            )
        }
    }

    private fun LightDashboardTimelineSegment.toVisibleGraphSegments(
        type: TodayLightPlanGraphSegmentType
    ): List<TodayLightPlanGraphSegment> {
        val safeStart = normalizeMinute(startMinute)
        val safeEnd = normalizeMinute(endMinute)

        return when {
            safeStart == safeEnd -> emptyList()
            safeEnd > safeStart -> listOf(
                toGraphSegment(
                    type = type,
                    suffix = "",
                    visibleStartMinute = safeStart,
                    visibleEndMinute = safeEnd
                )
            )
            else -> listOf(
                toGraphSegment(
                    type = type,
                    suffix = "-late",
                    visibleStartMinute = safeStart,
                    visibleEndMinute = MINUTES_PER_DAY
                ),
                toGraphSegment(
                    type = type,
                    suffix = "-early",
                    visibleStartMinute = 0,
                    visibleEndMinute = safeEnd
                )
            )
        }
    }

    private fun LightDashboardTimelineSegment.toGraphSegment(
        type: TodayLightPlanGraphSegmentType,
        suffix: String,
        visibleStartMinute: Int,
        visibleEndMinute: Int
    ): TodayLightPlanGraphSegment {
        val boundedPeakStart = peakStartMinute.coerceIn(visibleStartMinute, visibleEndMinute)
        val boundedPeakEnd = peakEndMinute.coerceIn(boundedPeakStart, visibleEndMinute)

        return TodayLightPlanGraphSegment(
            id = "$id$suffix",
            name = name,
            start = pointForMinute(visibleStartMinute),
            peakStart = pointForMinute(boundedPeakStart),
            peakEnd = pointForMinute(boundedPeakEnd),
            end = pointForMinute(visibleEndMinute),
            outputPercent = outputPercent.coerceIn(0, 100),
            transitionMode = transitionMode,
            type = type,
            startMinute = visibleStartMinute,
            peakStartMinute = boundedPeakStart,
            peakEndMinute = boundedPeakEnd,
            endMinute = visibleEndMinute
        )
    }

    private fun normalizeMinute(
        minute: Int
    ): Int {
        return when {
            minute == MINUTES_PER_DAY -> MINUTES_PER_DAY
            else -> ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        }
    }

    private fun pointForMinute(
        minute: Int
    ): LightCurvePoint {
        val safeMinute = minute.coerceIn(0, MINUTES_PER_DAY)
        return LightCurvePoint.of(
            hour = safeMinute / 60,
            minute = safeMinute % 60
        )
    }

    private const val MINUTES_PER_DAY = TodayLightPlanGraphSegment.MINUTES_PER_DAY
}

data class LightDashboardTimelineRenderResult(
    val statusText: String,
    val nextEventText: String,
    val graphState: TodayLightPlanGraphState
)
