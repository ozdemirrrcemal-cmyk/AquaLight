package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.programs.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramDraftValidator
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramValidationResult

object LightProgramScheduleCompiler {

    fun compile(
        draft: LightProgramDraft,
        programId: String = LOCAL_PREVIEW_PROGRAM_ID,
        programName: String = LOCAL_PREVIEW_PROGRAM_NAME,
        options: LightProgramCompileOptions = LightProgramCompileOptions()
    ): LightProgramCompileResult {
        val safeDraft = draft.copy(
            channelValues = draft.channelValues.normalized(),
            selectedDays = sanitizeRepeatDays(draft.selectedDays)
        )

        when (val validation = LightProgramDraftValidator.validate(safeDraft)) {
            LightProgramValidationResult.Valid -> Unit
            is LightProgramValidationResult.Invalid -> {
                return LightProgramCompileResult.Invalid(validation.message)
            }
        }

        val start = LightProgramTimeMath.startMinutes(safeDraft.start)
        val peakStart = LightProgramTimeMath.normalMinutes(safeDraft.peakStart)
        val peakEnd = LightProgramTimeMath.normalMinutes(safeDraft.peakEnd)
        val end = LightProgramTimeMath.endMinutes(safeDraft.end)
        val channels = safeDraft.channelValues.normalized()

        val red = curveForChannel(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            peak = channels.red,
            draft = safeDraft,
            options = options
        )
        val green = curveForChannel(start, peakStart, peakEnd, end, channels.green, safeDraft, options)
        val blue = curveForChannel(start, peakStart, peakEnd, end, channels.blue, safeDraft, options)
        val white = curveForChannel(start, peakStart, peakEnd, end, channels.white, safeDraft, options)

        val allMinutes = (red.keys + green.keys + blue.keys + white.keys)
            .toSortedSet()

        val points = allMinutes.map { minute ->
            CompiledLightProgramPoint(
                minuteOfDay = minute,
                channels = LightCurveChannelValues(
                    red = red[minute] ?: 0,
                    green = green[minute] ?: 0,
                    blue = blue[minute] ?: 0,
                    white = white[minute] ?: 0
                ).normalized()
            )
        }

        return LightProgramCompileResult.Valid(
            CompiledLightProgramSchedule(
                programId = programId.ifBlank { LOCAL_PREVIEW_PROGRAM_ID },
                programName = programName.ifBlank { LOCAL_PREVIEW_PROGRAM_NAME },
                startMinute = start,
                peakStartMinute = peakStart,
                peakEndMinute = peakEnd,
                endMinute = end,
                peakChannels = channels,
                repeatMode = safeDraft.repeatMode,
                repeatDays = effectiveRepeatDays(
                    repeatMode = safeDraft.repeatMode,
                    selectedDays = safeDraft.selectedDays,
                    legacyEveryDayOnly = options.legacyEveryDayOnly
                ),
                transitionMode = safeDraft.transitionMode,
                points = points
            )
        )
    }

    private fun curveForChannel(
        start: Int,
        peakStart: Int,
        peakEnd: Int,
        end: Int,
        peak: Int,
        draft: LightProgramDraft,
        options: LightProgramCompileOptions
    ): Map<Int, Int> {
        return LightCurveInterpolator.buildCurvePoints(
            startMinute = start,
            peakStartMinute = peakStart,
            peakEndMinute = peakEnd,
            endMinute = end,
            peakPercent = peak,
            transitionMode = draft.transitionMode,
            samplesPerRamp = options.samplesPerRamp
        ).associate { sample ->
            sample.minuteOfDay to sample.percent
        }
    }

    private fun effectiveRepeatDays(
        repeatMode: RepeatMode,
        selectedDays: Set<Int>,
        legacyEveryDayOnly: Boolean
    ): Set<Int> {
        if (legacyEveryDayOnly) {
            return ALL_DAYS
        }

        return when (repeatMode) {
            RepeatMode.EVERY -> ALL_DAYS
            RepeatMode.WEEK -> WEEK_DAYS
            RepeatMode.WEEKEND -> WEEKEND_DAYS
            RepeatMode.CUSTOM -> sanitizeRepeatDays(selectedDays).ifEmpty { ALL_DAYS }
        }
    }

    private fun sanitizeRepeatDays(
        days: Set<Int>
    ): Set<Int> {
        return days
            .filter { day -> day in 1..7 }
            .toSet()
    }

    private val ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
    private val WEEK_DAYS = setOf(1, 2, 3, 4, 5)
    private val WEEKEND_DAYS = setOf(6, 7)
    private const val LOCAL_PREVIEW_PROGRAM_ID = "local-preview"
    private const val LOCAL_PREVIEW_PROGRAM_NAME = "Preview"
}

data class LightProgramCompileOptions(
    val samplesPerRamp: Int = LightCurveInterpolator.DEFAULT_SAMPLES_PER_RAMP,
    val legacyEveryDayOnly: Boolean = true
)

sealed interface LightProgramCompileResult {
    data class Valid(
        val schedule: CompiledLightProgramSchedule
    ) : LightProgramCompileResult

    data class Invalid(
        val message: String
    ) : LightProgramCompileResult
}
