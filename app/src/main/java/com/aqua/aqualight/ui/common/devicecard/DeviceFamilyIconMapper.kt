package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

object DeviceFamilyIconMapper {
    @DrawableRes
    fun iconFor(family: OwnerDeviceFamily): Int {
        return when (family) {
            OwnerDeviceFamily.LIGHT -> R.drawable.ic_device_light
            OwnerDeviceFamily.TIMER -> R.drawable.ic_device_timer
            OwnerDeviceFamily.DOSING -> R.drawable.img_dosing_pump_4ch
            OwnerDeviceFamily.COOLING -> R.drawable.ic_device_cooling
            OwnerDeviceFamily.UNKNOWN -> R.drawable.ic_device_aqua_ster
        }
    }
}
