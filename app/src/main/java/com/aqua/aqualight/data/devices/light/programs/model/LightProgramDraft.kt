package com.aqua.aqualight.data.devices.light.programs.model


data class LightProgramDraft(
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val channelValues: LightCurveChannelValues,
    val repeatMode: RepeatMode,
    val selectedDays: Set<Int>,
    val transitionMode: LightCurveTransitionMode
) {
    companion object {
        fun default(): LightProgramDraft = LightProgramDraft(
            start = LightCurvePoint.of(8, 0),
            peakStart = LightCurvePoint.of(10, 0),
            peakEnd = LightCurvePoint.of(16, 0),
            end = LightCurvePoint.of(18, 0),
            channelValues = LightCurveChannelValues(red = 0, green = 0, blue = 0, white = 0),
            repeatMode = RepeatMode.EVERY,
            selectedDays = setOf(1, 2, 3, 4, 5, 6, 7),
            transitionMode = LightCurveTransitionMode.NATURAL
        )
    }
}
