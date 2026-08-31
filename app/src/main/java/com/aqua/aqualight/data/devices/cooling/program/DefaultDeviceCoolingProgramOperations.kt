package com.aqua.aqualight.data.devices.cooling.program

import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations

/**
 * Production persistence seam for Fan Program.
 *
 * The firmware/runtime program contract is intentionally not implemented yet. Until an authoritative
 * device contract exists, read and save report unavailability and never fabricate device state or a
 * successful persistence acknowledgement.
 */
class DefaultDeviceCoolingProgramOperations : DeviceCoolingProgramOperations {
    override suspend fun readProgram(deviceUid: String): CoolingProgramReadResult {
        require(deviceUid.isNotBlank())
        return CoolingProgramReadResult.Unavailable
    }

    override suspend fun saveProgram(
        deviceUid: String,
        slots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult {
        require(deviceUid.isNotBlank())
        return CoolingProgramSaveResult.Unavailable
    }
}
