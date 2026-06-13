package com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.timeline

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.LightProgramTimeMath

object LightProgramTimelineBuilder {
    fun build(draft: LightProgramDraft): LightProgramTimeline {
        val mainStart = LightProgramTimeMath.startMinutes(draft.start)
        val mainPeakStart = LightProgramTimeMath.normalMinutes(draft.peakStart)
        val mainPeakEnd = LightProgramTimeMath.normalMinutes(draft.peakEnd)
        val mainEnd = LightProgramTimeMath.endMinutes(draft.end)

        return LightProgramTimeline(
            phases = listOf(
                LightProgramTimelinePhase(
                    type = LightProgramPhaseType.MAIN_CURVE,
                    label = "Main Program",
                    startMinute = mainStart,
                    endMinute = mainEnd,
                    peakStartMinute = mainPeakStart,
                    peakEndMinute = mainPeakEnd,
                    channelValues = draft.channelValues,
                    transitionMode = draft.transitionMode
                )
            )
        )
    }
}
