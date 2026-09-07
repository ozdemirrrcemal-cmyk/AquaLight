package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIcon
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIconKind
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingHealthState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingRootUiState

@Composable
internal fun CoolingPowerCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingDashboardGeometry.statusCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                AquaCoolingDashboardGeometry.telemetryContentGap
            )
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_power_title),
                style = typography.title
            )
            CoolingPowerContent(state = state, colors = colors, typography = typography)
        }
    }
}

@Composable
private fun CoolingPowerContent(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.powerContentGap)
    ) {
        CoolingPowerGlyph(colors)
        CoolingPowerValues(
            state = state,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CoolingPowerValues(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.powerValueGap)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = coolingPowerNumberText(state.powerWatts),
                style = typography.title.copy(
                    color = colors.primaryText,
                    fontSize = AquaCoolingDashboardTypography.powerValueSize
                ),
                maxLines = 1
            )
            if (state.powerWatts != null) {
                BasicText(
                    text = stringResource(R.string.device_cooling_power_unit),
                    style = typography.body.copy(
                        color = colors.primaryText,
                        fontSize = AquaCoolingDashboardTypography.powerUnitSize
                    ),
                    maxLines = 1
                )
            }
        }
        BasicText(
            text = stringResource(R.string.device_cooling_estimated_consumption),
            style = typography.micro.copy(color = colors.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaCoolingDashboardGeometry.powerDividerHeight)
                .background(colors.outline.copy(alpha = AquaCoolingDashboardAlpha.divider))
        )
        BasicText(
            text = coolingEnergyText(state.estimatedKwhPerDay),
            style = typography.body.copy(color = colors.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoolingPowerGlyph(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .size(AquaCoolingDashboardGeometry.powerGlyphContainerSize)
            .clip(CircleShape)
            .background(colors.mediaSurface.copy(alpha = AquaCoolingDashboardAlpha.chartBackground))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.outline,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AquaCoolingDashboardIcon(
            kind = AquaCoolingDashboardIconKind.POWER,
            tint = colors.accent,
            modifier = Modifier.size(AquaCoolingDashboardGeometry.powerGlyphSize)
        )
    }
}

@Composable
internal fun CoolingStatusCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openDescription = stringResource(R.string.device_cooling_open_system_status_description)
    AquaDeviceCardSurface(
        modifier = modifier
            .heightIn(min = AquaCoolingDashboardGeometry.statusCardMinimumHeight)
            .semantics { contentDescription = openDescription }
            .clickable(
                enabled = state.contentEnabled,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.statusRowGap)
        ) {
            CoolingStatusCardHeader(colors = colors, typography = typography)
            CoolingStatusCardRows(state = state, colors = colors, typography = typography)
        }
    }
}

@Composable
private fun CoolingStatusCardHeader(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_cooling_system_status_title),
            style = typography.title,
            modifier = Modifier.weight(1f)
        )
        AquaCoolingDashboardIcon(
            kind = AquaCoolingDashboardIconKind.CHEVRON,
            tint = colors.secondaryText,
            modifier = Modifier.size(AquaCoolingDashboardGeometry.modeSettingsChevronSize)
        )
    }
}

@Composable
private fun CoolingStatusCardRows(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    CoolingStatusRow(
        model = state.fanOutputHealth.toStatusRowModel(
            label = stringResource(R.string.device_cooling_status_fan_output)
        ),
        colors = colors,
        typography = typography
    )
    CoolingStatusRow(
        model = state.sensorHealth.toStatusRowModel(
            label = stringResource(R.string.device_cooling_status_sensors)
        ),
        colors = colors,
        typography = typography
    )
    CoolingStatusRow(
        model = CoolingStatusRowModel(
            label = stringResource(R.string.device_cooling_status_connection),
            value = if (state.contentEnabled) {
                stringResource(R.string.device_cooling_status_online)
            } else {
                stringResource(R.string.device_cooling_status_offline)
            },
            tone = if (state.contentEnabled) {
                CoolingStatusTone.SUCCESS
            } else {
                CoolingStatusTone.NEUTRAL
            }
        ),
        colors = colors,
        typography = typography
    )
    CoolingStatusRow(
        model = coolingAlarmStatusRow(
            activeAlarmCount = state.activeAlarmCount,
            highestSeverity = state.highestAlarmSeverity
        ),
        colors = colors,
        typography = typography
    )
}

