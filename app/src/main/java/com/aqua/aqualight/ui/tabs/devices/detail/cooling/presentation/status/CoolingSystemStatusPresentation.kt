package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPwmOutputHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorReadingHealth
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlReason
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState

internal enum class CoolingSystemStatusTone {
    SUCCESS,
    WARNING,
    DANGER,
    NEUTRAL
}

internal data class CoolingSystemStatusCopy(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

internal fun DeviceCoolingAlarmCode.toSystemStatusCopy(): CoolingSystemStatusCopy = when (this) {
    DeviceCoolingAlarmCode.WATER_SENSOR_FAULT -> CoolingSystemStatusCopy(
        R.string.device_cooling_system_status_alarm_water_sensor_title,
        R.string.device_cooling_system_status_alarm_water_sensor_message
    )
    DeviceCoolingAlarmCode.AMBIENT_SENSOR_FAULT -> CoolingSystemStatusCopy(
        R.string.device_cooling_system_status_alarm_ambient_sensor_title,
        R.string.device_cooling_system_status_alarm_ambient_sensor_message
    )
    DeviceCoolingAlarmCode.FAN_HARDWARE_FAULT -> CoolingSystemStatusCopy(
        R.string.device_cooling_system_status_alarm_fan_output_title,
        R.string.device_cooling_system_status_alarm_fan_output_message
    )
    DeviceCoolingAlarmCode.CLOCK_UNSYNCED -> CoolingSystemStatusCopy(
        R.string.device_cooling_system_status_alarm_clock_title,
        R.string.device_cooling_system_status_alarm_clock_message
    )
    DeviceCoolingAlarmCode.HISTORY_STORAGE_FAULT -> CoolingSystemStatusCopy(
        R.string.device_cooling_system_status_alarm_history_storage_title,
        R.string.device_cooling_system_status_alarm_history_storage_message
    )
    DeviceCoolingAlarmCode.CONFIG_STORAGE_FAULT -> CoolingSystemStatusCopy(
        R.string.device_cooling_system_status_alarm_config_storage_title,
        R.string.device_cooling_system_status_alarm_config_storage_message
    )
    DeviceCoolingAlarmCode.UNKNOWN -> CoolingSystemStatusCopy(
        R.string.device_cooling_system_status_alarm_unknown_title,
        R.string.device_cooling_system_status_alarm_unknown_message
    )
}

internal fun DeviceCoolingAlarmSeverity.toStatusTone(): CoolingSystemStatusTone = when (this) {
    DeviceCoolingAlarmSeverity.NONE -> CoolingSystemStatusTone.SUCCESS
    DeviceCoolingAlarmSeverity.WARNING -> CoolingSystemStatusTone.WARNING
    DeviceCoolingAlarmSeverity.CRITICAL -> CoolingSystemStatusTone.DANGER
    DeviceCoolingAlarmSeverity.UNKNOWN -> CoolingSystemStatusTone.NEUTRAL
}

@StringRes
internal fun DeviceCoolingAlarmSeverity.toStatusTextRes(): Int = when (this) {
    DeviceCoolingAlarmSeverity.NONE -> R.string.device_cooling_system_status_severity_none
    DeviceCoolingAlarmSeverity.WARNING -> R.string.device_cooling_system_status_severity_warning
    DeviceCoolingAlarmSeverity.CRITICAL -> R.string.device_cooling_system_status_severity_critical
    DeviceCoolingAlarmSeverity.UNKNOWN -> R.string.device_cooling_system_status_value_unknown
}

@StringRes
internal fun DeviceCoolingPwmOutputHealth.toStatusTextRes(): Int = when (this) {
    DeviceCoolingPwmOutputHealth.OK -> R.string.device_cooling_status_ready
    DeviceCoolingPwmOutputHealth.FAULT -> R.string.device_cooling_status_fault
    DeviceCoolingPwmOutputHealth.UNKNOWN -> R.string.device_cooling_system_status_value_unknown
}

internal fun DeviceCoolingPwmOutputHealth.toStatusTone(): CoolingSystemStatusTone = when (this) {
    DeviceCoolingPwmOutputHealth.OK -> CoolingSystemStatusTone.SUCCESS
    DeviceCoolingPwmOutputHealth.FAULT -> CoolingSystemStatusTone.DANGER
    DeviceCoolingPwmOutputHealth.UNKNOWN -> CoolingSystemStatusTone.NEUTRAL
}

@StringRes
internal fun DeviceCoolingFanHealth.toStatusTextRes(): Int = when (this) {
    DeviceCoolingFanHealth.UNVERIFIED ->
        R.string.device_cooling_system_status_fan_rotation_unverified
    DeviceCoolingFanHealth.HARDWARE_FAULT -> R.string.device_cooling_status_fault
    DeviceCoolingFanHealth.UNKNOWN -> R.string.device_cooling_system_status_value_unknown
}

@StringRes
internal fun DeviceCoolingSensorReadingHealth.toStatusTextRes(): Int = when (this) {
    DeviceCoolingSensorReadingHealth.OK -> R.string.device_cooling_status_ready
    DeviceCoolingSensorReadingHealth.MISSING ->
        R.string.device_cooling_system_status_sensor_missing
    DeviceCoolingSensorReadingHealth.TOPOLOGY_INVALID ->
        R.string.device_cooling_system_status_sensor_topology_invalid
    DeviceCoolingSensorReadingHealth.CRC_ERROR ->
        R.string.device_cooling_system_status_sensor_crc_error
    DeviceCoolingSensorReadingHealth.STALE ->
        R.string.device_cooling_system_status_sensor_stale
    DeviceCoolingSensorReadingHealth.OUT_OF_RANGE ->
        R.string.device_cooling_system_status_sensor_out_of_range
    DeviceCoolingSensorReadingHealth.WARMING_UP ->
        R.string.device_cooling_system_status_sensor_warming_up
    DeviceCoolingSensorReadingHealth.IO_ERROR ->
        R.string.device_cooling_system_status_sensor_io_error
    DeviceCoolingSensorReadingHealth.UNKNOWN ->
        R.string.device_cooling_system_status_value_unknown
}

@StringRes
internal fun DeviceCoolingControlMode.toStatusTextRes(): Int = when (this) {
    DeviceCoolingControlMode.AUTOMATIC -> R.string.device_cooling_mode_automatic
    DeviceCoolingControlMode.MANUAL -> R.string.device_cooling_mode_manual
    DeviceCoolingControlMode.PROGRAM -> R.string.device_cooling_mode_program
}

@StringRes
internal fun DeviceCoolingOperatingState.toStatusTextRes(): Int = when (this) {
    DeviceCoolingOperatingState.IDLE -> R.string.device_cooling_system_status_operating_idle
    DeviceCoolingOperatingState.COOLING -> R.string.device_cooling_system_status_operating_cooling
    DeviceCoolingOperatingState.MANUAL -> R.string.device_cooling_system_status_operating_manual
    DeviceCoolingOperatingState.PROGRAM -> R.string.device_cooling_system_status_operating_program
    DeviceCoolingOperatingState.FAULT -> R.string.device_cooling_system_status_operating_fault
}

@StringRes
internal fun DeviceCoolingControlReason.toStatusTextRes(): Int =
    coolingControlReasonText.getValue(this)

private val coolingControlReasonText = mapOf(
    DeviceCoolingControlReason.FAN_HARDWARE_FAULT to
        R.string.device_cooling_system_status_reason_fan_hardware_fault,
    DeviceCoolingControlReason.MANUAL_PERSISTENT_TARGET to
        R.string.device_cooling_system_status_reason_manual_target,
    DeviceCoolingControlReason.MANUAL_ZERO_OUTPUT to
        R.string.device_cooling_system_status_reason_manual_zero,
    DeviceCoolingControlReason.AUTOMATIC_CURVE to
        R.string.device_cooling_system_status_reason_automatic_curve,
    DeviceCoolingControlReason.BELOW_AUTOMATIC_START to
        R.string.device_cooling_system_status_reason_below_automatic_start,
    DeviceCoolingControlReason.PROGRAM_EMPTY to
        R.string.device_cooling_system_status_reason_program_empty,
    DeviceCoolingControlReason.CLOCK_UNSYNCED to
        R.string.device_cooling_system_status_reason_clock_unsynced,
    DeviceCoolingControlReason.NO_ACTIVE_PROGRAM_SLOT to
        R.string.device_cooling_system_status_reason_no_active_slot,
    DeviceCoolingControlReason.PROGRAM_SLOT_COOLING to
        R.string.device_cooling_system_status_reason_program_cooling,
    DeviceCoolingControlReason.PROGRAM_SLOT_BELOW_THRESHOLD to
        R.string.device_cooling_system_status_reason_program_below_threshold,
    DeviceCoolingControlReason.CONFIG_STORAGE_REJECTED to
        R.string.device_cooling_system_status_reason_config_storage,
    DeviceCoolingControlReason.WATER_SENSOR_MISSING to
        R.string.device_cooling_system_status_reason_water_missing,
    DeviceCoolingControlReason.WATER_SENSOR_TOPOLOGY_INVALID to
        R.string.device_cooling_system_status_reason_water_topology,
    DeviceCoolingControlReason.WATER_SENSOR_STALE to
        R.string.device_cooling_system_status_reason_water_stale,
    DeviceCoolingControlReason.WATER_SENSOR_INVALID to
        R.string.device_cooling_system_status_reason_water_invalid,
    DeviceCoolingControlReason.OTA_SAFE_MODE to
        R.string.device_cooling_system_status_reason_ota_safe_mode,
    DeviceCoolingControlReason.AUTOMATIC_PENDING_SENSOR_EVALUATION to
        R.string.device_cooling_system_status_reason_automatic_pending,
    DeviceCoolingControlReason.PROGRAM_PENDING_TIME_EVALUATION to
        R.string.device_cooling_system_status_reason_program_pending,
    DeviceCoolingControlReason.UNKNOWN to R.string.device_cooling_system_status_value_unknown
)
