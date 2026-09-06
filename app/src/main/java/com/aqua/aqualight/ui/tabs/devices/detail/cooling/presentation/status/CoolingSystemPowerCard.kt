package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPowerSource
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot

@Composable
internal fun CoolingSystemPowerCard(
    telemetry: DeviceCoolingTelemetrySnapshot,
    visuals: CoolingSystemStatusVisuals
) {
    val power = telemetry.power
    CoolingSystemStatusSection(
        title = stringResource(R.string.device_cooling_system_status_power_title),
        visuals = visuals,
        tone = when (power?.available) {
            true -> CoolingSystemStatusTone.SUCCESS
            false -> CoolingSystemStatusTone.WARNING
            null -> CoolingSystemStatusTone.NEUTRAL
        }
    ) {
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_power_source),
            value = when (power?.source) {
                DeviceCoolingPowerSource.ESTIMATED -> stringResource(
                    R.string.device_cooling_system_status_power_estimated
                )
                DeviceCoolingPowerSource.UNKNOWN,
                null -> stringResource(R.string.device_cooling_system_status_value_unknown)
            },
            visuals = visuals
        )
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_rated_power),
            value = coolingPowerText(power?.ratedPowerWatts),
            visuals = visuals
        )
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_current_power),
            value = coolingPowerText(power?.powerWatts),
            visuals = visuals
        )
        CoolingSystemStatusDetailRow(
            label = stringResource(R.string.device_cooling_system_status_daily_estimate),
            value = power?.estimatedKwhPerDay?.let { estimate ->
                stringResource(R.string.device_cooling_energy_value_format, estimate)
            } ?: stringResource(R.string.device_cooling_value_unavailable),
            visuals = visuals
        )
    }
}
