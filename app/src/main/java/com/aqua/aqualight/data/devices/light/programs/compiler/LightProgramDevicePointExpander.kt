package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Converts the editor draft into controller-safe points.
 *
 * ESP32 firmware behavior this compiler targets:
 * - The light program is stored under LLight.Data[index].LP.
 * - LLight.SetFromJSon updates only Data indexes included in the request.
 * - MLight.SetFromJSon clears and replaces LP only when an LP array is sent
 *   for that specific channel.
 *
 * Because of that, a full program write must include every current firmware
 * light channel. When a user sets a channel to 0%, that channel still receives
 * four zero anchors so old LP points cannot remain active on the controller.
 * This is not a separate "zero channel" concept; it is the firmware-safe full
 * replacement behavior for a channel whose target percent is zero.
 *
 * Important rules:
 * - LINEAR is the normal mode and stays exactly on the four user time anchors.
 * - SMOOTH/NATURAL are expanded on Android while firmware has no native
 *   transition-mode field.
 * - Dashboard must render controller runtime points; it should not read local
 *   transition metadata and reshape live data.
 */
object LightProgramDevicePointExpander {

    fun expand(
        draft: LightProgramDraft,
        options: LightProgramPointExpansionOptions = LightProgramPointExpansionOptions()
    ): LightProgramDeviceSchedule {
        val normalizedDraft = draft.copy(
            channelValues = draft.channelValues.normalized()
        )
        val anchors = ProgramAnchors.from(normalizedDraft)

        return LightProgramDeviceSchedule(
            transitionMode = normalizedDraft.transitionMode,
            strategy = options.strategy,
            channels = listOf(
                buildChannel(
                    channel = LightProgramDeviceChannel.WHITE,
                    peakPercent = normalizedDraft.channelValues.white,
                    anchors = anchors,
                    transitionMode = normalizedDraft.transitionMode,
                    options = options
                ),
                buildChannel(
                    channel = LightProgramDeviceChannel.RED,
                    peakPercent = normalizedDraft.channelValues.red,
                    anchors = anchors,
                    transitionMode = normalizedDraft.transitionMode,
                    options = options
                ),
                buildChannel(
                    channel = LightProgramDeviceChannel.GREEN,
                    peakPercent = normalizedDraft.channelValues.green,
                    anchors = anchors,
                    transitionMode = normalizedDraft.transitionMode,
                    options = options
                ),
                buildChannel(
                    channel = LightProgramDeviceChannel.BLUE,
                    peakPercent = normalizedDraft.channelValues.blue,
                    anchors = anchors,
                    transitionMode = normalizedDraft.transitionMode,
                    options = options
                )
            )
        )
    }

    private fun buildChannel(
        channel: LightProgramDeviceChannel,
        peakPercent: Int,
        anchors: ProgramAnchors,
        transitionMode: LightCurveTransitionMode,
        options: LightProgramPointExpansionOptions
    ): LightProgramDeviceChannelSchedule {
        val safePeak = peakPercent.coerceIn(0, 100)
        val points = when {
            options.strategy == LightProgramDeviceTransitionStrategy.NATIVE_TRANSITION -> {
                buildAnchorPoints(anchors, safePeak)
            }

            transitionMode == LightCurveTransitionMode.LINEAR -> {
                buildAnchorPoints(anchors, safePeak)
            }

            safePeak == 0 -> {
                // Current ESP32 updates only the submitted channel indexes.
                // Sending four 0% anchors replaces old LP points and keeps
                // normal-mode shape consistent: every channel has four anchors.
                buildAnchorPoints(anchors, safePeak)
            }

            else -> {
                buildExpandedPoints(
                    anchors = anchors,
                    peakPercent = safePeak,
                    transitionMode = transitionMode,
                    options = options
                )
            }
        }

        return LightProgramDeviceChannelSchedule(
            channel = channel,
            firmwareChannelIndex = channel.firmwareChannelIndex,
            points = points
        )
    }

    private fun buildAnchorPoints(
        anchors: ProgramAnchors,
        peakPercent: Int
    ): List<LightProgramDevicePoint> {
        return listOf(
            LightProgramDevicePoint(
                minuteOfDay = anchors.startMinute,
                percent = 0
            ),
            LightProgramDevicePoint(
                minuteOfDay = anchors.peakStartMinute,
                percent = peakPercent
            ),
            LightProgramDevicePoint(
                minuteOfDay = anchors.peakEndMinute,
                percent = peakPercent
            ),
            LightProgramDevicePoint(
                minuteOfDay = anchors.endMinute,
                percent = 0
            )
        )
    }

