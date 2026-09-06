package com.aqua.aqualight.application.devices.cooling.control

import com.aqua.aqualight.application.devices.DeviceOperationDiagnostic

sealed interface DeviceCoolingControlResult {
    data class Available(
        val snapshot: DeviceCoolingControlSnapshot
    ) : DeviceCoolingControlResult

    data class Failed(
        val failure: DeviceCoolingControlFailure,
        val diagnostic: DeviceOperationDiagnostic? = null
    ) : DeviceCoolingControlResult
}
