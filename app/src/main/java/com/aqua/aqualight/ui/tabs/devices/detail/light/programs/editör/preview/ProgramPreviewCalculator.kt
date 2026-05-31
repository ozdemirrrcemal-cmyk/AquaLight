package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.preview

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CurvePointState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.MAX_PERCENT
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramEditorConstants.MINUTES_IN_DAY
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewFrame
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.ProgramChannelBalanceDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RampSmoothing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

object ProgramPreviewCalculator {

    fun createFrame(
        isProMode: Boolean,
        selectedProChannel: ProChannel,
        rampSmoothing: RampSmoothing,
        simpleCurvePoints: List<CurvePointState>,
        proChannelCurves: Map<ProChannel, List<CurvePointState>>,
        channelBalance: ProgramChannelBalanceDraft,
        dayMinute: Int
    ): PreviewFrame {
        return if (isProMode) {
            createProFrame(
                selectedProChannel = selectedProChannel,
                rampSmoothing = rampSmoothing,
                proChannelCurves = proChannelCurves,
                dayMinute = dayMinute
            )
        } else {
            createSimpleFrame(
                rampSmoothing = rampSmoothing,
                simpleCurvePoints = simpleCurvePoints,
                channelBalance = channelBalance,
                dayMinute = dayMinute
            )
        }
    }

    private fun createSimpleFrame(
        rampSmoothing: RampSmoothing,
        simpleCurvePoints: List<CurvePointState>,
        channelBalance: ProgramChannelBalanceDraft,
        dayMinute: Int
    ): PreviewFrame {
        val main =
            calculateCurveIntensityAtMinute(
                points = simpleCurvePoints,
                rampSmoothing = rampSmoothing,
                dayMinute = dayMinute
            )

        return PreviewFrame(
            time = minutesToTime(dayMinute),
            mainIntensity = main,
            red =
                scaleSimpleChannel(
                    channelValue = channelBalance.red,
                    mainIntensity = main
                ),
            green =
                scaleSimpleChannel(
                    channelValue = channelBalance.green,
                    mainIntensity = main
                ),
            blue =
                scaleSimpleChannel(
                    channelValue = channelBalance.blue,
                    mainIntensity = main
                ),
            white =
                scaleSimpleChannel(
                    channelValue = channelBalance.white,
                    mainIntensity = main
                )
        )
    }

    private fun createProFrame(
        selectedProChannel: ProChannel,
        rampSmoothing: RampSmoothing,
        proChannelCurves: Map<ProChannel, List<CurvePointState>>,
        dayMinute: Int
    ): PreviewFrame {
        val red =
            calculateCurveIntensityAtMinute(
                points = proChannelCurves[ProChannel.RED].orEmpty(),
                rampSmoothing = rampSmoothing,
                dayMinute = dayMinute
            )

        val green =
            calculateCurveIntensityAtMinute(
                points = proChannelCurves[ProChannel.GREEN].orEmpty(),
                rampSmoothing = rampSmoothing,
                dayMinute = dayMinute
            )

        val blue =
            calculateCurveIntensityAtMinute(
                points = proChannelCurves[ProChannel.BLUE].orEmpty(),
                rampSmoothing = rampSmoothing,
                dayMinute = dayMinute
            )

        val white =
            calculateCurveIntensityAtMinute(
                points = proChannelCurves[ProChannel.WHITE].orEmpty(),
                rampSmoothing = rampSmoothing,
                dayMinute = dayMinute
            )

        val main =
            when (selectedProChannel) {
                ProChannel.RED -> red
                ProChannel.GREEN -> green
                ProChannel.BLUE -> blue
                ProChannel.WHITE -> white
            }

        return PreviewFrame(
            time = minutesToTime(dayMinute),
            mainIntensity = main,
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }

    private fun calculateCurveIntensityAtMinute(
        points: List<CurvePointState>,
        rampSmoothing: RampSmoothing,
        dayMinute: Int
    ): Int {
        val sortedPoints =
            points.sortedWith(
                compareBy<CurvePointState> {
                    timeToMinutes(
                        time = it.time
                    )
                }.thenBy {
                    it.kind.sortOrder
                }
            )

        if (sortedPoints.isEmpty()) {
            return 0
        }

        val safeMinute =
            dayMinute.coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )

        val firstPoint = sortedPoints.first()
        val lastPoint = sortedPoints.last()

        if (safeMinute <= timeToMinutes(firstPoint.time)) {
            return firstPoint.intensity.coerceIn(
                minimumValue = 0,
                maximumValue = MAX_PERCENT
            )
        }

        if (safeMinute >= timeToMinutes(lastPoint.time)) {
            return lastPoint.intensity.coerceIn(
                minimumValue = 0,
                maximumValue = MAX_PERCENT
            )
        }

        val segment =
            sortedPoints
                .zipWithNext()
                .firstOrNull { pair ->
                    val startMinute =
                        timeToMinutes(
                            time = pair.first.time
                        )

                    val endMinute =
                        timeToMinutes(
                            time = pair.second.time
                        )

                    safeMinute in startMinute..endMinute
                } ?: return 0

        val segmentStartMinute =
            timeToMinutes(
                time = segment.first.time
            )

        val segmentEndMinute =
            timeToMinutes(
                time = segment.second.time
            )

        val duration =
            (segmentEndMinute - segmentStartMinute)
                .coerceAtLeast(1)

        val rawProgress =
            ((safeMinute - segmentStartMinute).toFloat() / duration.toFloat())
                .coerceIn(
                    minimumValue = 0f,
                    maximumValue = 1f
                )

        val progress =
            applyRampSmoothing(
                rampSmoothing = rampSmoothing,
                progress = rawProgress
            )

        val startIntensity = segment.first.intensity
        val endIntensity = segment.second.intensity

        return (startIntensity + ((endIntensity - startIntensity) * progress))
            .roundToInt()
            .coerceIn(
                minimumValue = 0,
                maximumValue = MAX_PERCENT
            )
    }

    private fun applyRampSmoothing(
        rampSmoothing: RampSmoothing,
        progress: Float
    ): Float {
        val safeProgress =
            progress.coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            )

        return when (rampSmoothing) {
            RampSmoothing.LINEAR -> {
                safeProgress
            }

            RampSmoothing.SOFT -> {
                safeProgress * safeProgress * (3f - (2f * safeProgress))
            }

            RampSmoothing.NATURAL -> {
                ((1.0 - cos(safeProgress * PI)) / 2.0).toFloat()
            }
        }
    }

    private fun scaleSimpleChannel(
        channelValue: Int,
        mainIntensity: Int
    ): Int {
        return ((channelValue * mainIntensity) / 100f)
            .roundToInt()
            .coerceIn(
                minimumValue = 0,
                maximumValue = MAX_PERCENT
            )
    }

    private fun timeToMinutes(
        time: String
    ): Int {
        val parts = time.split(":")

        if (parts.size != 2) {
            return 0
        }

        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        return (hour * 60 + minute)
            .coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
            minutes.coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )

        val hour = safeMinutes / 60
        val minute = safeMinutes % 60

        return "%02d:%02d".format(
            hour,
            minute
        )
    }
}