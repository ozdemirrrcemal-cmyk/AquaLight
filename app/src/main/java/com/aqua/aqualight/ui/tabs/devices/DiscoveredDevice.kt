package com.aqua.aqualight.ui.tabs.devices

data class DiscoveredDevice(
    val id: Long,                 // Sağda göstereceğimiz ID
    val aquaName: String,         // Soldaki pill
    val name: String,             // Ortadaki isim
    val ip: String,               // İçeride kullanacağız (HTTP vs.)
    val firmwareBuild: String? = null
)