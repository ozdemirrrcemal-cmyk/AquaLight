package com.aqua.aqualight.ui.tabs.devices

data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val aquaName: String? = null,
    val firmwareBuild: String? = null
)