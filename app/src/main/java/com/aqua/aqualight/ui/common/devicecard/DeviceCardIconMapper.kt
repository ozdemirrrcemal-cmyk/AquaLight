package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory

object DeviceCardIconMapper {

    @DrawableRes
    fun iconFor(
        category: AquaDeviceCategory
    ): Int {
        return when (category) {
            AquaDeviceCategory.LIGHT -> {
                R.drawable.ic_device_light
            }

            AquaDeviceCategory.TIMER -> {
                R.drawable.ic_device_timer
            }

            AquaDeviceCategory.DOSING -> {
                R.drawable.img_dosing_pump_4ch
            }

            AquaDeviceCategory.COOLING -> {
                R.drawable.ic_device_temperature
            }

            AquaDeviceCategory.CONTROLLER -> {
                R.drawable.ic_device_wifi_hub
            }

            AquaDeviceCategory.UNKNOWN -> {
                R.drawable.ic_device_aqua_ster
            }
        }
    }
}
