package com.aqua.aqualight.application.devices.cooling

/** Stable application projection of the firmware-owned Cooling telemetry contract. */
data class DeviceCoolingTelemetrySnapshot(
    val roomTemperatureC: Double?,
    val humidityPercent: Double?,
    val powerWatts: Double?,
    val estimatedKwhPerDay: Double?,
    val fanHealth: DeviceCoolingFanHealth,
    val sensorHealth: DeviceCoolingSensorHealth,
    val alarms: List<DeviceCoolingAlarmSnapshot>
) {
    val activeAlarms: List<DeviceCoolingAlarmSnapshot>
        get() = alarms.filter(DeviceCoolingAlarmSnapshot::active)
}

enum class DeviceCoolingFanHealth {
    UNVERIFIED,
    HARDWARE_FAULT,
    UNKNOWN
}

enum class DeviceCoolingSensorHealth {
    OK,
    WARNING,
    CRITICAL,
    UNKNOWN
}

data class DeviceCoolingAlarmSnapshot(
    val code: DeviceCoolingAlarmCode,
    val severity: DeviceCoolingAlarmSeverity,
    val active: Boolean,
    val latched: Boolean
)

enum class DeviceCoolingAlarmCode {
    WATER_SENSOR_FAULT,
    AMBIENT_SENSOR_FAULT,
    FAN_HARDWARE_FAULT,
    CLOCK_UNSYNCED,
    HISTORY_STORAGE_FAULT,
    CONFIG_STORAGE_FAULT,
    UNKNOWN
}

enum class DeviceCoolingAlarmSeverity {
    WARNING,
    CRITICAL,
    UNKNOWN
}
