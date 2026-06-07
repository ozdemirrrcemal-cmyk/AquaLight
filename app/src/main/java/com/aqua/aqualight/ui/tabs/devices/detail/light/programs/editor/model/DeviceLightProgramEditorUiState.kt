package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveMoonlightGraphSegment
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramPhaseType
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelineBuilder
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelinePhase

data class DeviceLightProgramEditorUiState(
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val channelValues: LightCurveChannelValues,
    val repeatMode: RepeatMode,
    val selectedDays: Set<Int>,
    val moonlightSettings: MoonlightSettings,
    val cloudSimulationSettings: CloudSimulationSettings,
    val transitionMode: LightCurveTransitionMode,
    val previewSpeed: PreviewSpeed,
    val currentDeviceTime: LightCurvePoint,
    val previewSimulationTime: LightCurvePoint? = null,
val isPreviewRunning: Boolean = false,
val previewProgressPercent: Int = 0
) {
    val graphState: LightCurveGraphState
    get() {
        val timeline = LightProgramTimelineBuilder.build(draft)

        return LightCurveGraphState(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues,
            currentTime = previewSimulationTime ?: currentDeviceTime,
            transitionMode = transitionMode,
            moonlightSegments = buildMoonlightGraphSegments(timeline)
        )
    }
	
	private fun buildMoonlightGraphSegments(
    timeline: com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimeline
): List<LightCurveMoonlightGraphSegment> {
    val moonlightPhase = timeline.phases.firstOrNull { phase ->
        phase.type == LightProgramPhaseType.MOONLIGHT
    } ?: return emptyList()

    return splitPhaseIntoVisibleDaySegments(
        phase = moonlightPhase
    )
}

private fun splitPhaseIntoVisibleDaySegments(
    phase: LightProgramTimelinePhase
): List<LightCurveMoonlightGraphSegment> {
    val result = mutableListOf<LightCurveMoonlightGraphSegment>()

    val day = LightCurveMoonlightGraphSegment.MINUTES_PER_DAY

    val firstStart = phase.startMinute.coerceIn(0, day)
    val firstEnd = phase.endMinute.coerceIn(0, day)

    if (firstEnd > firstStart) {
        result += LightCurveMoonlightGraphSegment(
            startMinute = firstStart,
            endMinute = firstEnd,
            outputPercent = phase.outputPercent,
            label = phase.label
        )
    }

    if (phase.endMinute > day) {
        val secondEnd = (phase.endMinute - day).coerceIn(0, day)

        if (secondEnd > 0) {
            result += LightCurveMoonlightGraphSegment(
                startMinute = 0,
                endMinute = secondEnd,
                outputPercent = phase.outputPercent,
                label = phase.label
            )
        }
    }

    return result
}

    val draft: LightProgramDraft
        get() = LightProgramDraft(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues,
            repeatMode = repeatMode,
            selectedDays = selectedDays,
            moonlightSettings = moonlightSettings,
            cloudSimulationSettings = cloudSimulationSettings,
            transitionMode = transitionMode
        )

    companion object {
        fun default(): DeviceLightProgramEditorUiState {
            val draft = LightProgramDraft.default()

            return DeviceLightProgramEditorUiState(
                start = draft.start,
                peakStart = draft.peakStart,
                peakEnd = draft.peakEnd,
                end = draft.end,
                channelValues = draft.channelValues,
                repeatMode = draft.repeatMode,
                selectedDays = draft.selectedDays,
                moonlightSettings = draft.moonlightSettings,
                cloudSimulationSettings = draft.cloudSimulationSettings,
                transitionMode = draft.transitionMode,
                previewSpeed = PreviewSpeed.ONE_MINUTE,
                currentDeviceTime = LightCurvePoint.of(0, 0)
            )
        }
    }
}