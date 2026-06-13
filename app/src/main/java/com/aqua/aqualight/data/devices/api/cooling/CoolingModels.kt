package com.aqua.aqualight.data.devices.api.cooling

data class CoolingStatus(
    val enabled: Boolean = false,
    val fanCount: Int = 0,
    val enabledFanCount: Int = 0,
    val currentTemperatureCelsius: Double? = null,
    val fanStartTemperatureCelsius: Int = 30,
    val fanFullSpeedTemperatureCelsius: Int = 50
)

data class CoolingSettings(
    val enabled: Boolean,
    val fanStartTemperatureCelsius: Int,
    val fanFullSpeedTemperatureCelsius: Int
)
