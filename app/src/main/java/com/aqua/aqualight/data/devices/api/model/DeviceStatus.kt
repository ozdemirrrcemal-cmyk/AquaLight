package com.aqua.aqualight.data.devices.api.model

data class DeviceStatus(
    val isOnline: Boolean = false,
    val deviceTimeText: String = "",
    val firmwareVersion: String = "",
    val uptimeSeconds: Long? = null,
    val signalStrength: Int? = null,
    val temperatureCelsius: Double? = null,
    val rawStatus: String = ""
)
