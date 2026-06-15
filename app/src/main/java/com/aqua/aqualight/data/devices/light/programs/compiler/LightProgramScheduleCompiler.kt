package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

object LightProgramScheduleCompiler {

    const val SAMPLES_PER_RAMP = 12

    fun compile(
        program: SavedLightProgram
    ): LightProgramCompileResult {
        return compile(
            programId = program.id,
            programName = program.name,
            startMinute = program.startMinute,
            peakStartMinute = program.peakStartMinute,
            peakEndMinute = program.peakEndMinute,
            endMinute = program.endMinute,
            channels = LightChannelValues(
                red = program.red,
                green = program.green,
                blue = program.blue,
                white = program.white
            ),
            transitionMode = program.transitionMode
        )
    }

    fun compileDraft(
        programId: String,
        programName: String,
        draft: LightProgramDraft
    ): LightProgramCompileResult {
        val normalizedDraft = draft.normalizedForCurrentFirmware()

        return compile(
            programId = programId,
            programName = programName,
            startMinute = normalizedDraft.startMinute,
            peakStartMinute = normalizedDraft.peakStartMinute,
            peakEndMinute = normalizedDraft.peakEndMinute,
            endMinute = normalizedDraft.endMinute,
            channels = LightChannelValues(
                red = normalizedDraft.red,
                green = normalizedDraft.green,
                blue = normalizedDraft.blue,
                white = normalizedDraft.white
            ),
            transitionMode = normalizedDraft.transitionMode
        )
    }

    private fun compile(
        programId: String,
        programName: String,
        startMinute: Int,
        peakStartMinute: Int,
        peakEndMinute: Int,
        endMinute: Int,
        channels: LightChannelValues,
        transitionMode: LightProgramTransitionMode
    ): LightProgramCompileResult {
        val safeStart = startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        val safePeakStart = peakStartMinute.coerceIn(safeStart + 1, MINUTES_PER_DAY - 1)
        val safePeakEnd = peakEndMinute.coerceIn(safePeakStart + 1, MINUTES_PER_DAY - 1)
        val safeEnd = endMinute.coerceIn(safePeakEnd + 1, MINUTES_PER_DAY)

        if (!(safeStart < safePeakStart && safePeakStart < safePeakEnd && safePeakEnd < safeEnd)) {
            return LightProgramCompileResult.Invalid("Program times must be in order.")
        }

        val normalized = channels.normalized()
        if (listOf(normalized.red, normalized.green, normalized.blue, normalized.white).all { value -> value == 0 }) {
            return LightProgramCompileResult.Invalid("At least one channel must be above 0%.")
        }

        val minutes = buildSampleMinutes(
            startMinute = safeStart,
            peakStartMinute = safePeakStart,
            peakEndMinute = safePeakEnd,
            endMinute = safeEnd,
            transitionMode = transitionMode
        )

        val redCurve = buildChannelCurve(
            startMinute = safeStart,
            peakStartMinute = safePeakStart,
            peakEndMinute = safePeakEnd,
            endMinute = safeEnd,
            peakPercent = normalized.red,
            transitionMode = transitionMode
        )

        val greenCurve = buildChannelCurve(
            startMinute = safeStart,
            peakStartMinute = safePeakStart,
            peakEndMinute = safePeakEnd,
            endMinute = safeEnd,
            peakPercent = normalized.green,
            transitionMode = transitionMode
        )

        val blueCurve = buildChannelCurve(
            startMinute = safeStart,
            peakStartMinute = safePeakStart,
            peakEndMinute = safePeakEnd,
            endMinute = safeEnd,
            peakPercent = normalized.blue,
            transitionMode = transitionMode
        )

        val whiteCurve = buildChannelCurve(
            startMinute = safeStart,
            peakStartMinute = safePeakStart,
            peakEndMinute = safePeakEnd,
            endMinute = safeEnd,
            peakPercent = normalized.white,
            transitionMode = transitionMode
        )

        val points = minutes.map { minute ->
            CompiledLightProgramPoint(
                minuteOfDay = minute,
                red = redCurve.valueAt(minute),
                green = greenCurve.valueAt(minute),
                blue = blueCurve.valueAt(minute),
                white = whiteCurve.valueAt(minute)
            )
        }.distinctBy { point ->
            point.minuteOfDay
        }.sortedBy { point ->
            point.minuteOfDay
        }

        return LightProgramCompileResult.Success(
            schedule = CompiledLightProgramSchedule(
                programId = programId,
                programName = programName,
                transitionMode = transitionMode,
                points = points,
                hash = points.toStableHash(programId, programName, transitionMode)
            )
        )
    }

