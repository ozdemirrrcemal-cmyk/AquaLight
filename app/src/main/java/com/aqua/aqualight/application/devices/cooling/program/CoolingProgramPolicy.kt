package com.aqua.aqualight.application.devices.cooling.program

import kotlin.math.abs
import kotlin.math.round

private const val FAN_PERCENT_MINIMUM = 0
private const val FAN_PERCENT_MAXIMUM = 100
private const val MINIMUM_POSITIVE_VALUE = 1
private const val TEMPERATURE_ALIGNMENT_EPSILON = 0.000_001

data class CoolingProgramFanPolicy(
    val minimumPercent: Int,
    val maximumPercent: Int,
    val stepPercent: Int
) {
    init {
        require(minimumPercent in FAN_PERCENT_MINIMUM..FAN_PERCENT_MAXIMUM)
        require(maximumPercent in minimumPercent..FAN_PERCENT_MAXIMUM)
        require(stepPercent in MINIMUM_POSITIVE_VALUE..FAN_PERCENT_MAXIMUM)
        require((maximumPercent - minimumPercent) % stepPercent == 0) {
            "Fan percentage range must align with the reported step."
        }
    }
}

data class CoolingProgramFanOnTemperaturePolicy(
    val minimumC: Double,
    val maximumC: Double,
    val stepC: Double,
    val defaultC: Double
) {
    init {
        require(minimumC.isFinite() && maximumC.isFinite())
        require(stepC.isFinite() && stepC > 0.0)
        require(defaultC.isFinite())
        require(maximumC >= minimumC)
        require(defaultC in minimumC..maximumC)
        require(isTemperatureAligned(maximumC, minimumC, stepC)) {
            "Fan-on temperature range must align with the reported step."
        }
        require(isTemperatureAligned(defaultC, minimumC, stepC)) {
            "Default fan-on temperature must align with the reported step."
        }
    }
}

/** Device-reported limits used by the fan program editor. No production defaults live here. */
data class CoolingProgramPolicy(
    val maximumSlotCount: Int,
    val minimumSlotDurationMinutes: Int,
    val fan: CoolingProgramFanPolicy,
    val fanOnTemperature: CoolingProgramFanOnTemperaturePolicy
) {
    init {
        require(maximumSlotCount >= MINIMUM_POSITIVE_VALUE)
        require(
            minimumSlotDurationMinutes in MINIMUM_POSITIVE_VALUE until COOLING_PROGRAM_MINUTES_PER_DAY
        )
    }

    val minimumFanPercent: Int
        get() = fan.minimumPercent

    val maximumFanPercent: Int
        get() = fan.maximumPercent

    val fanPercentStep: Int
        get() = fan.stepPercent
}

internal fun isTemperatureAligned(value: Double, origin: Double, step: Double): Boolean {
    val units = (value - origin) / step
    return abs(units - round(units)) <= TEMPERATURE_ALIGNMENT_EPSILON
}
