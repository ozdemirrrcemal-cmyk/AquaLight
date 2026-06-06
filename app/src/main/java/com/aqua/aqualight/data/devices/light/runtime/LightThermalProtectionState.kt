package com.aqua.aqualight.data.devices.light.runtime

import kotlin.math.roundToInt

data class LightThermalProtectionState(
    val hasData: Boolean = false,
    val sensorCount: Int = 0,
    val currentTemperatureCelsius: Double? = null,
    val limitTemperatureCelsius: Int = 50,
    val lightReductionPercent: Int = 70,
    val recoveryIntervalSeconds: Int = 60,
    val currentReductionMultiplier: Double? = null
) {

    val currentTemperatureText: String
        get() {
            val temperature = currentTemperatureCelsius ?: return "-- °C"
            return "${temperature.roundToOneDecimal()}°C"
        }

    val statusText: String
        get() {
            val multiplier = currentReductionMultiplier

            return when {
                !hasData -> "SYNC"
                multiplier != null && multiplier < 0.99 -> "REDUCING"
                else -> "ACTIVE"
            }
        }

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
}