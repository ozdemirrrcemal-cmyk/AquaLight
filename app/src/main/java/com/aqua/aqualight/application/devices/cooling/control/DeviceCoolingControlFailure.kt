package com.aqua.aqualight.application.devices.cooling.control

sealed interface DeviceCoolingControlFailure {
    data object Unsupported : DeviceCoolingControlFailure
    data object Unavailable : DeviceCoolingControlFailure
    data object NotConnected : DeviceCoolingControlFailure
    data object Rejected : DeviceCoolingControlFailure
    data object InvalidData : DeviceCoolingControlFailure
}
