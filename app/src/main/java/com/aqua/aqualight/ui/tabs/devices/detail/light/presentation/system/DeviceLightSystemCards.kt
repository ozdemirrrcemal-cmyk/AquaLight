package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFanSnapshot
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun DeviceLightSystemStatusCard(
    state: DeviceLightSystemUiState,
    visuals: DeviceLightSystemVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.statusCardHeight),
        contentPadding = DeviceLightSystemGeometry.cardPadding
    ) {
        DeviceLightSystemStatusContent(state, visuals)
    }
}

@Composable
private fun DeviceLightSystemStatusContent(
    state: DeviceLightSystemUiState,
    visuals: DeviceLightSystemVisuals
) {
    val snapshot = state.snapshot
    Column(Modifier.fillMaxSize()) {
        BasicText(
            text = stringResource(R.string.device_light_system_current_temperature),
            style = visuals.typography.title
        )
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            DeviceLightTemperatureGauge(
                temperatureCelsius = snapshot?.temperatureCelsius,
                condition = snapshot?.condition ?: DeviceLightSystemCondition.NORMAL,
                visuals = visuals,
                modifier = Modifier.size(DeviceLightSystemGeometry.gaugeSize)
            )
        }
        Spacer(Modifier.height(DeviceLightSystemGeometry.gaugeFanGap))
        DeviceLightFanPair(state, visuals)
        Spacer(Modifier.height(DeviceLightSystemGeometry.dividerHeight))
        Box(
            Modifier
                .fillMaxWidth()
                .height(DeviceLightSystemGeometry.dividerHeight)
                .background(
                    visuals.colors.card.mediaOutline.copy(
                        alpha = DeviceLightSystemAlpha.divider
                    )
                )
        )
        DeviceLightSensorHealth(state, visuals)
    }
}

@Composable
private fun DeviceLightFanPair(
    state: DeviceLightSystemUiState,
    visuals: DeviceLightSystemVisuals
) {
    val fans = state.snapshot?.fans
    val firmwareSummary = fans?.takeIf { values -> values.size == SYSTEM_FAN_COUNT }
        ?.let { values ->
            stringResource(
                R.string.device_light_system_fans_summary,
                values[FIRST_FAN_INDEX].percent,
                values[SECOND_FAN_INDEX].percent
            )
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                firmwareSummary?.let { summary ->
                    Modifier.clearAndSetSemantics { contentDescription = summary }
                } ?: Modifier
            )
    ) {
        DeviceLightFanCard(
            fan = state.snapshot?.fans?.getOrNull(FIRST_FAN_INDEX),
            index = FIRST_FAN_INDEX,
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(DeviceLightSystemGeometry.fanCardGap)
                .height(DeviceLightSystemGeometry.fanCardHeight),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(DeviceLightSystemGeometry.dividerHeight)
                    .fillMaxSize()
                    .background(visuals.colors.card.mediaOutline)
            )
        }
        DeviceLightFanCard(
            fan = state.snapshot?.fans?.getOrNull(SECOND_FAN_INDEX),
            index = SECOND_FAN_INDEX,
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DeviceLightSensorHealth(
    state: DeviceLightSystemUiState,
    visuals: DeviceLightSystemVisuals
) {
    val sensorFault = state.snapshot?.sensorHealthy == false
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.sensorRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceLightInfoIcon(
            tint = visuals.colors.card.secondaryText,
            modifier = Modifier.size(DeviceLightSystemGeometry.infoIconSize)
        )
        Spacer(Modifier.width(DeviceLightSystemGeometry.infoTextGap))
        BasicText(
            text = stringResource(
                if (sensorFault) {
                    R.string.device_light_system_sensor_fault
                } else {
                    R.string.device_light_system_sensor_healthy
                }
            ),
            style = visuals.typography.caption.copy(
                color = if (sensorFault) {
                    visuals.colors.card.danger
                } else {
                    visuals.colors.card.secondaryText
                }
            )
        )
    }
}

