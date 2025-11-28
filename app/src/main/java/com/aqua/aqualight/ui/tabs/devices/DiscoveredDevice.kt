package com.aqua.aqualight.ui.tabs.devices

data class DiscoveredDevice(
    val id: Long,                 // UDP'den gelen ID (0 ise geçersiz)
    val name: String,             // Name
    val ip: String,               // Cihazın IP adresi (ekranda göstermiyoruz ama lazım)
    val aquaName: String? = null, // AquaName
    val firmwareBuild: String? = null
)