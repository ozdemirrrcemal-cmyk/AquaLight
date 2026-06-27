package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.model.DeviceFamily

object DeviceFamilyIconMapper {

    @DrawableRes
    fun iconFor(family: DeviceFamily): Int {
        return when (family) {
            DeviceFamily.LIGHT -> R.drawable.ic_device_light
            DeviceFamily.TIMER -> R.drawable.ic_device_timer
            DeviceFamily.DOSING -> R.drawable.img_dosing_pump_4ch
            DeviceFamily.COOLING -> R.drawable.ic_device_temperature
            DeviceFamily.UNKNOWN -> R.drawable.ic_device_aqua_ster
        }
    }
}
