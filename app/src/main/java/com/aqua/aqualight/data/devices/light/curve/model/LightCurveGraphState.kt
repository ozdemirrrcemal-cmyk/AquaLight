package com.aqua.aqualight.data.devices.light.curve.model

data class LightCurveGraphState(
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val channelValues: LightCurveChannelValues,
    val currentTime: LightCurvePoint,
    val transitionMode: LightCurveTransitionMode = LightCurveTransitionMode.LINEAR,
    val controllerPointChannels: List<LightCurveGraphControllerChannel> = emptyList(),
    val moonlightSegments: List<LightCurveMoonlightGraphSegment> = emptyList()
) {
    companion object {
        fun preview(): LightCurveGraphState {
            return LightCurveGraphState(
                start = LightCurvePoint.of(7, 0),
                peakStart = LightCurvePoint.of(9, 0),
                peakEnd = LightCurvePoint.of(17, 0),
                end = LightCurvePoint.of(20, 0),
                channelValues = LightCurveChannelValues(
                    red = 80,
                    green = 85,
                    blue = 100,
                    white = 60
                ),
                currentTime = LightCurvePoint.of(13, 28),
                transitionMode = LightCurveTransitionMode.LINEAR,
                controllerPointChannels = emptyList(),
                moonlightSegments = emptyList()
            )
        }
    }
}

data class LightCurveGraphControllerChannel(
    val channel: LightCurveGraphChannel,
    val points: List<LightCurveGraphControllerPoint>
)

data class LightCurveGraphControllerPoint(
    val minuteOfDay: Int,
    val percent: Int
)

enum class LightCurveGraphChannel {
    WHITE,
    RED,
    GREEN,
    BLUE
}

data class LightCurveMoonlightGraphSegment(
    val startMinute: Int,
    val endMinute: Int,
    val outputPercent: Int,
    val label: String = "Moonlight"
) {
    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
