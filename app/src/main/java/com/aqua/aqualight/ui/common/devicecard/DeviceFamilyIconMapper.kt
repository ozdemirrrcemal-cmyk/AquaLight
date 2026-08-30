package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceFamily

object DeviceFamilyIconMapper {

    @DrawableRes
    fun iconFor(family: DeviceFamily): Int {
        return when (family) {
            DeviceFamily.LIGHT -> R.drawable.ic_device_light
            DeviceFamily.TIMER -> R.drawable.ic_device_timer
            DeviceFamily.DOSING -> R.drawable.img_dosing_pump_4ch
            DeviceFamily.COOLING -> R.drawable.img_device_cooling_fan
            DeviceFamily.UNKNOWN -> R.drawable.ic_device_aqua_ster
        }
    }

    @DrawableRes
    fun iconFor(family: OwnerDeviceFamily): Int {
        return when (family) {
            OwnerDeviceFamily.LIGHT -> R.drawable.ic_device_light
            OwnerDeviceFamily.TIMER -> R.drawable.ic_device_timer
            OwnerDeviceFamily.DOSING -> R.drawable.img_dosing_pump_4ch
            OwnerDeviceFamily.COOLING -> R.drawable.img_device_cooling_fan
            OwnerDeviceFamily.UNKNOWN -> R.drawable.ic_device_aqua_ster
        }
    }
}