@Composable
private fun DeviceLightFanCard(
    fan: DeviceLightSystemFanSnapshot?,
    index: Int,
    visuals: DeviceLightSystemVisuals,
    modifier: Modifier = Modifier
) {
    val percent = fan?.percent ?: 0
    val fanColor = if (fan?.healthy == false) {
        visuals.colors.card.danger
    } else {
        visuals.colors.action
    }
    Row(
        modifier = modifier
            .height(DeviceLightSystemGeometry.fanCardHeight)
            .clip(DeviceLightSystemGeometry.fanCardShape)
            .border(
                width = DeviceLightSystemGeometry.segmentOutlineWidth,
                color = visuals.colors.card.mediaOutline,
                shape = DeviceLightSystemGeometry.fanCardShape
            )
            .padding(DeviceLightSystemGeometry.fanCardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceLightFanIcon(
            tint = fanColor,
            modifier = Modifier.size(DeviceLightSystemGeometry.fanIconSize)
        )
        Spacer(Modifier.width(DeviceLightSystemGeometry.fanCardGap))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = stringResource(R.string.device_light_system_fan_name, index + 1),
                style = visuals.typography.body
            )
            Spacer(Modifier.height(DeviceLightSystemGeometry.fanCardGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DeviceLightSystemGeometry.fanTrackHeight)
                    .clip(DeviceLightSystemGeometry.fanTrackShape)
                    .background(
                        visuals.colors.card.mediaOutline.copy(
                            alpha = DeviceLightSystemAlpha.track
                        )
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(percent / PERCENT_MAXIMUM.toFloat())
                        .height(DeviceLightSystemGeometry.fanTrackHeight)
                        .background(fanColor)
                )
            }
        }
        Spacer(Modifier.width(DeviceLightSystemGeometry.fanCardGap))
        BasicText(
            text = stringResource(R.string.device_light_system_percent_format, percent),
            style = visuals.typography.body.copy(textAlign = TextAlign.End),
            modifier = Modifier.width(DeviceLightSystemGeometry.fanValueWidth)
        )
    }
}

@Composable
internal fun DeviceLightFanModeCard(
    state: DeviceLightSystemUiState,
    actions: DeviceLightSystemActions,
    visuals: DeviceLightSystemVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.modeCardHeight),
        contentPadding = DeviceLightSystemGeometry.cardPadding
    ) {
        Column(Modifier.fillMaxSize()) {
            BasicText(
                text = stringResource(R.string.device_light_system_fan_control),
                style = visuals.typography.title
            )
            BasicText(
                text = stringResource(R.string.device_light_system_operating_mode),
                style = visuals.typography.caption
            )
            Spacer(Modifier.height(DeviceLightSystemGeometry.modeLabelGap))
            DeviceLightFanModeSelector(state, actions, visuals)
            Spacer(Modifier.height(DeviceLightSystemGeometry.modeHelperGap))
            BasicText(
                text = stringResource(state.selectedMode.helperRes()),
                style = visuals.typography.caption
            )
        }
    }
}

@Composable
private fun DeviceLightFanModeSelector(
    state: DeviceLightSystemUiState,
    actions: DeviceLightSystemActions,
    visuals: DeviceLightSystemVisuals
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.segmentHeight)
            .clip(DeviceLightSystemGeometry.segmentShape)
            .border(
                DeviceLightSystemGeometry.segmentOutlineWidth,
                visuals.colors.card.mediaOutline,
                DeviceLightSystemGeometry.segmentShape
            )
    ) {
        DeviceLightFanMode.entries.forEachIndexed { index, mode ->
            val selected = state.selectedMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(1.dp)
                    .clip(DeviceLightSystemGeometry.segmentInnerShape)
                    .background(if (selected) visuals.colors.action else Color.Transparent)
                    .clickable(
                        enabled = state.controlsEnabled,
                        role = Role.RadioButton,
                        onClick = { actions.onModeChanged(mode) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = stringResource(mode.labelRes()),
                    style = visuals.typography.body.copy(
                        color = if (selected) {
                            visuals.colors.card.primaryText
                        } else {
                            visuals.colors.card.secondaryText
                        },
                        textAlign = TextAlign.Center
                    )
                )
            }
            if (index < DeviceLightFanMode.entries.lastIndex && !selected) {
                Box(
                    Modifier
                        .width(DeviceLightSystemGeometry.dividerHeight)
                        .fillMaxSize()
                        .background(visuals.colors.card.mediaOutline)
                )
            }
        }
    }
}

