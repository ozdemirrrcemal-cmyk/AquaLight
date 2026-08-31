package com.aqua.aqualight.application.devices.cooling.program

sealed interface CoolingProgramReadResult {
    data class Loaded(
        val snapshot: CoolingProgramSnapshot
    ) : CoolingProgramReadResult

    data object Unavailable : CoolingProgramReadResult
    data object NotConnected : CoolingProgramReadResult
}

sealed interface CoolingProgramSaveResult {
    data class Saved(
        val snapshot: CoolingProgramSnapshot
    ) : CoolingProgramSaveResult

    data object Unavailable : CoolingProgramSaveResult
    data object NotConnected : CoolingProgramSaveResult
    data object Rejected : CoolingProgramSaveResult
    data object InvalidConfiguration : CoolingProgramSaveResult
}
