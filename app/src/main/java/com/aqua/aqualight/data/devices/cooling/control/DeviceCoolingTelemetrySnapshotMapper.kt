package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Alarm
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Telemetry

/** Maps firmware telemetry without deriving device-owned health or alarm state on Android. */
internal object DeviceCoolingTelemetrySnapshotMapper {
    fun map(telemetry: DeviceCoolingV1Telemetry): DeviceCoolingTelemetrySnapshot {
        val ambient = telemetry.sensors.firstOrNull { sensor ->
            sensor.sensorKey == DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY
        }
        return DeviceCoolingTelemetrySnapshot(
            roomTemperatureC = ambient?.temperatureC.takeIf { ambient?.readingValid == true },
            humidityPercent = ambient?.humidityPercent.takeIf { ambient?.readingValid == true },
            powerWatts = telemetry.power.powerWatts,
            estimatedKwhPerDay = telemetry.power.estimatedKwhPerDay,
            fanHealth = when (telemetry.healthSummary.fanHealth) {
                "UNVERIFIED" -> DeviceCoolingFanHealth.UNVERIFIED
                "HARDWARE_FAULT" -> DeviceCoolingFanHealth.HARDWARE_FAULT
                else -> DeviceCoolingFanHealth.UNKNOWN
            },
            sensorHealth = when (telemetry.healthSummary.sensorHealth) {
                "OK" -> DeviceCoolingSensorHealth.OK
                "WARNING" -> DeviceCoolingSensorHealth.WARNING
                "CRITICAL" -> DeviceCoolingSensorHealth.CRITICAL
                else -> DeviceCoolingSensorHealth.UNKNOWN
            },
            alarms = telemetry.alarms.map(::toApplicationAlarm),
            activeAlarmCount = telemetry.healthSummary.activeAlarmCount,
            highestAlarmSeverity = toApplicationAlarmSeverity(
                telemetry.healthSummary.highestAlarmSeverity
            )
        )
    }

    private fun toApplicationAlarm(alarm: DeviceCoolingV1Alarm): DeviceCoolingAlarmSnapshot =
        DeviceCoolingAlarmSnapshot(
            code = when (alarm.code) {
                "WATER_SENSOR_FAULT" -> DeviceCoolingAlarmCode.WATER_SENSOR_FAULT
                "AMBIENT_SENSOR_FAULT" -> DeviceCoolingAlarmCode.AMBIENT_SENSOR_FAULT
                "FAN_HARDWARE_FAULT" -> DeviceCoolingAlarmCode.FAN_HARDWARE_FAULT
                "CLOCK_UNSYNCED" -> DeviceCoolingAlarmCode.CLOCK_UNSYNCED
                "HISTORY_STORAGE_FAULT" -> DeviceCoolingAlarmCode.HISTORY_STORAGE_FAULT
                "CONFIG_STORAGE_FAULT" -> DeviceCoolingAlarmCode.CONFIG_STORAGE_FAULT
                else -> DeviceCoolingAlarmCode.UNKNOWN
            },
            severity = toApplicationAlarmSeverity(alarm.severity),
            active = alarm.active,
            latched = alarm.latched
        )

    private fun toApplicationAlarmSeverity(value: String): DeviceCoolingAlarmSeverity =
        when (value) {
            "NONE" -> DeviceCoolingAlarmSeverity.NONE
            "WARNING" -> DeviceCoolingAlarmSeverity.WARNING
            "CRITICAL" -> DeviceCoolingAlarmSeverity.CRITICAL
            else -> DeviceCoolingAlarmSeverity.UNKNOWN
        }
}