private data class CoolingStatusRowModel(
    val label: String,
    val value: String,
    val tone: CoolingStatusTone
)

private enum class CoolingStatusTone {
    SUCCESS,
    WARNING,
    DANGER,
    NEUTRAL
}

@Composable
private fun CoolingHealthState.toStatusRowModel(label: String): CoolingStatusRowModel = when (this) {
    CoolingHealthState.READY -> CoolingStatusRowModel(
        label = label,
        value = stringResource(R.string.device_cooling_status_ready),
        tone = CoolingStatusTone.SUCCESS
    )
    CoolingHealthState.WARNING -> CoolingStatusRowModel(
        label = label,
        value = stringResource(R.string.device_cooling_status_warning),
        tone = CoolingStatusTone.WARNING
    )
    CoolingHealthState.FAULT -> CoolingStatusRowModel(
        label = label,
        value = stringResource(R.string.device_cooling_status_fault),
        tone = CoolingStatusTone.DANGER
    )
    CoolingHealthState.UNKNOWN -> CoolingStatusRowModel(
        label = label,
        value = stringResource(R.string.device_cooling_value_unavailable),
        tone = CoolingStatusTone.NEUTRAL
    )
}

@Composable
private fun coolingAlarmStatusRow(
    activeAlarmCount: Int?,
    highestSeverity: DeviceCoolingAlarmSeverity
): CoolingStatusRowModel = when {
    activeAlarmCount == null -> CoolingStatusRowModel(
        label = stringResource(R.string.device_cooling_status_alarm),
        value = stringResource(R.string.device_cooling_value_unavailable),
        tone = CoolingStatusTone.NEUTRAL
    )
    activeAlarmCount == 0 -> CoolingStatusRowModel(
        label = stringResource(R.string.device_cooling_status_alarm),
        value = stringResource(R.string.device_cooling_status_no_active_alarms),
        tone = CoolingStatusTone.SUCCESS
    )
    else -> CoolingStatusRowModel(
        label = stringResource(R.string.device_cooling_status_alarm),
        value = pluralStringResource(
            R.plurals.device_cooling_active_alarm_count,
            activeAlarmCount,
            activeAlarmCount
        ),
        tone = when (highestSeverity) {
            DeviceCoolingAlarmSeverity.WARNING -> CoolingStatusTone.WARNING
            DeviceCoolingAlarmSeverity.CRITICAL -> CoolingStatusTone.DANGER
            DeviceCoolingAlarmSeverity.NONE,
            DeviceCoolingAlarmSeverity.UNKNOWN -> CoolingStatusTone.NEUTRAL
        }
    )
}

@Composable
private fun CoolingStatusRow(
    model: CoolingStatusRowModel,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val toneColor = when (model.tone) {
        CoolingStatusTone.SUCCESS -> colors.success
        CoolingStatusTone.WARNING -> colors.warning
        CoolingStatusTone.DANGER -> colors.danger
        CoolingStatusTone.NEUTRAL -> colors.secondaryText
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = model.label,
            style = typography.caption.copy(color = colors.secondaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                AquaCoolingDashboardGeometry.statusValueGap
            )
        ) {
            Box(
                modifier = Modifier
                    .size(AquaCoolingDashboardGeometry.statusDotSize)
                    .clip(CircleShape)
                    .background(toneColor.copy(alpha = AquaCoolingDashboardAlpha.statusDot))
            )
            BasicText(
                text = model.value,
                style = typography.micro.copy(
                    color = toneColor,
                    fontSize = AquaCoolingDashboardTypography.statusValueSize
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
