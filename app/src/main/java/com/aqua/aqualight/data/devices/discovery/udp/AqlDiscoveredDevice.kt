package com.aqua.aqualight.data.devices.discovery.udp

import com.aqua.aqualight.data.devices.model.DeviceSnapshot

data class AqlDiscoveredDevice(
    val snapshot: DeviceSnapshot,
    val sourceIp: String = "",
    val receivedAtMillis: Long
)
