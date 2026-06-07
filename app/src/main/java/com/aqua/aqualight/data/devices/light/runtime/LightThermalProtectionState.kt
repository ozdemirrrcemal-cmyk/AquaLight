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
            if (!hasData) {
                return "Syncing"
            }

            val temperature = currentTemperatureCelsius
                ?: return "Syncing"

            return "${temperature.roundToOneDecimal()}°C"
        }

    val statusText: String
        get() {
            if (!hasData || currentTemperatureCelsius == null) {
                return ""
            }

            val multiplier = currentReductionMultiplier

            return when {
                multiplier != null && multiplier < 0.99 -> "REDUCING"
                else -> "ACTIVE"
            }
        }

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }
}