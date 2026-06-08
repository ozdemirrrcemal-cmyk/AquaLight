package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightChannel

object LightProgramTimelineBuilder {

    fun build(
        draft: LightProgramDraft
    ): LightProgramTimeline {
        val phases = mutableListOf<LightProgramTimelinePhase>()

        val mainStart = LightProgramTimeMath.startMinutes(
            point = draft.start
        )

        val mainPeakStart = LightProgramTimeMath.normalMinutes(
            point = draft.peakStart
        )

        val mainPeakEnd = LightProgramTimeMath.normalMinutes(
            point = draft.peakEnd
        )

        val mainEnd = LightProgramTimeMath.endMinutes(
            point = draft.end
        )

        phases += LightProgramTimelinePhase(
            type = LightProgramPhaseType.MAIN_CURVE,
            label = "Main Program",
            startMinute = mainStart,
            endMinute = mainEnd,
            peakStartMinute = mainPeakStart,
            peakEndMinute = mainPeakEnd,
            channelValues = draft.channelValues,
            transitionMode = draft.transitionMode
        )

        val moonlight = draft.moonlightSettings

        if (moonlight.enabled) {
            val moonlightStart = if (moonlight.followProgramEnd) {
                mainEnd
            } else {
                normalizeMoonlightStart(
                    startMinute = moonlight.startTime.totalMinutes,
                    mainEndMinute = mainEnd
                )
            }

            val moonlightEnd = normalizeMoonlightEnd(
                startMinute = moonlightStart,
                endMinute = moonlight.endTime.totalMinutes
            )

            phases += LightProgramTimelinePhase(
                type = LightProgramPhaseType.MOONLIGHT,
                label = "Moonlight",
                startMinute = moonlightStart,
                endMinute = moonlightEnd,
                channelValues = moonlightChannelValues(
                    channel = moonlight.channel,
                    intensityPercent = moonlight.intensityPercent
                ),
                transitionMode = draft.transitionMode
            )
        }

        return LightProgramTimeline(
            phases = phases.sortedBy { phase ->
                phase.startMinute
            }
        )
    }

    private fun normalizeMoonlightStart(
        startMinute: Int,
        mainEndMinute: Int
    ): Int {
        return if (startMinute < mainEndMinute) {
            startMinute + LightProgramTimelinePhase.MINUTES_PER_DAY
        } else {
            startMinute
        }
    }

    private fun normalizeMoonlightEnd(
        startMinute: Int,
        endMinute: Int
    ): Int {
        var normalizedEnd = endMinute

        while (normalizedEnd <= startMinute) {
            normalizedEnd += LightProgramTimelinePhase.MINUTES_PER_DAY
        }

        return normalizedEnd
    }

    private fun moonlightChannelValues(
        channel: MoonlightChannel,
        intensityPercent: Int
    ): LightCurveChannelValues {
        val safeIntensity = intensityPercent.coerceIn(1, 15)

        val softWhite = (safeIntensity / 2)
            .coerceAtLeast(1)

        return when (channel) {
            MoonlightChannel.BLUE -> {
                LightCurveChannelValues(
                    red = 0,
                    green = 0,
                    blue = safeIntensity,
                    white = 0
                )
            }

            MoonlightChannel.WHITE -> {
                LightCurveChannelValues(
                    red = 0,
                    green = 0,
                    blue = 0,
                    white = safeIntensity
                )
            }

            MoonlightChannel.BLUE_WHITE -> {
                LightCurveChannelValues(
                    red = 0,
                    green = 0,
                    blue = safeIntensity,
                    white = softWhite
                )
            }
        }
    }
}