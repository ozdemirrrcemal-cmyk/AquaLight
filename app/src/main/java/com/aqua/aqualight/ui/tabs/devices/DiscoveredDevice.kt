package com.aqua.aqualight.ui.tabs.devices

data class DiscoveredDevice(
    val id: Long = 0L,
    val name: String,
    val ip: String,
    val aquaName: String? = null,
    val firmwareBuild: String? = null
)