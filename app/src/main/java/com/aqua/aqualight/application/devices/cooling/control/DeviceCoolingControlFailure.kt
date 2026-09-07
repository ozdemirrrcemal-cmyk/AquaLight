package com.aqua.aqualight.application.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure

sealed interface DeviceCoolingControlFailure {
    data object Unsupported : DeviceCoolingControlFailure
    data object Unavailable : DeviceCoolingControlFailure
    data object NotConnected : DeviceCoolingControlFailure
    data class Rejected(
        val reason: DeviceCoolingCommandFailure
    ) : DeviceCoolingControlFailure
    data object InvalidData : DeviceCoolingControlFailure
}
