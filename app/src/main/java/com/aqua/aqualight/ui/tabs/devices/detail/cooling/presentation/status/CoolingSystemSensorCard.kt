package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorKind
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorReadingHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot

@Composable
internal fun CoolingSystemSensorsCard(
    telemetry: DeviceCoolingTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusSection(
        title = stringResource(R.string.device_cooling_system_status_sensors_title),
        visuals = visuals,
        tone = telemetry.sensorHealth.toStatusTone()
    ) {
        if (telemetry.sensors.isEmpty()) {
            CoolingSystemStatusParagraph(
                text = stringResource(R.string.device_cooling_system_status_unavailable_description),
                visuals = visuals
            )
        } else {
            telemetry.sensors.forEachIndexed { index, sensor ->
                if (index > 0) CoolingSystemStatusDivider(visuals)
                CoolingSystemSensorDetails(sensor, telemetry, visuals)
            }
        }
    }
}

@Composable
private fun CoolingSystemSensorDetails(
    sensor: DeviceCoolingSensorTelemetrySnapshot,
    telemetry: DeviceCoolingTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    val tone = sensor.toStatusTone(telemetry)
    CoolingSystemStatusParagraph(
        text = stringResource(sensor.kind.toStatusTitleRes()),
        visuals = visuals,
        tone = tone
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_sensor_health),
        value = stringResource(sensor.health.toStatusTextRes()),
        visuals = visuals,
        tone = tone
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_sensor_present),
        value = coolingStatusBooleanText(sensor.present),
        visuals = visuals
    )
    CoolingSystemSensorReadings(sensor = sensor, visuals = visuals)
}

@Composable
private fun CoolingSystemSensorReadings(
    sensor: DeviceCoolingSensorTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_temperature),
        value = if (sensor.readingValid && sensor.temperatureC != null) {
            stringResource(R.string.device_cooling_temperature_value_format, sensor.temperatureC)
        } else {
            stringResource(R.string.device_cooling_value_unavailable)
        },
        visuals = visuals
    )
    if (sensor.kind == DeviceCoolingSensorKind.AMBIENT) {
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_humidity),
            value = if (sensor.readingValid && sensor.humidityPercent != null) {
                stringResource(R.string.device_cooling_humidity_value_format, sensor.humidityPercent)
            } else {
                stringResource(R.string.device_cooling_value_unavailable)
            },
            visuals = visuals
        )
    }
}

private fun DeviceCoolingSensorTelemetrySnapshot.toStatusTone(
    telemetry: DeviceCoolingTelemetrySnapshot
): CoolingSystemStatusTone {
    if (health == DeviceCoolingSensorReadingHealth.OK) return CoolingSystemStatusTone.SUCCESS
    val alarmCode = when (kind) {
        DeviceCoolingSensorKind.WATER -> DeviceCoolingAlarmCode.WATER_SENSOR_FAULT
        DeviceCoolingSensorKind.AMBIENT -> DeviceCoolingAlarmCode.AMBIENT_SENSOR_FAULT
        DeviceCoolingSensorKind.UNKNOWN -> DeviceCoolingAlarmCode.UNKNOWN
    }
    return telemetry.alarms.firstOrNull { alarm ->
        alarm.active && alarm.code == alarmCode
    }?.severity?.toStatusTone() ?: CoolingSystemStatusTone.NEUTRAL
}

private fun DeviceCoolingSensorHealth.toStatusTone(): CoolingSystemStatusTone = when (this) {
    DeviceCoolingSensorHealth.OK -> CoolingSystemStatusTone.SUCCESS
    DeviceCoolingSensorHealth.WARNING -> CoolingSystemStatusTone.WARNING
    DeviceCoolingSensorHealth.CRITICAL -> CoolingSystemStatusTone.DANGER
    DeviceCoolingSensorHealth.UNKNOWN -> CoolingSystemStatusTone.NEUTRAL
}

@StringRes
private fun DeviceCoolingSensorKind.toStatusTitleRes(): Int = when (this) {
    DeviceCoolingSensorKind.WATER -> R.string.device_cooling_system_status_water_sensor
    DeviceCoolingSensorKind.AMBIENT -> R.string.device_cooling_system_status_ambient_sensor
    DeviceCoolingSensorKind.UNKNOWN -> R.string.device_cooling_system_status_unknown_sensor
}
