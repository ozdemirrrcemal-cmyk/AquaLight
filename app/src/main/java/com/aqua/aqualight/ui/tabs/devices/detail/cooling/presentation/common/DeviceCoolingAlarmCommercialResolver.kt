package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode

/** Maps firmware-owned alarm identities to localized customer copy. */
@StringRes
internal fun DeviceCoolingAlarmCode.toCommercialCoolingAlarmMessageRes(): Int? = when (this) {
    DeviceCoolingAlarmCode.WATER_SENSOR_FAULT -> R.string.device_cooling_alarm_water_sensor_fault
    DeviceCoolingAlarmCode.AMBIENT_SENSOR_FAULT -> R.string.device_cooling_alarm_ambient_sensor_fault
    DeviceCoolingAlarmCode.FAN_HARDWARE_FAULT -> R.string.device_cooling_alarm_fan_hardware_fault
    DeviceCoolingAlarmCode.CLOCK_UNSYNCED -> R.string.device_cooling_alarm_clock_unsynced
    DeviceCoolingAlarmCode.HISTORY_STORAGE_FAULT -> R.string.device_cooling_alarm_history_storage_fault
    DeviceCoolingAlarmCode.CONFIG_STORAGE_FAULT -> R.string.device_cooling_alarm_config_storage_fault
    DeviceCoolingAlarmCode.UNKNOWN -> null
}
