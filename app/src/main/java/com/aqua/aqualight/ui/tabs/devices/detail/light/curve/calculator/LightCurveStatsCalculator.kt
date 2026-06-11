package com.aqua.aqualight.ui.tabs.devices.detail.light.curve.calculator

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveStats
import kotlin.math.roundToInt

object LightCurveStatsCalculator {

    /**
     * Temporary preview calculation.
     * Real device watt/channel power mapping will replace this later.
     */
    fun calculate(
        state: LightCurveGraphState
    ): LightCurveStats {
        val channels = state.channelValues

        val peakOutput = listOf(
            channels.red,
            channels.green,
            channels.blue,
            channels.white
        ).maxOrNull() ?: 0

        val durationMinutes = (
            endMinutesForStats(state.end) - state.start.totalMinutes
        ).coerceAtLeast(0)

        val durationHours =
            durationMinutes / 60.0

        val averagePercent =
            (
                channels.red +
                    channels.green +
                    channels.blue +
                    channels.white
                ) / 4.0

        val estimatedWatts =
            (averagePercent / 100.0) * 40.0

        return LightCurveStats(
            outputPercent = peakOutput,
            estimatedPowerWatts = estimatedWatts,
            durationHours = durationHours
        )
    }

    private fun endMinutesForStats(
        point: LightCurvePoint
    ): Int {
        return if (point.hour == 0 && point.minute == 0) {
            24 * 60
        } else {
            point.totalMinutes
        }
    }
}