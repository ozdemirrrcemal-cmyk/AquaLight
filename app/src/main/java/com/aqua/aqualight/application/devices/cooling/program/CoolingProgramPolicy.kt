package com.aqua.aqualight.application.devices.cooling.program

private const val FAN_PERCENT_MINIMUM = 0
private const val FAN_PERCENT_MAXIMUM = 100
private const val MINIMUM_POSITIVE_VALUE = 1

/** Device-reported limits used by the fan program editor. No production defaults live here. */
data class CoolingProgramPolicy(
    val maximumSlotCount: Int,
    val minimumFanPercent: Int,
    val maximumFanPercent: Int,
    val fanPercentStep: Int,
    val minimumSlotDurationMinutes: Int
) {
    init {
        require(maximumSlotCount >= MINIMUM_POSITIVE_VALUE)
        require(minimumFanPercent in FAN_PERCENT_MINIMUM..FAN_PERCENT_MAXIMUM)
        require(maximumFanPercent in minimumFanPercent..FAN_PERCENT_MAXIMUM)
        require(fanPercentStep in MINIMUM_POSITIVE_VALUE..FAN_PERCENT_MAXIMUM)
        require((maximumFanPercent - minimumFanPercent) % fanPercentStep == 0) {
            "Fan percentage range must align with the reported step."
        }
        require(
            minimumSlotDurationMinutes in MINIMUM_POSITIVE_VALUE until COOLING_PROGRAM_MINUTES_PER_DAY
        )
    }
}