    private fun buildSampleMinutes(
        startMinute: Int,
        peakStartMinute: Int,
        peakEndMinute: Int,
        endMinute: Int,
        transitionMode: LightProgramTransitionMode
    ): List<Int> {
        return buildCurvePoints(
            startMinute = startMinute,
            peakStartMinute = peakStartMinute,
            peakEndMinute = peakEndMinute,
            endMinute = endMinute,
            peakPercent = 100,
            transitionMode = transitionMode,
            samplesPerRamp = SAMPLES_PER_RAMP
        ).map { point ->
            point.minute.roundToInt()
        }.map { minute ->
            if (minute >= MINUTES_PER_DAY) {
                MINUTES_PER_DAY - 1
            } else {
                minute.coerceIn(0, MINUTES_PER_DAY - 1)
            }
        }.toMutableSet().apply {
            add(0)
            add(startMinute.coerceIn(0, MINUTES_PER_DAY - 1))
            add(peakStartMinute.coerceIn(0, MINUTES_PER_DAY - 1))
            add(peakEndMinute.coerceIn(0, MINUTES_PER_DAY - 1))
            add((endMinute.coerceAtMost(MINUTES_PER_DAY) - 1).coerceAtLeast(0))
        }.sorted()
    }

    private fun buildChannelCurve(
        startMinute: Int,
        peakStartMinute: Int,
        peakEndMinute: Int,
        endMinute: Int,
        peakPercent: Int,
        transitionMode: LightProgramTransitionMode
    ): List<CurvePoint> {
        return buildCurvePoints(
            startMinute = startMinute,
            peakStartMinute = peakStartMinute,
            peakEndMinute = peakEndMinute,
            endMinute = endMinute,
            peakPercent = peakPercent.coerceIn(0, 100),
            transitionMode = transitionMode,
            samplesPerRamp = SAMPLES_PER_RAMP
        ).map { point ->
            CurvePoint(
                minute = point.minute.roundToInt(),
                value = point.percent.roundToInt().coerceIn(0, 100)
            )
        }.map { point ->
            if (point.minute >= MINUTES_PER_DAY) {
                point.copy(minute = MINUTES_PER_DAY - 1)
            } else {
                point.copy(minute = point.minute.coerceIn(0, MINUTES_PER_DAY - 1))
            }
        }.distinctBy { point ->
            point.minute
        }.sortedBy { point ->
            point.minute
        }
    }

