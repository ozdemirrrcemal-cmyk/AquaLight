package com.aqua.aqualight.ui.tabs.devices.detail.cooling.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun CoolingSectionHeader(
    title: String,
    trailing: String?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = typography.title.copy(color = colors.primaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.let { value ->
            BasicText(
                text = value,
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun CoolingMetric(
    label: String,
    value: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    primary: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)) {
        BasicText(
            text = label,
            style = typography.micro.copy(color = colors.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            style = typography.body.copy(
                color = if (primary) colors.accent else colors.primaryText,
                fontSize = if (primary) {
                    AquaCoolingDashboardTypography.metricValueSize
                } else {
                    AquaCoolingDashboardTypography.compactValueSize
                }
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun coolingTemperatureText(value: Double?): String = value?.let { temperature ->
    stringResource(R.string.device_cooling_temperature_value_format, temperature)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
internal fun coolingHumidityText(value: Double?): String = value?.let { humidity ->
    stringResource(R.string.device_cooling_humidity_value_format, humidity)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
internal fun coolingPowerText(value: Double?): String = value?.let { power ->
    stringResource(R.string.device_cooling_power_value_format, power)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
internal fun coolingEnergyText(value: Double?): String = value?.let { energy ->
    stringResource(R.string.device_cooling_energy_value_format, energy)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
internal fun coolingModeLabel(mode: CoolingControlMode): String = when (mode) {
    CoolingControlMode.AUTOMATIC -> stringResource(R.string.device_cooling_mode_automatic)
    CoolingControlMode.MANUAL -> stringResource(R.string.device_cooling_mode_manual)
    CoolingControlMode.PROGRAM -> stringResource(R.string.device_cooling_mode_program)
}
