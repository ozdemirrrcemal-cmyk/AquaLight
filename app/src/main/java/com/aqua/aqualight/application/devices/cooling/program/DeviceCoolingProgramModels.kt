package com.aqua.aqualight.application.devices.cooling.program

private const val PERCENT_MINIMUM = 0
private const val PERCENT_MAXIMUM = 100
internal const val COOLING_PROGRAM_MINUTES_PER_HOUR = 60
internal const val COOLING_PROGRAM_HOURS_PER_DAY = 24
internal const val COOLING_PROGRAM_MINUTES_PER_DAY =
    COOLING_PROGRAM_HOURS_PER_DAY * COOLING_PROGRAM_MINUTES_PER_HOUR

/** Product-level fan program period. Fan output is expressed as percentage above data/runtime. */
data class CoolingProgramSlot(
    val startMinutes: Int,
    val endMinutes: Int,
    val fanLimitPercent: Int
) {
    init {
        require(startMinutes in 0 until COOLING_PROGRAM_MINUTES_PER_DAY)
        require(endMinutes in 1 until COOLING_PROGRAM_MINUTES_PER_DAY)
        require(startMinutes < endMinutes) {
            "Cooling program periods must stay within one calendar day."
        }
        require(fanLimitPercent in PERCENT_MINIMUM..PERCENT_MAXIMUM)
    }
}

data class CoolingProgramSnapshot(
    val slots: List<CoolingProgramSlot>,
    val policy: CoolingProgramPolicy
)
