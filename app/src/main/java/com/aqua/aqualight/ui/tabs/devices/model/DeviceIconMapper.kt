package com.aqua.aqualight.ui.tabs.devices.model

import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType

object DeviceIconMapper {

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

    /** Compatibility overload for old card code. */
    fun iconFor(
        type: AquaDeviceType
    ): Int {
        val category = AquaDeviceCatalog.findByType(
            type = type
        )?.category ?: AquaDeviceCategory.UNKNOWN

        return iconFor(
            category = category
        )
    }
}
