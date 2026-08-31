package com.aqua.aqualight.application.devices.cooling

private const val PERCENT_MINIMUM = 0
private const val PERCENT_MAXIMUM = 100
internal const val COOLING_PROGRAM_MINUTES_PER_HOUR = 60
internal const val COOLING_PROGRAM_HOURS_PER_DAY = 24
internal const val COOLING_PROGRAM_MINUTES_PER_DAY =
    COOLING_PROGRAM_HOURS_PER_DAY * COOLING_PROGRAM_MINUTES_PER_HOUR

/** Device-reported product capabilities for the fan program editor. */
data class CoolingProgramCapabilities(
    val minimumSlotCount: Int,
    val maximumSlotCount: Int,
    val minimumFanLimitPercent: Int,
    val maximumFanLimitPercent: Int,
    val fanLimitStepPercent: Int,
    val minimumSlotDurationMinutes: Int
) {
    init {
        require(minimumSlotCount >= 0)
        require(maximumSlotCount >= minimumSlotCount)
        require(minimumFanLimitPercent in PERCENT_MINIMUM..PERCENT_MAXIMUM)
        require(maximumFanLimitPercent in minimumFanLimitPercent..PERCENT_MAXIMUM)
        require(fanLimitStepPercent > 0)
        require(fanLimitStepPercent <= PERCENT_MAXIMUM)
        require(
            (maximumFanLimitPercent - minimumFanLimitPercent) % fanLimitStepPercent == 0
        ) { "Fan percentage range must align with the reported step." }
        require(minimumSlotDurationMinutes in 1 until COOLING_PROGRAM_MINUTES_PER_DAY)
    }
}

/** Product-level schedule period. Fan output is always expressed as percentage above data/runtime. */
data class CoolingProgramSlot(
    val id: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val fanLimitPercent: Int
) {
    init {
        require(id.isNotBlank())
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
    val capabilities: CoolingProgramCapabilities
)

sealed interface CoolingProgramReadResult {
    data class Loaded(val snapshot: CoolingProgramSnapshot) : CoolingProgramReadResult
    data object Unsupported : CoolingProgramReadResult
    data object Unavailable : CoolingProgramReadResult
}

sealed interface CoolingProgramSaveResult {
    data class Saved(val snapshot: CoolingProgramSnapshot) : CoolingProgramSaveResult
    data object Unsupported : CoolingProgramSaveResult
    data object Unavailable : CoolingProgramSaveResult
    data object InvalidConfiguration : CoolingProgramSaveResult
}

interface DeviceCoolingProgramOperations {
    suspend fun readProgram(deviceUid: String): CoolingProgramReadResult

    suspend fun saveProgram(
        deviceUid: String,
        slots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult
}
