package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.R
import java.util.Locale

object DeviceIconResolver {

    fun resolve(
        aquaName: String
    ): Int {

        val key =
            aquaName
                .trim()
                .lowercase(Locale.ROOT)

        return when {

            // DOSER
            key.contains("doser") ||
            key.contains("dosing") ||
            key.contains("dose") ||
            key.contains("pump") ||
            key.contains("liquid") -> {

                R.drawable.ic_device_doser
            }

            // LIGHT
            key.contains("light") ||
            key.contains("wrgb") ||
            key.contains("rgb") ||
            key.contains("led") ||
            key.contains("lamp") ||
            key.contains("shade") -> {

                R.drawable.ic_device_light
            }

            // WIFI HUB
            key.contains("hub") ||
            key.contains("gateway") ||
            key.contains("wifi") ||
            key.contains("bridge") ||
            key.contains("controller") -> {

                R.drawable.ic_device_wifi_hub
            }

            // TIMER
            key.contains("timer") ||
            key.contains("socket") ||
            key.contains("plug") ||
            key.contains("smartplug") ||
            key.contains("switch") -> {

                R.drawable.ic_device_timer
            }

            // TEMPERATURE
            key.contains("temp") ||
            key.contains("temperature") ||
            key.contains("fan") ||
            key.contains("cooler") ||
            key.contains("heater") -> {

                R.drawable.ic_device_temperature
            }

            // CO2
            key.contains("co2") ||
            key.contains("regulator") ||
            key.contains("solenoid") -> {

                R.drawable.ic_device_co2
            }

            // MAIN
            key.contains("aqua") ||
            key.contains("aquaster") ||
            key.contains("master") ||
            key.contains("main") -> {

                R.drawable.ic_device_aqua_ster
            }

            else -> {

                R.drawable.ic_device_aqua_ster
            }
        }
    }
}