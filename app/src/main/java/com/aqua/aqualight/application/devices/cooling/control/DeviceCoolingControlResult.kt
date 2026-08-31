package com.aqua.aqualight.application.devices.cooling.control

sealed interface DeviceCoolingControlResult {
    data class Available(
        val snapshot: DeviceCoolingControlSnapshot
    ) : DeviceCoolingControlResult

    data class Failed(
        val failure: DeviceCoolingControlFailure
    ) : DeviceCoolingControlResult
}
