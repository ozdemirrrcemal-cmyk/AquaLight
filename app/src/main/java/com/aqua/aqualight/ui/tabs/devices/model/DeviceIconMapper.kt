package com.aqua.aqualight.ui.tabs.devices.model

import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType

object DeviceIconMapper {

    fun iconFor(
        type: AquaDeviceType
    ): Int {
        return when (type) {
            AquaDeviceType.AQUA_LIGHT_001,
            AquaDeviceType.AQUA_LIGHT_002,
            AquaDeviceType.AQUA_LIGHT_003,
            AquaDeviceType.AQUA_LIGHT_004 -> {
                R.drawable.ic_device_light
            }

            AquaDeviceType.AQUA_TIMER_001,
            AquaDeviceType.AQUA_TIMER_002,
            AquaDeviceType.AQUA_TIMER_003,
            AquaDeviceType.AQUA_TIMER_004,
            AquaDeviceType.AQUA_TIMER_005 -> {
                R.drawable.ic_device_timer
            }

            AquaDeviceType.AQUA_COOL_001 -> {
                R.drawable.ic_device_temperature
            }

            AquaDeviceType.AQUA_CONTROL_001 -> {
                R.drawable.ic_device_wifi_hub
            }

            AquaDeviceType.UNKNOWN -> {
                R.drawable.ic_device_aqua_ster
            }
        }
    }
}