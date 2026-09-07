package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot

@Composable
internal fun CoolingSystemFanCard(
    telemetry: DeviceCoolingTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    val fan = telemetry.fan
    CoolingSystemStatusSection(
        title = stringResource(R.string.device_cooling_system_status_fan_title),
        visuals = visuals,
        tone = fan?.pwmOutputHealth?.toStatusTone() ?: CoolingSystemStatusTone.NEUTRAL
    ) {
        if (fan == null) {
            CoolingSystemStatusParagraph(
                text = stringResource(R.string.device_cooling_system_status_unavailable_description),
                visuals = visuals
            )
        } else {
            CoolingSystemFanOutputDetails(fan = fan, visuals = visuals)
            CoolingSystemFanFeedbackDetails(fan = fan, visuals = visuals)
        }
    }
}

@Composable
private fun CoolingSystemFanOutputDetails(
    fan: DeviceCoolingFanTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_fan_pwm_output),
        value = stringResource(fan.pwmOutputHealth.toStatusTextRes()),
        visuals = visuals,
        tone = fan.pwmOutputHealth.toStatusTone()
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_fan_target),
        value = coolingRuntimePercentText(fan.targetPercent),
        visuals = visuals
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_fan_applied),
        value = coolingRuntimePercentText(fan.outputPercent),
        visuals = visuals
    )
}

@Composable
private fun CoolingSystemFanFeedbackDetails(
    fan: DeviceCoolingFanTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_fan_rotation_verification),
        value = stringResource(fan.physicalHealth.toStatusTextRes()),
        visuals = visuals,
        tone = fan.physicalHealth.toStatusTone()
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_fan_rpm),
        value = coolingRpmText(fan),
        visuals = visuals
    )
    if (!fan.rpmAvailable) {
        CoolingSystemStatusParagraph(
            text = stringResource(
                R.string.device_cooling_system_status_fan_feedback_explanation
            ),
            visuals = visuals
        )
    }
}

@Composable
private fun coolingRpmText(fan: DeviceCoolingFanTelemetrySnapshot): String = when {
    !fan.rpmAvailable -> stringResource(
        R.string.device_cooling_system_status_fan_rpm_not_supported
    )
    fan.rpm != null -> stringResource(
        R.string.device_cooling_system_status_fan_rpm_value_format,
        fan.rpm
    )
    else -> stringResource(R.string.device_cooling_value_unavailable)
}

private fun DeviceCoolingFanHealth.toStatusTone(): CoolingSystemStatusTone = when (this) {
    DeviceCoolingFanHealth.HARDWARE_FAULT -> CoolingSystemStatusTone.DANGER
    DeviceCoolingFanHealth.UNVERIFIED,
    DeviceCoolingFanHealth.UNKNOWN -> CoolingSystemStatusTone.NEUTRAL
}
