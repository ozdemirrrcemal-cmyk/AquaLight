package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIcon
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIconKind
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState

@Composable
internal fun CoolingTemperatureCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val historyDescription = stringResource(R.string.device_cooling_view_history_description)
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.temperatureCardMinimumHeight)
            .semantics { contentDescription = historyDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            CoolingSectionHeader(
                title = stringResource(R.string.device_cooling_temperature_title),
                trailing = stringResource(R.string.device_cooling_temperature_history_caption),
                colors = colors,
                typography = typography
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    AquaCoolingDashboardGeometry.temperatureMetricGap
                ),
                verticalAlignment = Alignment.Top
            ) {
                CoolingTemperatureChart(
                    tankHistoryC = state.temperatureHistoryC,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                CoolingTemperatureMetricsPanel(
                    state = state,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

@Composable
private fun CoolingTemperatureMetricsPanel(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.width(AquaCoolingDashboardGeometry.temperatureMetricWidth),
        horizontalArrangement = Arrangement.spacedBy(
            AquaCoolingDashboardGeometry.temperatureMetricGap
        )
    ) {
        Box(
            modifier = Modifier
                .width(AquaCoolingDashboardGeometry.temperatureMetricDividerWidth)
                .height(AquaCoolingDashboardGeometry.temperatureMetricDividerHeight)
                .background(colors.outline.copy(alpha = AquaCoolingDashboardAlpha.divider))
        )
        Column(modifier = Modifier.weight(1f)) {
            CoolingTemperatureMetricRow(
                metric = CoolingTemperatureMetricModel(
                    icon = AquaCoolingDashboardIconKind.WATER,
                    label = stringResource(R.string.device_cooling_tank_temperature_label),
                    value = coolingTemperatureText(state.tankTemperatureC),
                    accent = true
                ),
                colors = colors,
                typography = typography
            )
            CoolingTemperatureMetricDivider(colors)
            CoolingTemperatureMetricRow(
                metric = CoolingTemperatureMetricModel(
                    icon = AquaCoolingDashboardIconKind.ROOM,
                    label = stringResource(R.string.device_cooling_room_temperature_label),
                    value = coolingTemperatureText(state.roomTemperatureC),
                    accent = false
                ),
                colors = colors,
                typography = typography
            )
            CoolingTemperatureMetricDivider(colors)
            CoolingTemperatureMetricRow(
                metric = CoolingTemperatureMetricModel(
                    icon = AquaCoolingDashboardIconKind.HUMIDITY,
                    label = stringResource(R.string.device_cooling_humidity_label),
                    value = coolingHumidityText(state.humidityPercent),
                    accent = false
                ),
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun CoolingTemperatureMetricRow(
    metric: CoolingTemperatureMetricModel,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.temperatureMetricRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            AquaCoolingDashboardGeometry.temperatureMetricIconGap
        )
    ) {
        AquaCoolingDashboardIcon(
            kind = metric.icon,
            tint = if (metric.accent) colors.accent else colors.primaryText,
            modifier = Modifier.size(AquaCoolingDashboardGeometry.temperatureMetricIconSize)
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = metric.label,
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = metric.value,
                style = typography.body.copy(
                    color = if (metric.accent) colors.accent else colors.primaryText,
                    fontSize = AquaCoolingDashboardTypography.temperatureMetricValueSize
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoolingTemperatureMetricDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AquaCoolingDashboardGeometry.temperatureMetricRowDividerInset)
            .height(AquaCoolingDashboardGeometry.temperatureMetricRowDividerHeight)
            .background(colors.outline.copy(alpha = AquaCoolingDashboardAlpha.divider))
    )
}