@Composable
internal fun DeviceLightAutomaticRangeCard(
    state: DeviceLightSystemUiState,
    actions: DeviceLightSystemActions,
    visuals: DeviceLightSystemVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.automaticCardHeight),
        contentPadding = DeviceLightSystemGeometry.cardPadding
    ) {
        Column(Modifier.fillMaxSize()) {
            BasicText(
                text = stringResource(R.string.device_light_system_automatic_range),
                style = visuals.typography.title
            )
            Spacer(Modifier.height(DeviceLightSystemGeometry.chartTitleGap))
            DeviceLightAutomaticChart(
                startTemperature = state.selectedStartTemperatureCelsius,
                fullSpeedTemperature = state.selectedFullSpeedTemperatureCelsius,
                visuals = visuals,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DeviceLightSystemGeometry.chartHeight)
            )
            DeviceLightTemperatureControlRow(
                control = DeviceLightTemperatureControlSpec(
                    labelRes = R.string.device_light_system_start,
                    value = state.selectedStartTemperatureCelsius,
                    minimum = state.snapshot?.startTemperaturePolicy?.minimum
                        ?: DEFAULT_START_TEMPERATURE_MINIMUM,
                    maximum = minOf(
                        state.snapshot?.startTemperaturePolicy?.maximum
                            ?: DEFAULT_START_TEMPERATURE_MAXIMUM,
                        state.selectedFullSpeedTemperatureCelsius - MINIMUM_TEMPERATURE_GAP
                    ),
                    enabled = state.controlsEnabled,
                    onValueChanged = actions.onStartTemperatureChanged
                ),
                visuals = visuals
            )
            DeviceLightTemperatureControlRow(
                control = DeviceLightTemperatureControlSpec(
                    labelRes = R.string.device_light_system_full_speed,
                    value = state.selectedFullSpeedTemperatureCelsius,
                    minimum = maxOf(
                        state.snapshot?.fullSpeedTemperaturePolicy?.minimum
                            ?: DEFAULT_FULL_SPEED_TEMPERATURE_MINIMUM,
                        state.selectedStartTemperatureCelsius + MINIMUM_TEMPERATURE_GAP
                    ),
                    maximum = state.snapshot?.fullSpeedTemperaturePolicy?.maximum
                        ?: DEFAULT_FULL_SPEED_TEMPERATURE_MAXIMUM,
                    enabled = state.controlsEnabled,
                    onValueChanged = actions.onFullSpeedTemperatureChanged
                ),
                visuals = visuals
            )
        }
    }
}

private fun DeviceLightFanMode.labelRes(): Int = when (this) {
    DeviceLightFanMode.AUTOMATIC -> R.string.device_light_system_mode_automatic
    DeviceLightFanMode.ON -> R.string.device_light_system_mode_on
    DeviceLightFanMode.OFF -> R.string.device_light_system_mode_off
}

private fun DeviceLightFanMode.helperRes(): Int = when (this) {
    DeviceLightFanMode.AUTOMATIC -> R.string.device_light_system_mode_automatic_helper
    DeviceLightFanMode.ON -> R.string.device_light_system_mode_on_helper
    DeviceLightFanMode.OFF -> R.string.device_light_system_mode_off_helper
}

private const val PERCENT_MAXIMUM = 100
private const val FIRST_FAN_INDEX = 0
private const val SECOND_FAN_INDEX = 1
private const val SYSTEM_FAN_COUNT = 2
private const val DEFAULT_START_TEMPERATURE_MINIMUM = 0
private const val DEFAULT_START_TEMPERATURE_MAXIMUM = 80
private const val DEFAULT_FULL_SPEED_TEMPERATURE_MINIMUM = 1
private const val DEFAULT_FULL_SPEED_TEMPERATURE_MAXIMUM = 90
private const val MINIMUM_TEMPERATURE_GAP = 1
