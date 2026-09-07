package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPowerSource
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPowerTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPwmOutputHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorKind
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorReadingHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingWaterTemperatureSample
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Alarm
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1FanTelemetry
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1PowerTelemetry
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1SensorTelemetry
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Telemetry

/** Maps firmware telemetry without deriving device-owned health or alarm state on Android. */
internal object DeviceCoolingTelemetrySnapshotMapper {
    fun map(
        telemetry: DeviceCoolingV1Telemetry,
        rpmAvailable: Boolean
    ): DeviceCoolingTelemetrySnapshot {
        val water = telemetry.sensors.firstOrNull { sensor ->
            sensor.sensorKey == DeviceCoolingV1Contract.WATER_SENSOR_KEY
        }
        val ambient = telemetry.sensors.firstOrNull { sensor ->
            sensor.sensorKey == DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY
        }
        return DeviceCoolingTelemetrySnapshot(
            roomTemperatureC = ambient?.temperatureC.takeIf { ambient?.readingValid == true },
            humidityPercent = ambient?.humidityPercent.takeIf { ambient?.readingValid == true },
            powerWatts = telemetry.power.powerWatts,
            estimatedKwhPerDay = telemetry.power.estimatedKwhPerDay,
            fanHealth = telemetry.healthSummary.fanHealth.toApplicationFanHealth(),
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
            ),
            waterTemperatureSample = toWaterTemperatureSample(telemetry, water),
            fan = telemetry.fan.toApplicationFan(rpmAvailable),
            sensors = telemetry.sensors.map(::toApplicationSensor),
            power = telemetry.power.toApplicationPower()
        )
    }

    private fun toWaterTemperatureSample(
        telemetry: DeviceCoolingV1Telemetry,
        sensor: DeviceCoolingV1SensorTelemetry?
    ): DeviceCoolingWaterTemperatureSample? = sensor
        ?.takeIf(DeviceCoolingV1SensorTelemetry::readingValid)
        ?.let { validSensor ->
            validSensor.temperatureC?.takeIf(Double::isFinite)?.let { validTemperatureC ->
                DeviceCoolingWaterTemperatureSample(
                    inputSampleSequence = telemetry.inputSampleSequence,
                    sampledAtUptimeMillis = validSensor.sampledAtMs,
                    evaluatedAtUptimeMillis = telemetry.evaluatedAtMs,
                    timeGeneration = telemetry.timeGeneration,
                    temperatureC = validTemperatureC
                )
            }
        }

    private fun DeviceCoolingV1FanTelemetry.toApplicationFan(
        rpmAvailable: Boolean
    ): DeviceCoolingFanTelemetrySnapshot = DeviceCoolingFanTelemetrySnapshot(
        targetPercent = targetPercent,
        outputPercent = outputPercent,
        rpm = rpm,
        rpmAvailable = rpmAvailable,
        pwmOutputHealth = when (pwmOutputHealth) {
            "OK" -> DeviceCoolingPwmOutputHealth.OK
            "FAULT" -> DeviceCoolingPwmOutputHealth.FAULT
            else -> DeviceCoolingPwmOutputHealth.UNKNOWN
        },
        physicalHealth = health.toApplicationFanHealth()
    )

    private fun DeviceCoolingV1PowerTelemetry.toApplicationPower():
        DeviceCoolingPowerTelemetrySnapshot = DeviceCoolingPowerTelemetrySnapshot(
        source = when (source) {
            "ESTIMATED" -> DeviceCoolingPowerSource.ESTIMATED
            else -> DeviceCoolingPowerSource.UNKNOWN
        },
        available = available,
        ratedPowerWatts = ratedPowerWatts,
        powerWatts = powerWatts,
        estimatedKwhPerDay = estimatedKwhPerDay
    )

    private fun toApplicationSensor(
        sensor: DeviceCoolingV1SensorTelemetry
    ): DeviceCoolingSensorTelemetrySnapshot = DeviceCoolingSensorTelemetrySnapshot(
        kind = when (sensor.sensorKey) {
            DeviceCoolingV1Contract.WATER_SENSOR_KEY -> DeviceCoolingSensorKind.WATER
            DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY -> DeviceCoolingSensorKind.AMBIENT
            else -> DeviceCoolingSensorKind.UNKNOWN
        },
        present = sensor.present,
        health = when (sensor.health) {
            "OK" -> DeviceCoolingSensorReadingHealth.OK
            "MISSING" -> DeviceCoolingSensorReadingHealth.MISSING
            "TOPOLOGY_INVALID" -> DeviceCoolingSensorReadingHealth.TOPOLOGY_INVALID
            "CRC_ERROR" -> DeviceCoolingSensorReadingHealth.CRC_ERROR
            "STALE" -> DeviceCoolingSensorReadingHealth.STALE
            "OUT_OF_RANGE" -> DeviceCoolingSensorReadingHealth.OUT_OF_RANGE
            "WARMING_UP" -> DeviceCoolingSensorReadingHealth.WARMING_UP
            "IO_ERROR" -> DeviceCoolingSensorReadingHealth.IO_ERROR
            else -> DeviceCoolingSensorReadingHealth.UNKNOWN
        },
        readingValid = sensor.readingValid,
        temperatureC = sensor.temperatureC,
        humidityPercent = sensor.humidityPercent,
        sampledAtUptimeMillis = sensor.sampledAtMs
    )

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
            latched = alarm.latched,
            diagnosticCode = alarm.code,
            affectedKey = alarm.affectedKey,
            diagnosticReason = alarm.reason
        )

    private fun toApplicationAlarmSeverity(value: String): DeviceCoolingAlarmSeverity =
        when (value) {
            "NONE" -> DeviceCoolingAlarmSeverity.NONE
            "WARNING" -> DeviceCoolingAlarmSeverity.WARNING
            "CRITICAL" -> DeviceCoolingAlarmSeverity.CRITICAL
            else -> DeviceCoolingAlarmSeverity.UNKNOWN
        }

    private fun String.toApplicationFanHealth(): DeviceCoolingFanHealth = when (this) {
        "UNVERIFIED" -> DeviceCoolingFanHealth.UNVERIFIED
        "HARDWARE_FAULT" -> DeviceCoolingFanHealth.HARDWARE_FAULT
        else -> DeviceCoolingFanHealth.UNKNOWN
    }
}
