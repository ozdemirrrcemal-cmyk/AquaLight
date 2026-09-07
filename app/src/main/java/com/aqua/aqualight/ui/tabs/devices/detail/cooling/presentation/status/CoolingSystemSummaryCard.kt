package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot

private data class CoolingSystemSummaryCopy(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val tone: CoolingSystemStatusTone
)

@Composable
internal fun CoolingSystemSummaryCard(
    state: DeviceCoolingSystemStatusUiState,
    telemetry: DeviceCoolingTelemetrySnapshot?,
    visuals: CoolingSystemStatusVisuals
) {
    val severity = telemetry?.highestAlarmSeverity ?: DeviceCoolingAlarmSeverity.UNKNOWN
    val copy = coolingSystemSummaryCopy(online = state.online, severity = severity)
    CoolingSystemStatusSection(
        title = stringResource(copy.titleRes),
        visuals = visuals,
        tone = copy.tone
    ) {
        CoolingSystemStatusParagraph(
            text = stringResource(copy.messageRes),
            visuals = visuals,
            tone = copy.tone
        )
        CoolingSystemSummaryRows(
            state = state,
            telemetry = telemetry,
            severity = severity,
            visuals = visuals
        )
    }
}

@Composable
private fun CoolingSystemSummaryRows(
    state: DeviceCoolingSystemStatusUiState,
    telemetry: DeviceCoolingTelemetrySnapshot?,
    severity: DeviceCoolingAlarmSeverity,
    visuals: CoolingSystemStatusVisuals
) {
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_status_connection),
        value = stringResource(
            if (state.online) R.string.device_cooling_status_online
            else R.string.device_cooling_status_offline
        ),
        visuals = visuals,
        tone = if (state.online) {
            CoolingSystemStatusTone.SUCCESS
        } else {
            CoolingSystemStatusTone.NEUTRAL
        }
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_system_status_data_label),
        value = stringResource(
            when {
                state.stale -> R.string.device_cooling_system_status_data_last_known
                telemetry != null -> R.string.device_cooling_system_status_data_live
                else -> R.string.device_cooling_value_unavailable
            }
        ),
        visuals = visuals
    )
    CoolingSystemStatusDetailRow(
        label = stringResource(R.string.device_cooling_status_alarm),
        value = coolingAlarmCountText(telemetry?.activeAlarmCount),
        visuals = visuals,
        tone = severity.toStatusTone()
    )
}

private fun coolingSystemSummaryCopy(
    online: Boolean,
    severity: DeviceCoolingAlarmSeverity
): CoolingSystemSummaryCopy = when {
    !online -> CoolingSystemSummaryCopy(
        R.string.device_cooling_status_offline,
        R.string.device_cooling_system_status_offline_description,
        CoolingSystemStatusTone.NEUTRAL
    )
    severity == DeviceCoolingAlarmSeverity.NONE -> CoolingSystemSummaryCopy(
        R.string.device_cooling_system_status_healthy,
        R.string.device_cooling_system_status_healthy_description,
        CoolingSystemStatusTone.SUCCESS
    )
    severity == DeviceCoolingAlarmSeverity.WARNING -> CoolingSystemSummaryCopy(
        R.string.device_cooling_system_status_warning,
        R.string.device_cooling_system_status_warning_description,
        CoolingSystemStatusTone.WARNING
    )
    severity == DeviceCoolingAlarmSeverity.CRITICAL -> CoolingSystemSummaryCopy(
        R.string.device_cooling_system_status_critical,
        R.string.device_cooling_system_status_critical_description,
        CoolingSystemStatusTone.DANGER
    )
    else -> CoolingSystemSummaryCopy(
        R.string.device_cooling_system_status_unavailable,
        R.string.device_cooling_system_status_unavailable_description,
        CoolingSystemStatusTone.NEUTRAL
    )
}

@Composable
private fun coolingAlarmCountText(count: Int?): String = when {
    count == null -> stringResource(R.string.device_cooling_value_unavailable)
    count == 0 -> stringResource(R.string.device_cooling_status_no_active_alarms)
    else -> pluralStringResource(
        R.plurals.device_cooling_active_alarm_count,
        count,
        count
    )
}
