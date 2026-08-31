package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingProgramOperations

/**
 * Production persistence seam for Fan Program.
 *
 * The runtime protocol does not expose a stable program contract yet. Production therefore reports
 * unavailability instead of fabricating state. Firmware/wire mapping belongs below this boundary
 * once that protocol is finalized.
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
