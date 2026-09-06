package com.aqua.aqualight.application.devices.cooling

/** Stable application projection of the firmware-owned Cooling telemetry contract. */
data class DeviceCoolingTelemetrySnapshot(
    val roomTemperatureC: Double?,
    val humidityPercent: Double?,
    val powerWatts: Double?,
    val estimatedKwhPerDay: Double?,
    val fanHealth: DeviceCoolingFanHealth,
    val sensorHealth: DeviceCoolingSensorHealth,
    val alarms: List<DeviceCoolingAlarmSnapshot>,
    /** Firmware healthSummary value; never recomputed from [alarms]. */
    val activeAlarmCount: Int? = null,
    val highestAlarmSeverity: DeviceCoolingAlarmSeverity = DeviceCoolingAlarmSeverity.UNKNOWN,
    val waterTemperatureSample: DeviceCoolingWaterTemperatureSample? = null
) {
    val activeAlarms: List<DeviceCoolingAlarmSnapshot>
        get() = alarms.filter(DeviceCoolingAlarmSnapshot::active)
}

/** Identity and timing are passed through from the firmware; Android never derives the value. */
data class DeviceCoolingWaterTemperatureSample(
    val inputSampleSequence: Long,
    val sampledAtUptimeMillis: Long,
    val evaluatedAtUptimeMillis: Long,
    val timeGeneration: Long,
    val temperatureC: Double
) {
    init {
        require(inputSampleSequence > 0L)
        require(sampledAtUptimeMillis >= 0L)
        require(timeGeneration >= 0L)
        require(temperatureC.isFinite())
    }
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
    NONE,
    WARNING,
    CRITICAL,
    UNKNOWN
}
