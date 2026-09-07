package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot

@Composable
internal fun CoolingSystemAlarmsCard(
    telemetry: DeviceCoolingTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusSection(
        title = stringResource(R.string.device_cooling_system_status_alarms_title),
        visuals = visuals,
        tone = telemetry.highestAlarmSeverity.toStatusTone()
    ) {
        if (telemetry.alarms.isEmpty()) {
            CoolingSystemStatusParagraph(
                text = stringResource(
                    R.string.device_cooling_system_status_no_alarms_description
                ),
                visuals = visuals,
                tone = CoolingSystemStatusTone.SUCCESS
            )
        } else {
            telemetry.alarms.forEachIndexed { index, alarm ->
                if (index > 0) CoolingSystemStatusDivider(visuals)
                CoolingSystemAlarmDetails(alarm = alarm, visuals = visuals)
            }
        }
    }
}

@Composable
private fun CoolingSystemAlarmDetails(
    alarm: DeviceCoolingAlarmSnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    val copy = alarm.code.toSystemStatusCopy()
    val tone = if (alarm.active) {
        alarm.severity.toStatusTone()
    } else {
        CoolingSystemStatusTone.NEUTRAL
    }
    CoolingSystemStatusParagraph(
        text = stringResource(copy.titleRes),
        visuals = visuals,
        tone = tone
    )
    CoolingSystemStatusParagraph(
        text = stringResource(copy.messageRes),
        visuals = visuals
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_alarm_severity),
        value = stringResource(alarm.severity.toStatusTextRes()),
        visuals = visuals,
        tone = tone
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_alarm_state),
        value = stringResource(
            if (alarm.active) R.string.device_cooling_system_status_alarm_active
            else R.string.device_cooling_system_status_alarm_cleared
        ),
        visuals = visuals,
        tone = tone
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_alarm_latched),
        value = coolingStatusBooleanText(alarm.latched),
        visuals = visuals
    )
    CoolingSystemAlarmTechnicalDetails(alarm = alarm, visuals = visuals)
}

@Composable
private fun CoolingSystemAlarmTechnicalDetails(
    alarm: DeviceCoolingAlarmSnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusParagraph(
        text = stringResource(
            R.string.device_cooling_system_status_alarm_code_format,
            alarm.diagnosticCode
        ),
        visuals = visuals
    )
    if (alarm.affectedKey.isNotBlank()) {
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_alarm_affected),
            value = alarm.affectedKey,
            visuals = visuals
        )
    }
    if (alarm.diagnosticReason.isNotBlank()) {
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_alarm_reason),
            value = alarm.diagnosticReason,
            visuals = visuals
        )
    }
}
