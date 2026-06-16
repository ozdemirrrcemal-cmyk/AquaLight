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
        val rampUpDuration = anchors.peakStartMinute - anchors.startMinute
        val rampDownDuration = anchors.endMinute - anchors.peakEndMinute

        val rampUpDesired = calculateDesiredIntermediatePointCount(
            durationMinutes = rampUpDuration,
            options = options
        )
        val rampDownDesired = calculateDesiredIntermediatePointCount(
            durationMinutes = rampDownDuration,
            options = options
        )

        val availableIntermediatePoints = (
            options.maximumPointsPerChannel -
                LightProgramPointExpansionOptions.MINIMUM_ANCHOR_POINTS_PER_CHANNEL
            ).coerceAtLeast(0)

        val allocation = allocateIntermediatePointBudget(
            firstDesired = rampUpDesired,
            secondDesired = rampDownDesired,
            maximumTotal = availableIntermediatePoints
        )

        val points = mutableListOf<LightProgramDevicePoint>()

        points += LightProgramDevicePoint(
            minuteOfDay = anchors.startMinute,
            percent = 0
        )

        points += buildRampIntermediatePoints(
            fromMinute = anchors.startMinute,
            toMinute = anchors.peakStartMinute,
            fromPercent = 0,
            toPercent = peakPercent,
            transitionMode = transitionMode,
            intermediatePointCount = allocation.first
        )

        points += LightProgramDevicePoint(
            minuteOfDay = anchors.peakStartMinute,
            percent = peakPercent
        )

        points += LightProgramDevicePoint(
            minuteOfDay = anchors.peakEndMinute,
            percent = peakPercent
        )

        points += buildRampIntermediatePoints(
            fromMinute = anchors.peakEndMinute,
            toMinute = anchors.endMinute,
            fromPercent = peakPercent,
            toPercent = 0,
            transitionMode = transitionMode,
            intermediatePointCount = allocation.second
        )

        points += LightProgramDevicePoint(
            minuteOfDay = anchors.endMinute,
            percent = 0
        )

        return points
            .sortedBy { point -> point.minuteOfDay }
            .dedupeSameMinuteKeepingLast()
    }

    private fun buildRampIntermediatePoints(
        fromMinute: Int,
        toMinute: Int,
        fromPercent: Int,
        toPercent: Int,
        transitionMode: LightCurveTransitionMode,
        intermediatePointCount: Int
    ): List<LightProgramDevicePoint> {
        val duration = toMinute - fromMinute
        if (duration <= 1 || intermediatePointCount <= 0) {
            return emptyList()
        }

        return (1..intermediatePointCount).map { index ->
            val t = index / (intermediatePointCount + 1).toFloat()
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
            .filter { point -> point.minuteOfDay in (fromMinute + 1) until toMinute }
            .dedupeSameMinuteKeepingLast()
    }

    private fun calculateDesiredIntermediatePointCount(
        durationMinutes: Int,
        options: LightProgramPointExpansionOptions
    ): Int {
        if (durationMinutes <= 1) {
            return 0
        }

        val byStep = ceil(durationMinutes / options.rampStepMinutes.toDouble())
            .toInt()
            .coerceAtLeast(1) - 1

        val requested = byStep.coerceAtLeast(
            options.minimumIntermediatePointsPerRamp
        )

        // Integer-minute device points cannot contain more unique intermediate
        // samples than the minutes inside the ramp. Endpoints are uploaded as
        // explicit user anchors, so they are intentionally excluded here.
        return requested.coerceAtMost(durationMinutes - 1)
    }

    private fun allocateIntermediatePointBudget(
        firstDesired: Int,
        secondDesired: Int,
        maximumTotal: Int
    ): Pair<Int, Int> {
        val firstSafe = firstDesired.coerceAtLeast(0)
        val secondSafe = secondDesired.coerceAtLeast(0)
        val maxSafe = maximumTotal.coerceAtLeast(0)
        val desiredTotal = firstSafe + secondSafe

        if (desiredTotal <= maxSafe) {
            return firstSafe to secondSafe
        }
        if (maxSafe == 0 || desiredTotal == 0) {
            return 0 to 0
        }

        var first = ((maxSafe * firstSafe) / desiredTotal.toFloat())
            .roundToInt()
            .coerceIn(
                if (firstSafe > 0) 1 else 0,
                firstSafe
            )
        var second = (maxSafe - first).coerceIn(
            if (secondSafe > 0) 1 else 0,
            secondSafe
        )

        while (first + second > maxSafe) {
            if (first >= second && first > 0) {
                first--
            } else if (second > 0) {
                second--
            } else {
                break
            }
        }

        while (first + second < maxSafe) {
            val firstRemaining = firstSafe - first
            val secondRemaining = secondSafe - second
            when {
                firstRemaining <= 0 && secondRemaining <= 0 -> break
                firstRemaining >= secondRemaining && firstRemaining > 0 -> first++
                secondRemaining > 0 -> second++
                else -> break
            }
        }

        return first to second
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