    private fun buildExpandedPoints(
        anchors: ProgramAnchors,
        peakPercent: Int,
        transitionMode: LightCurveTransitionMode,
        options: LightProgramPointExpansionOptions
    ): List<LightProgramDevicePoint> {
        val points = mutableListOf<LightProgramDevicePoint>()

        points += LightProgramDevicePoint(
            minuteOfDay = anchors.startMinute,
            percent = 0
        )

        points += buildRampPoints(
            fromMinute = anchors.startMinute,
            toMinute = anchors.peakStartMinute,
            fromPercent = 0,
            toPercent = peakPercent,
            transitionMode = transitionMode,
            options = options
        )

        points += LightProgramDevicePoint(
            minuteOfDay = anchors.peakEndMinute,
            percent = peakPercent
        )

        points += buildRampPoints(
            fromMinute = anchors.peakEndMinute,
            toMinute = anchors.endMinute,
            fromPercent = peakPercent,
            toPercent = 0,
            transitionMode = transitionMode,
            options = options
        )

        return points
            .sortedBy { point -> point.minuteOfDay }
            .dedupeSameMinuteKeepingLast()
    }

    private fun buildRampPoints(
        fromMinute: Int,
        toMinute: Int,
        fromPercent: Int,
        toPercent: Int,
        transitionMode: LightCurveTransitionMode,
        options: LightProgramPointExpansionOptions
    ): List<LightProgramDevicePoint> {
        val duration = toMinute - fromMinute
        if (duration <= 0) {
            return listOf(
                LightProgramDevicePoint(
                    minuteOfDay = toMinute.coerceIn(0, MINUTES_PER_DAY),
                    percent = toPercent.coerceIn(0, 100)
                )
            )
        }

        val steps = calculateStepCount(
            durationMinutes = duration,
            options = options
        )

        return (1..steps).map { index ->
            val t = index / steps.toFloat()
            val eased = LightCurveInterpolator.ease(
                t = t,
                mode = transitionMode
            )

            val minute = fromMinute + (duration * t).roundToInt()
            val percent = fromPercent + ((toPercent - fromPercent) * eased).roundToInt()

            LightProgramDevicePoint(
                minuteOfDay = minute.coerceIn(0, MINUTES_PER_DAY),
                percent = percent.coerceIn(0, 100)
            )
        }
    }

    private fun calculateStepCount(
        durationMinutes: Int,
        options: LightProgramPointExpansionOptions
    ): Int {
        val byStep = ceil(durationMinutes / options.rampStepMinutes.toDouble()).toInt()
        val requested = byStep
            .coerceAtLeast(options.minimumRampPoints)
            .coerceAtMost(options.maximumRampPoints)

        // Integer-minute device points cannot contain more unique samples than
        // the number of minutes in the ramp. This protects very short ramps.
        return requested.coerceAtMost(durationMinutes.coerceAtLeast(1))
    }

    private fun List<LightProgramDevicePoint>.dedupeSameMinuteKeepingLast(): List<LightProgramDevicePoint> {
        val byMinute = linkedMapOf<Int, LightProgramDevicePoint>()
        forEach { point ->
            byMinute[point.minuteOfDay] = point
        }
        return byMinute.values.toList()
    }

    private data class ProgramAnchors(
        val startMinute: Int,
        val peakStartMinute: Int,
        val peakEndMinute: Int,
        val endMinute: Int
    ) {
        companion object {
            fun from(
                draft: LightProgramDraft
            ): ProgramAnchors {
                val start = LightProgramTimeMath
                    .startMinutes(draft.start)
                    .coerceIn(0, MINUTES_PER_DAY)

                val peakStart = LightProgramTimeMath
                    .normalMinutes(draft.peakStart)
                    .coerceIn(start, MINUTES_PER_DAY)

                val peakEnd = LightProgramTimeMath
                    .normalMinutes(draft.peakEnd)
                    .coerceIn(peakStart, MINUTES_PER_DAY)

                val end = LightProgramTimeMath
                    .endMinutes(draft.end)
                    .coerceIn(peakEnd, MINUTES_PER_DAY)

                return ProgramAnchors(
                    startMinute = start,
                    peakStartMinute = peakStart,
                    peakEndMinute = peakEnd,
                    endMinute = end
                )
            }
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
