package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelineBuilder

object LightProgramScheduleConflictValidator {

    fun findConflict(
        candidate: SavedLightProgram,
        existingPrograms: List<SavedLightProgram>
    ): SavedLightProgram? {
        return existingPrograms.firstOrNull { existing ->
            existing.id != candidate.id &&
                existing.deviceId == candidate.deviceId &&
                existing.isActive &&
                hasTimeOverlap(
                    first = candidate,
                    second = existing
                )
        }
    }

    private fun hasTimeOverlap(
        first: SavedLightProgram,
        second: SavedLightProgram
    ): Boolean {
        val firstIntervals = buildWeeklyIntervals(first)
        val secondIntervals = buildWeeklyIntervals(second)

        return firstIntervals.any { firstInterval ->
            secondIntervals.any { secondInterval ->
                intervalsOverlap(
                    first = firstInterval,
                    second = secondInterval
                ) ||
                    intervalsOverlap(
                        first = firstInterval,
                        second = secondInterval.shiftedBy(-MINUTES_PER_WEEK)
                    ) ||
                    intervalsOverlap(
                        first = firstInterval,
                        second = secondInterval.shiftedBy(MINUTES_PER_WEEK)
                    )
            }
        }
    }

    private fun buildWeeklyIntervals(
        program: SavedLightProgram
    ): List<ScheduleInterval> {
        val timeline = LightProgramTimelineBuilder.build(
            draft = program.draft
        )

        val days = normalizedSelectedDays(program)

        val intervals = mutableListOf<ScheduleInterval>()

        days.forEach { appDay ->
            val dayStartMinute =
                (appDay - 1) * MINUTES_PER_DAY

            timeline.phases.forEach { phase ->
                val startMinute =
                    dayStartMinute + phase.startMinute

                val endMinute =
                    dayStartMinute + phase.endMinute

                if (endMinute > startMinute) {
                    intervals += ScheduleInterval(
                        startMinute = startMinute,
                        endMinute = endMinute
                    )
                }
            }
        }

        return intervals
    }

    private fun normalizedSelectedDays(
        program: SavedLightProgram
    ): Set<Int> {
        val selectedDays = program.draft.selectedDays
            .filter { day ->
                day in 1..7
            }
            .toSet()

        return selectedDays.ifEmpty {
            setOf(1, 2, 3, 4, 5, 6, 7)
        }
    }

    private fun intervalsOverlap(
        first: ScheduleInterval,
        second: ScheduleInterval
    ): Boolean {
        return first.startMinute < second.endMinute &&
            second.startMinute < first.endMinute
    }

    private data class ScheduleInterval(
        val startMinute: Int,
        val endMinute: Int
    ) {

        fun shiftedBy(
            minutes: Int
        ): ScheduleInterval {
            return copy(
                startMinute = startMinute + minutes,
                endMinute = endMinute + minutes
            )
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
}