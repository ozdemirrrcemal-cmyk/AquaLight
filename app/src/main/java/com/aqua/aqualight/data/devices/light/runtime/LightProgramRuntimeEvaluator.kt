package com.aqua.aqualight.data.devices.light.runtime

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Single evaluator for light schedule runtime.
 *
 * It never uses phone time as a fake device-time fallback. If ESP32 time is not
 * fresh, schedule runtime is unavailable and the UI must show a waiting/offline
 * state instead of pretending that a program is currently running.
 */
object LightProgramRuntimeEvaluator {

    fun evaluate(
        deviceId: Long,
        programs: List<SavedLightProgram>,
        deviceTime: LightDeviceTimeState?
    ): LightProgramRuntimeEvaluation {
        val activePrograms = programs
            .filter { program ->
                program.deviceId == deviceId && program.isActive
            }
            .sortedBy { program ->
                program.draft.start.totalMinutes
            }

        if (deviceTime == null) {
            return LightProgramRuntimeEvaluation(
                hasDeviceTime = false,
                currentMinute = 0,
                deviceWeekDay = null,
                activePrograms = activePrograms,
                todayPrograms = emptyList(),
                runningProgram = null,
                nextProgram = null,
                displayProgram = null
            )
        }

        val currentMinute = deviceTime.curvePoint.totalMinutes

        val todayPrograms = activePrograms
            .filter { program ->
                isScheduledToday(
                    program = program,
                    deviceWeekDay = deviceTime.weekDay
                )
            }
            .sortedBy { program ->
                program.draft.start.totalMinutes
            }

        val runningProgram = todayPrograms.firstOrNull { program ->
            isProgramRunningAt(
                program = program,
                minute = currentMinute
            )
        }

        val nextProgram = todayPrograms.firstOrNull { program ->
            program.draft.start.totalMinutes > currentMinute
        }

        return LightProgramRuntimeEvaluation(
            hasDeviceTime = true,
            currentMinute = currentMinute,
            deviceWeekDay = deviceTime.weekDay,
            activePrograms = activePrograms,
            todayPrograms = todayPrograms,
            runningProgram = runningProgram,
            nextProgram = nextProgram,
            displayProgram = runningProgram
                ?: nextProgram
                ?: todayPrograms.firstOrNull()
        )
    }

    fun isProgramRunningAt(
        program: SavedLightProgram,
        minute: Int
    ): Boolean {
        val start = program.draft.start.totalMinutes
        val end = LightProgramTimeMath.endMinutes(program.draft.end)

        return minute >= start && minute < end
    }

    fun progressPercent(
        program: SavedLightProgram?,
        currentMinute: Int
    ): Int {
        if (program == null) {
            return 0
        }

        val start = program.draft.start.totalMinutes
        val end = LightProgramTimeMath.endMinutes(program.draft.end)

        if (end <= start) {
            return 0
        }

        return when {
            currentMinute <= start -> 0
            currentMinute >= end -> 100
            else -> {
                (((currentMinute - start).toDouble() / (end - start).toDouble()) * 100.0)
                    .roundToInt()
                    .coerceIn(0, 100)
            }
        }
    }

    fun isScheduledToday(
        program: SavedLightProgram,
        deviceWeekDay: Int
    ): Boolean {
        val selectedDays = program.draft.selectedDays

        if (selectedDays.isEmpty()) {
            return true
        }

        return selectedDays.contains(
            appDayFromDeviceWeekDay(deviceWeekDay)
        )
    }

    fun appDayFromDeviceWeekDay(
        weekDay: Int
    ): Int {
        return when (weekDay) {
            in 1..7 -> weekDay
            else -> todayAppDay()
        }
    }

    fun labelForMinute(
        minute: Int
    ): String {
        val normalized = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val hour = normalized / 60
        val min = normalized % 60

        return "%02d:%02d".format(hour, min)
    }

    private fun todayAppDay(): Int {
        val dayOfWeek = Calendar.getInstance()
            .get(Calendar.DAY_OF_WEEK)

        return if (dayOfWeek == Calendar.SUNDAY) {
            7
        } else {
            dayOfWeek - 1
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

data class LightProgramRuntimeEvaluation(
    val hasDeviceTime: Boolean,
    val currentMinute: Int,
    val deviceWeekDay: Int?,
    val activePrograms: List<SavedLightProgram>,
    val todayPrograms: List<SavedLightProgram>,
    val runningProgram: SavedLightProgram?,
    val nextProgram: SavedLightProgram?,
    val displayProgram: SavedLightProgram?
)
