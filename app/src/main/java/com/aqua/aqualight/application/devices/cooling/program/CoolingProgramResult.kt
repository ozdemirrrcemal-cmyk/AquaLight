package com.aqua.aqualight.application.devices.cooling.program

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure

sealed interface CoolingProgramReadResult {
    data class Loaded(
        val snapshot: CoolingProgramSnapshot
    ) : CoolingProgramReadResult

    data object Unsupported : CoolingProgramReadResult
    data object Unavailable : CoolingProgramReadResult
    data object NotConnected : CoolingProgramReadResult
    data class Rejected(
        val reason: DeviceCoolingCommandFailure
    ) : CoolingProgramReadResult
}

sealed interface CoolingProgramSaveResult {
    data class Saved(
        val snapshot: CoolingProgramSnapshot
    ) : CoolingProgramSaveResult

    data object Unsupported : CoolingProgramSaveResult
    data object Unavailable : CoolingProgramSaveResult
    data object NotConnected : CoolingProgramSaveResult
    data class Rejected(
        val reason: DeviceCoolingCommandFailure
    ) : CoolingProgramSaveResult
    data object InvalidConfiguration : CoolingProgramSaveResult
}
