package com.aqua.aqualight.data.devices.light.runtime

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint

data class LightDeviceTimeState(
    val year: Int,
    val month: Int,
    val day: Int,
    val weekDay: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val source: Source
) {

    val curvePoint: LightCurvePoint
        get() = LightCurvePoint.of(
            hour = hour,
            minute = minute
        )

    val timeText: String
        get() = "%02d:%02d".format(
            hour.coerceIn(0, 23),
            minute.coerceIn(0, 59)
        )

    val dateText: String
        get() = "%04d-%02d-%02d".format(
            year,
            month,
            day
        )

    enum class Source {
        DEVICE,
        PHONE_FALLBACK
    }
}