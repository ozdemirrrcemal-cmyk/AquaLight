package com.aqua.aqualight.application.devices.cooling.program

/** Device-reported limits used by the fan program editor. No production defaults live here. */
data class CoolingProgramPolicy(
    val maximumSlotCount: Int,
    val minimumFanPercent: Int,
    val maximumFanPercent: Int,
    val fanPercentStep: Int,
    val minimumSlotDurationMinutes: Int
) {
    init {
        require(maximumSlotCount > 0)
        require(minimumFanPercent in 0..100)
        require(maximumFanPercent in minimumFanPercent..100)
        require(fanPercentStep in 1..100)
        require((maximumFanPercent - minimumFanPercent) % fanPercentStep == 0) {
            "Fan percentage range must align with the reported step."
        }
        require(minimumSlotDurationMinutes in 1 until COOLING_PROGRAM_MINUTES_PER_DAY)
    }
}
