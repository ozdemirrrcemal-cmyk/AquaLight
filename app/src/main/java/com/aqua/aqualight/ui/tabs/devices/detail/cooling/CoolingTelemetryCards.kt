package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun CoolingPowerCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    AquaCoolingDashboardCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingDashboardGeometry.statusCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_power_title),
                style = typography.title
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
            ) {
                CoolingPowerGlyph(colors)
                BasicText(
                    text = coolingPowerText(state.powerWatts),
                    style = typography.title.copy(
                        color = colors.primaryText,
                        fontSize = AquaCoolingDashboardTypography.metricValueSize
                    ),
                    maxLines = 1
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.statusRowGap)) {
                BasicText(
                    text = stringResource(R.string.device_cooling_estimated_consumption),
                    style = typography.micro.copy(color = colors.secondaryText)
                )
                BasicText(
                    text = coolingEnergyText(state.estimatedKwhPerDay),
                    style = typography.body.copy(color = colors.primaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CoolingPowerGlyph(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(colors.mediaSurface),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.57f, 0f)
                lineTo(size.width * 0.25f, size.height * 0.53f)
                lineTo(size.width * 0.48f, size.height * 0.53f)
                lineTo(size.width * 0.40f, size.height)
                lineTo(size.width * 0.78f, size.height * 0.40f)
                lineTo(size.width * 0.54f, size.height * 0.40f)
                close()
            }
            drawPath(path = path, color = colors.primaryText)
        }
    }
}

@Composable
internal fun CoolingStatusCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val ready = stringResource(R.string.device_cooling_status_ready)
    val unavailable = stringResource(R.string.device_cooling_value_unavailable)
    AquaCoolingDashboardCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingDashboardGeometry.statusCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.statusRowGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_status_title),
                style = typography.title
            )
            CoolingStatusRow(
                label = stringResource(R.string.device_cooling_status_fan),
                value = if (state.fanOutputCount > 0) ready else unavailable,
                positive = state.fanOutputCount > 0,
                colors = colors,
                typography = typography
            )
            CoolingStatusRow(
                label = stringResource(R.string.device_cooling_status_sensors),
                value = if (state.temperatureSensorCount > 0) ready else unavailable,
                positive = state.temperatureSensorCount > 0,
                colors = colors,
                typography = typography
            )
            CoolingStatusRow(
                label = stringResource(R.string.device_cooling_status_connection),
                value = if (state.contentEnabled) {
                    stringResource(R.string.device_cooling_status_online)
                } else {
                    stringResource(R.string.device_cooling_status_offline)
                },
                positive = state.contentEnabled,
                colors = colors,
                typography = typography
            )
            CoolingStatusRow(
                label = stringResource(R.string.device_cooling_status_alarm),
                value = unavailable,
                positive = false,
                colors = colors,
                typography = typography,
                showDot = false
            )
        }
    }
}

@Composable
private fun CoolingStatusRow(
    label: String,
    value: String,
    positive: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    showDot: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = typography.caption.copy(color = colors.secondaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.statusValueGap)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(AquaCoolingDashboardGeometry.statusDotSize)
                        .clip(CircleShape)
                        .background(
                            (if (positive) {
                                AquaCoolingDashboardPalette.success
                            } else {
                                colors.secondaryText
                            }).copy(alpha = AquaCoolingDashboardAlpha.statusDot)
                        )
                )
            }
            BasicText(
                text = value,
                style = typography.micro.copy(
                    color = if (positive) {
                        AquaCoolingDashboardPalette.success
                    } else {
                        colors.secondaryText
                    }
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
