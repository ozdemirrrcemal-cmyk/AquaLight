package com.aqua.aqualight.data.devices.light.programs.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint

data class MoonlightSettings(
    val enabled: Boolean = false,
    val followProgramEnd: Boolean = true,
    val startTime: LightCurvePoint = LightCurvePoint.of(20, 0),
    val endTime: LightCurvePoint = LightCurvePoint.of(6, 0),
    val channel: MoonlightChannel = MoonlightChannel.BLUE,
    val intensityPercent: Int = 5
)