package com.aqua.aqualight.data.devices.light.programs.timeline

data class LightProgramTimeline(
    val phases: List<LightProgramTimelinePhase>
) {

    val mainPhase: LightProgramTimelinePhase?
        get() = phases.firstOrNull { phase ->
            phase.type == LightProgramPhaseType.MAIN_CURVE
        }

    val moonlightPhase: LightProgramTimelinePhase?
        get() = phases.firstOrNull { phase ->
            phase.type == LightProgramPhaseType.MOONLIGHT
        }

    val effectiveStartMinute: Int?
        get() = phases.minOfOrNull { phase ->
            phase.startMinute
        }

    val effectiveEndMinute: Int?
        get() = phases.maxOfOrNull { phase ->
            phase.endMinute
        }

    fun phaseAt(
        minute: Int
    ): LightProgramTimelinePhase? {
        val normalizedMinute = minute.coerceIn(
            0,
            LightProgramTimelinePhase.MINUTES_PER_DAY
        )

        val sameDayPhase = phases.firstOrNull { phase ->
            normalizedMinute >= phase.startMinute &&
                normalizedMinute < phase.endMinute
        }

        if (sameDayPhase != null) {
            return sameDayPhase
        }

        val nextDayMinute =
            normalizedMinute + LightProgramTimelinePhase.MINUTES_PER_DAY

        return phases.firstOrNull { phase ->
            nextDayMinute >= phase.startMinute &&
                nextDayMinute < phase.endMinute
        }
    }
}