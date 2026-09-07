package com.aqua.aqualight.application.devices.cooling.program

/** Application boundary for authoritative device program read/save operations. */
interface DeviceCoolingProgramOperations {
    suspend fun readProgram(deviceUid: String): CoolingProgramReadResult

    suspend fun saveProgram(
        deviceUid: String,
        slots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult
}
