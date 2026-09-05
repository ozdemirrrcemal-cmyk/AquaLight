package com.aqua.aqualight.application.devices.cooling.program

private const val PERCENT_MINIMUM = 0
private const val PERCENT_MAXIMUM = 100
internal const val COOLING_PROGRAM_MINUTES_PER_HOUR = 60
internal const val COOLING_PROGRAM_HOURS_PER_DAY = 24
internal const val COOLING_PROGRAM_MINUTES_PER_DAY =
    COOLING_PROGRAM_HOURS_PER_DAY * COOLING_PROGRAM_MINUTES_PER_HOUR

/**
 * Product-level fan program period.
 *
 * During this same-day period, the fan starts when the tank reaches [fanOnTemperatureC]
 * and runs at the fixed [targetFanPercent] selected by the user.
 */
data class CoolingProgramSlot(
    val startMinutes: Int,
    val endMinutes: Int,
    val fanOnTemperatureC: Double,
    val targetFanPercent: Int
) {
    init {
        require(startMinutes in 0 until COOLING_PROGRAM_MINUTES_PER_DAY)
        require(endMinutes in 1..COOLING_PROGRAM_MINUTES_PER_DAY)
        require(startMinutes < endMinutes) {
            "Cooling program periods must stay within one calendar day."
        }
        require(fanOnTemperatureC.isFinite())
        require(targetFanPercent in PERCENT_MINIMUM..PERCENT_MAXIMUM)
    }
}

data class CoolingProgramSnapshot(
    val slots: List<CoolingProgramSlot>,
    val policy: CoolingProgramPolicy,
    val clockReady: Boolean,
    val currentMinuteOfDay: Int?,
    val activeSlotIndex: Int?
) {
    init {
        require(clockReady == (currentMinuteOfDay != null))
        require(currentMinuteOfDay == null || currentMinuteOfDay in 0 until COOLING_PROGRAM_MINUTES_PER_DAY)
        require(activeSlotIndex == null || activeSlotIndex in slots.indices)
    }

    val activeSlot: CoolingProgramSlot?
        get() = activeSlotIndex?.let(slots::getOrNull)
}
