package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

internal data class DeviceDosingCalibrationRoute(
    val deviceUid: String,
    val slotId: String,
    val pumpCount: Int,
    val channelNumber: Int,
    val channelTitle: String,
    val recalibration: Boolean = false
) {
    fun normalized(): DeviceDosingCalibrationRoute = copy(
        deviceUid = deviceUid.trim(),
        slotId = slotId.trim()
    )

    fun hasIdentity(): Boolean = deviceUid.isNotBlank() && slotId.isNotBlank()
}
