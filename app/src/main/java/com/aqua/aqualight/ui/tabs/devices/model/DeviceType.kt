package com.aqua.aqualight.ui.tabs.devices.model

import com.aqua.aqualight.R

enum class DeviceType(val iconRes: Int) {
    DOSER(R.drawable.ic_device_doser),
    LIGHT(R.drawable.ic_device_light),
    WIFI_HUB(R.drawable.ic_device_wifi_hub),
    TIMER(R.drawable.ic_device_timer),
    TEMPERATURE(R.drawable.ic_device_temperature),
    CO2(R.drawable.ic_device_co2),
    MAIN(R.drawable.ic_device_aqua_ster),
    UNKNOWN(R.drawable.ic_device_aqua_ster);

    companion object {
        fun fromName(name: String?): DeviceType {
            if (name.isNullOrBlank()) return UNKNOWN
            val key = name.trim().lowercase()
            return when {
                listOf("doser","dosing","dose","pump","liquid").any { key.contains(it) } -> DOSER
                listOf("light","wrgb","rgb","led","lamp","shade").any { key.contains(it) } -> LIGHT
                listOf("hub","gateway","wifi","bridge","controller").any { key.contains(it) } -> WIFI_HUB
                listOf("timer","socket","plug","smartplug","switch").any { key.contains(it) } -> TIMER
                listOf("temp","temperature","fan","cooler","heater").any { key.contains(it) } -> TEMPERATURE
                listOf("co2","regulator","solenoid").any { key.contains(it) } -> CO2
                listOf("aqua","aquaster","master","main").any { key.contains(it) } -> MAIN
                else -> UNKNOWN
            }
        }
    }
}