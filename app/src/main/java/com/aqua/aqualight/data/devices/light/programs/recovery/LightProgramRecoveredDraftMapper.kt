package com.aqua.aqualight.data.devices.light.programs.recovery

import com.aqua.aqualight.data.devices.api.light.LightChannelRole
import com.aqua.aqualight.data.devices.api.light.LightScheduleChannelState
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramDraftValidator
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramValidationResult
import kotlin.math.roundToInt

/**
 * Builds an editable program draft from concrete LP points read from the
 * controller. The exact controller payload is still matched by checksum; this
 * draft gives users a compact, understandable editor model after auto recovery.
 */
object LightProgramRecoveredDraftMapper {

    fun toEditableDraft(
        scheduleChannels: List<LightScheduleChannelState>
    ): LightProgramDraft? {
        val channels = scheduleChannels.filter { channel ->
            channel.points.isNotEmpty()
        }
        if (channels.isEmpty()) return null

        val allPoints = channels.flatMap { channel -> channel.points }
        if (allPoints.none { point -> point.percent > 0 }) return null

        val channelValues = LightCurveChannelValues(
            red = channels.maxPercentFor(role = LightChannelRole.RED, fallbackIndex = RED_INDEX),
            green = channels.maxPercentFor(role = LightChannelRole.GREEN, fallbackIndex = GREEN_INDEX),
            blue = channels.maxPercentFor(role = LightChannelRole.BLUE, fallbackIndex = BLUE_INDEX),
            white = channels.maxPercentFor(role = LightChannelRole.WHITE, fallbackIndex = WHITE_INDEX)
        ).normalized()

        if (listOf(channelValues.red, channelValues.green, channelValues.blue, channelValues.white).all { it == 0 }) {
            return null
        }

        val startMinute = allPoints
            .minOf { point -> point.minuteOfDay }
            .coerceIn(0, DAY_END_MINUTE - MIN_VALID_DURATION_MINUTES)

        val lastMinute = allPoints
            .maxOf { point -> point.minuteOfDay }
            .coerceIn(startMinute + MIN_VALID_DURATION_MINUTES, DAY_END_MINUTE)

        val peakWindow = estimatePeakWindow(
            channels = channels,
            startMinute = startMinute,
            endMinute = lastMinute
        )

        val draft = LightProgramDraft(
            start = pointFromMinute(startMinute),
            peakStart = pointFromMinute(peakWindow.first),
            peakEnd = pointFromMinute(peakWindow.second),
            end = pointFromMinute(lastMinute),
            channelValues = channelValues,
            repeatMode = RepeatMode.EVERY,
            selectedDays = EVERY_DAY_SELECTION,
            transitionMode = LightCurveTransitionMode.LINEAR
        )

        return when (LightProgramDraftValidator.validate(draft)) {
            LightProgramValidationResult.Valid -> draft
            is LightProgramValidationResult.Invalid -> fallbackDraft(
                startMinute = startMinute,
                endMinute = lastMinute,
                channelValues = channelValues
            )
        }
    }

    private fun estimatePeakWindow(
        channels: List<LightScheduleChannelState>,
        startMinute: Int,
        endMinute: Int
    ): Pair<Int, Int> {
        val totalsByMinute = channels
            .flatMap { channel -> channel.points }
            .groupBy { point -> point.minuteOfDay.coerceIn(0, DAY_END_MINUTE) }
            .mapValues { (_, points) -> points.sumOf { point -> point.percent.coerceIn(0, 100) } }

        val maxTotal = totalsByMinute.values.maxOrNull() ?: 0
        if (maxTotal <= 0) {
            return defaultPeakWindow(
                startMinute = startMinute,
                endMinute = endMinute
            )
        }

        val threshold = (maxTotal * PEAK_THRESHOLD_RATIO).roundToInt().coerceAtLeast(1)
        val peakMinutes = totalsByMinute
            .filterValues { total -> total >= threshold }
            .keys
            .sorted()
            .filter { minute -> minute in (startMinute + 1) until endMinute }

        if (peakMinutes.size >= 2) {
            val peakStart = peakMinutes.first()
            val peakEnd = peakMinutes.last()
            if (peakStart < peakEnd) {
                return peakStart to peakEnd
            }
        }

        return defaultPeakWindow(
            startMinute = startMinute,
            endMinute = endMinute
        )
    }

    private fun defaultPeakWindow(
        startMinute: Int,
        endMinute: Int
    ): Pair<Int, Int> {
        val duration = (endMinute - startMinute).coerceAtLeast(MIN_VALID_DURATION_MINUTES)
        val peakStart = (startMinute + duration / 3)
            .coerceIn(startMinute + 1, endMinute - 2)
        val peakEnd = (startMinute + (duration * 2) / 3)
            .coerceIn(peakStart + 1, endMinute - 1)

        return peakStart to peakEnd
    }

    private fun fallbackDraft(
        startMinute: Int,
        endMinute: Int,
        channelValues: LightCurveChannelValues
    ): LightProgramDraft? {
        val safeEndMinute = endMinute.coerceIn(startMinute + MIN_VALID_DURATION_MINUTES, DAY_END_MINUTE)
        val peakWindow = defaultPeakWindow(
            startMinute = startMinute,
            endMinute = safeEndMinute
        )
        val draft = LightProgramDraft(
            start = pointFromMinute(startMinute),
            peakStart = pointFromMinute(peakWindow.first),
            peakEnd = pointFromMinute(peakWindow.second),
            end = pointFromMinute(safeEndMinute),
            channelValues = channelValues.normalized(),
            repeatMode = RepeatMode.EVERY,
            selectedDays = EVERY_DAY_SELECTION,
            transitionMode = LightCurveTransitionMode.LINEAR
        )

        return when (LightProgramDraftValidator.validate(draft)) {
            LightProgramValidationResult.Valid -> draft
            is LightProgramValidationResult.Invalid -> null
        }
    }

    private fun List<LightScheduleChannelState>.maxPercentFor(
        role: LightChannelRole,
        fallbackIndex: Int
    ): Int {
        val channel = firstOrNull { item -> item.role == role }
            ?: firstOrNull { item -> item.index == fallbackIndex }
            ?: return 0

        return channel.points
            .maxOfOrNull { point -> point.percent.coerceIn(0, 100) }
            ?: 0
    }

    private fun pointFromMinute(
        minuteOfDay: Int
    ): LightCurvePoint {
        val safeMinute = minuteOfDay.coerceIn(0, DAY_END_MINUTE)
        return LightCurvePoint.of(
            hour = safeMinute / 60,
            minute = safeMinute % 60
        )
    }

    private const val DAY_END_MINUTE = 24 * 60
    private const val MIN_VALID_DURATION_MINUTES = 4
    private const val PEAK_THRESHOLD_RATIO = 0.95

    private const val WHITE_INDEX = 0
    private const val RED_INDEX = 1
    private const val GREEN_INDEX = 2
    private const val BLUE_INDEX = 3

    private val EVERY_DAY_SELECTION: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
}
