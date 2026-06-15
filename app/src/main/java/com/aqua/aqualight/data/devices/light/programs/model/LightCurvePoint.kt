package com.aqua.aqualight.data.devices.light.programs.model

data class LightCurvePoint(
    val hour: Int,
    val minute: Int = 0
) {
    val totalMinutes: Int
        get() = (hour.coerceIn(0, 24) * 60 + minute.coerceIn(0, 59))
            .coerceIn(0, 24 * 60)

    val label: String
        get() = "%02d:%02d".format(hour.coerceIn(0, 24), minute.coerceIn(0, 59))

    companion object {
        fun of(hour: Int, minute: Int = 0): LightCurvePoint {
            return LightCurvePoint(hour = hour, minute = minute)
        }

        fun fromTotalMinutes(totalMinutes: Int): LightCurvePoint {
            val safeMinutes = totalMinutes.coerceIn(0, 24 * 60)
            if (safeMinutes == 24 * 60) {
                return LightCurvePoint(hour = 0, minute = 0)
            }
            return LightCurvePoint(
                hour = safeMinutes / 60,
                minute = safeMinutes % 60
            )
        }
    }
}