    private fun buildCurvePoints(
        startMinute: Int,
        peakStartMinute: Int,
        peakEndMinute: Int,
        endMinute: Int,
        peakPercent: Int,
        transitionMode: LightProgramTransitionMode,
        samplesPerRamp: Int = SAMPLES_PER_RAMP
    ): List<FloatCurvePoint> {
        val safePeak = peakPercent.coerceIn(0, 100)
        val start = startMinute.coerceIn(0, MINUTES_PER_DAY)
        val peakStart = peakStartMinute.coerceIn(start, MINUTES_PER_DAY)
        val peakEnd = peakEndMinute.coerceIn(peakStart, MINUTES_PER_DAY)
        val end = endMinute.coerceIn(peakEnd, MINUTES_PER_DAY)

        val points = mutableListOf<FloatCurvePoint>()
        points.add(FloatCurvePoint(0f, 0f))
        points.add(FloatCurvePoint(start.toFloat(), 0f))

        points.addAll(
            buildRamp(
                fromMinute = start,
                toMinute = peakStart,
                fromPercent = 0,
                toPercent = safePeak,
                transitionMode = transitionMode,
                samples = samplesPerRamp
            )
        )

        points.add(FloatCurvePoint(peakEnd.toFloat(), safePeak.toFloat()))

        points.addAll(
            buildRamp(
                fromMinute = peakEnd,
                toMinute = end,
                fromPercent = safePeak,
                toPercent = 0,
                transitionMode = transitionMode,
                samples = samplesPerRamp
            )
        )

        points.add(FloatCurvePoint(MINUTES_PER_DAY.toFloat(), 0f))

        return points
            .distinctBy { point -> "${point.minute}:${point.percent}" }
            .sortedBy { point -> point.minute }
    }

    private fun buildRamp(
        fromMinute: Int,
        toMinute: Int,
        fromPercent: Int,
        toPercent: Int,
        transitionMode: LightProgramTransitionMode,
        samples: Int
    ): List<FloatCurvePoint> {
        if (toMinute <= fromMinute) {
            return listOf(FloatCurvePoint(toMinute.toFloat(), toPercent.toFloat()))
        }

        val safeSamples = samples.coerceAtLeast(2)
        return (1..safeSamples).map { index ->
            val t = index / safeSamples.toFloat()
            val eased = ease(t, transitionMode)
            FloatCurvePoint(
                minute = fromMinute + (toMinute - fromMinute) * t,
                percent = fromPercent + (toPercent - fromPercent) * eased
            )
        }
    }

    private fun ease(
        t: Float,
        mode: LightProgramTransitionMode
    ): Float {
        val clamped = t.coerceIn(0f, 1f)
        return when (mode) {
            LightProgramTransitionMode.LINEAR -> clamped
            LightProgramTransitionMode.SMOOTH -> clamped * clamped * (3f - 2f * clamped)
            LightProgramTransitionMode.NATURAL -> {
                val cosineEase = ((1f - cos(clamped * Math.PI)) / 2f).toFloat()
                cosineEase.pow(1.15f)
            }
        }
    }

    private fun List<CurvePoint>.valueAt(
        minute: Int
    ): Int {
        if (isEmpty()) return 0
        val safeMinute = minute.coerceIn(0, MINUTES_PER_DAY - 1)
        val previous = lastOrNull { point -> point.minute <= safeMinute }
        val next = firstOrNull { point -> point.minute >= safeMinute }

        return when {
            previous == null -> first().value
            next == null -> last().value
            previous.minute == next.minute -> previous.value
            else -> {
                val progress = (safeMinute - previous.minute).toDouble() / (next.minute - previous.minute).toDouble()
                (previous.value + ((next.value - previous.value) * progress))
                    .roundToInt()
                    .coerceIn(0, 100)
            }
        }
    }

    private fun List<CompiledLightProgramPoint>.toStableHash(
        programId: String,
        programName: String,
        transitionMode: LightProgramTransitionMode
    ): String {
        val source = buildString {
            append(programId)
            append('|')
            append(programName)
            append('|')
            append(transitionMode.name)
            forEach { point ->
                append('|')
                append(point.minuteOfDay)
                append(':')
                append(point.red)
                append(',')
                append(point.green)
                append(',')
                append(point.blue)
                append(',')
                append(point.white)
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))

        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private data class FloatCurvePoint(
        val minute: Float,
        val percent: Float
    )

    private data class CurvePoint(
        val minute: Int,
        val value: Int
    )

    private const val MINUTES_PER_DAY = 24 * 60
}

sealed interface LightProgramCompileResult {
    data class Success(
        val schedule: CompiledLightProgramSchedule
    ) : LightProgramCompileResult

    data class Invalid(
        val message: String
    ) : LightProgramCompileResult
}
