@file:Suppress("LongMethod", "LongParameterList", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.cooling.AquaCoolingGaugeSpec
import com.aqua.aqualight.ui.common.cooling.AquaCoolingInteractionStyle
import com.aqua.aqualight.ui.common.cooling.AquaCoolingTemperatureChartSpec
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

@Composable
internal fun DeviceCoolingDashboardScreen(
    state: DeviceCoolingRootUiState,
    onModeSelected: (CoolingControlMode) -> Unit,
    onProfileSelected: (CoolingProfile) -> Unit,
    onManualFanPercentChanged: (Int) -> Unit,
    onAutomaticSettingsClick: () -> Unit,
    onProgramSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val contentAlpha = if (state.contentEnabled) {
        AquaCoolingInteractionStyle.enabledContentAlpha
    } else {
        AquaCoolingInteractionStyle.disabledContentAlpha
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .semantics {
                if (!state.contentEnabled) disabled()
            },
        contentPadding = PaddingValues(
            start = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            top = AquaCoolingDashboardGeometry.screenTopPadding,
            end = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            bottom = AquaCoolingDashboardGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.cardGap)
    ) {
        item(key = "temperature") {
            CoolingTemperatureCard(state, colors, typography)
        }
        item(key = "fan") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    AquaCoolingDashboardGeometry.splitCardGap
                )
            ) {
                CoolingFanSpeedCard(
                    state = state,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                CoolingModeCard(
                    selectedMode = state.selectedMode,
                    enabled = state.contentEnabled,
                    colors = colors,
                    typography = typography,
                    onModeSelected = onModeSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item(key = "power-status") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    AquaCoolingDashboardGeometry.splitCardGap
                )
            ) {
                CoolingPowerCard(
                    state = state,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                CoolingStatusCard(
                    state = state,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item(key = "profile") {
            CoolingProfileCard(
                selectedProfile = state.selectedProfile,
                enabled = state.contentEnabled,
                colors = colors,
                typography = typography,
                onProfileSelected = onProfileSelected
            )
        }
        item(key = "mode-control") {
            CoolingModeControlCard(
                state = state,
                enabled = state.contentEnabled,
                colors = colors,
                typography = typography,
                onManualFanPercentChanged = onManualFanPercentChanged,
                onAutomaticSettingsClick = onAutomaticSettingsClick,
                onProgramSettingsClick = onProgramSettingsClick
            )
        }
    }
}

@Composable
private fun CoolingTemperatureCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.temperatureCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.contentGap)
        ) {
            SectionHeader(
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoolingTemperatureChart(
                    historyC = state.temperatureHistoryC,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier.width(
                        AquaCoolingDashboardGeometry.temperatureMetricWidth
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        AquaDeviceCardGeometry.contentGap
                    )
                ) {
                    CoolingMetric(
                        label = stringResource(R.string.device_cooling_tank_temperature_label),
                        value = temperatureText(state.tankTemperatureC),
                        colors = colors,
                        typography = typography,
                        primary = true
                    )
                    CoolingMetric(
                        label = stringResource(R.string.device_cooling_room_temperature_label),
                        value = temperatureText(state.roomTemperatureC),
                        colors = colors,
                        typography = typography
                    )
                    CoolingMetric(
                        label = stringResource(R.string.device_cooling_humidity_label),
                        value = humidityText(state.humidityPercent),
                        colors = colors,
                        typography = typography
                    )
                }
            }
        }
    }
}

@Composable
private fun CoolingTemperatureChart(
    historyC: List<Double>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaCoolingDashboardGeometry.temperatureChartHeight)
                .clip(
                    androidx.compose.foundation.shape.RoundedCornerShape(
                        AquaCoolingDashboardGeometry.temperatureChartCornerRadius
                    )
                )
                .background(
                    colors.mediaSurface.copy(alpha = AquaCoolingDashboardAlpha.chartBackground)
                )
                .border(
                    width = AquaDeviceCardGeometry.outlineWidth,
                    color = colors.mediaOutline,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        AquaCoolingDashboardGeometry.temperatureChartCornerRadius
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizontalPadding =
                    AquaCoolingDashboardGeometry.temperatureChartPadding.toPx()
                val verticalPadding = horizontalPadding
                val plotWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                val plotHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)
                val gridColor = colors.secondaryText.copy(
                    alpha = AquaCoolingDashboardAlpha.chartGrid
                )
                val gridStroke = AquaCoolingDashboardGeometry.chartGridStrokeWidth.toPx()
                val gridCount = AquaCoolingTemperatureChartSpec.horizontalGridLineCount
                repeat(gridCount) { index ->
                    val fraction = index.toFloat() / (gridCount - 1).coerceAtLeast(1)
                    val y = verticalPadding + plotHeight * fraction
                    drawLine(
                        color = gridColor,
                        start = Offset(horizontalPadding, y),
                        end = Offset(horizontalPadding + plotWidth, y),
                        strokeWidth = gridStroke
                    )
                }

                if (historyC.size >= 2) {
                    val values = historyC.map(Double::toFloat)
                    val rawMin = values.minOrNull() ?: 0f
                    val rawMax = values.maxOrNull() ?: rawMin
                    val rawSpan = rawMax - rawMin
                    val span = rawSpan.coerceAtLeast(
                        AquaCoolingTemperatureChartSpec.minimumVerticalSpanC
                    )
                    val center = (rawMin + rawMax) / 2f
                    val minValue = center - span / 2f -
                        AquaCoolingTemperatureChartSpec.verticalPaddingC
                    val maxValue = center + span / 2f +
                        AquaCoolingTemperatureChartSpec.verticalPaddingC
                    val valueSpan = (maxValue - minValue).coerceAtLeast(1f)
                    val denominator = (values.lastIndex).coerceAtLeast(1).toFloat()
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = horizontalPadding + plotWidth * (index / denominator)
                        val normalized = ((value - minValue) / valueSpan).coerceIn(0f, 1f)
                        val y = verticalPadding + plotHeight * (1f - normalized)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.chartLine),
                        style = Stroke(
                            width = AquaCoolingDashboardGeometry.chartLineStrokeWidth.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                    val finalValue = values.last()
                    val finalNormalized = ((finalValue - minValue) / valueSpan).coerceIn(0f, 1f)
                    drawCircle(
                        color = colors.accent,
                        radius = AquaCoolingDashboardGeometry.chartPointRadius.toPx(),
                        center = Offset(
                            horizontalPadding + plotWidth,
                            verticalPadding + plotHeight * (1f - finalNormalized)
                        )
                    )
                }
            }

            if (historyC.size < 2) {
                BasicText(
                    text = stringResource(R.string.device_cooling_temperature_history_empty),
                    style = typography.caption.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(
                        horizontal = AquaCoolingDashboardGeometry.temperatureChartPadding
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(AquaDeviceCardGeometry.compactGap))
        Row(modifier = Modifier.fillMaxWidth()) {
            ChartAxisLabel(
                text = stringResource(R.string.device_cooling_chart_24h_start),
                style = typography.micro,
                modifier = Modifier.weight(1f)
            )
            ChartAxisLabel(
                text = stringResource(R.string.device_cooling_chart_12h),
                style = typography.micro.copy(textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f)
            )
            ChartAxisLabel(
                text = stringResource(R.string.device_cooling_chart_now),
                style = typography.micro.copy(textAlign = TextAlign.End),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChartAxisLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier
) {
    BasicText(
        text = text,
        style = style,
        modifier = modifier,
        maxLines = 1
    )
}

@Composable
private fun CoolingFanSpeedCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier.heightIn(
            min = AquaCoolingDashboardGeometry.compactCardMinimumHeight
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_fan_speed_title),
                style = typography.title,
                modifier = Modifier.fillMaxWidth()
            )
            CoolingFanGauge(
                percent = state.fanPercentNow,
                colors = colors,
                typography = typography
            )
            BasicText(
                text = modeLabel(state.selectedMode),
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoolingFanGauge(
    percent: Int?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val clamped = percent?.coerceIn(
        AquaCoolingGaugeSpec.minimumPercent,
        AquaCoolingGaugeSpec.maximumPercent
    )
    Box(
        modifier = Modifier.size(AquaCoolingDashboardGeometry.gaugeSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = AquaCoolingDashboardGeometry.gaugeStrokeWidth.toPx()
            val inset = stroke / 2f + AquaCoolingDashboardGeometry.gaugeInnerGap.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(
                width = (size.width - inset * 2f).coerceAtLeast(1f),
                height = (size.height - inset * 2f).coerceAtLeast(1f)
            )
            drawArc(
                color = colors.secondaryText.copy(alpha = AquaCoolingDashboardAlpha.trackInactive),
                startAngle = AquaCoolingGaugeSpec.startAngle,
                sweepAngle = AquaCoolingGaugeSpec.sweepAngle,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            clamped?.let { value ->
                drawArc(
                    color = colors.accent,
                    startAngle = AquaCoolingGaugeSpec.startAngle,
                    sweepAngle = AquaCoolingGaugeSpec.sweepAngle * value / 100f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        BasicText(
            text = clamped?.let { value ->
                stringResource(R.string.device_cooling_percent_value_format, value)
            } ?: stringResource(R.string.device_cooling_value_unavailable),
            style = typography.title.copy(
                color = colors.primaryText,
                fontSize = AquaCoolingDashboardTypography.gaugeValueSize,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun CoolingModeCard(
    selectedMode: CoolingControlMode,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onModeSelected: (CoolingControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier.heightIn(
            min = AquaCoolingDashboardGeometry.compactCardMinimumHeight
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.optionGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_fan_mode_title),
                style = typography.title
            )
            CoolingControlMode.entries.forEach { mode ->
                CoolingModeOption(
                    mode = mode,
                    selected = mode == selectedMode,
                    enabled = enabled,
                    colors = colors,
                    typography = typography,
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

@Composable
private fun CoolingModeOption(
    mode: CoolingControlMode,
    selected: Boolean,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit
) {
    val shape = AquaCoolingDashboardGeometry.optionShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    colors.accent.copy(alpha = AquaCoolingDashboardAlpha.selectedBackground)
                } else {
                    colors.mediaSurface.copy(alpha = AquaCoolingDashboardAlpha.chartBackground)
                }
            )
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = if (selected) {
                    colors.accent.copy(alpha = AquaCoolingDashboardAlpha.selectedOutline)
                } else {
                    colors.mediaOutline
                },
                shape = shape
            )
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(
                horizontal = AquaCoolingDashboardGeometry.optionHorizontalPadding,
                vertical = AquaCoolingDashboardGeometry.optionVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.optionGap)
    ) {
        Canvas(modifier = Modifier.size(AquaCoolingDashboardGeometry.radioSize)) {
            drawCircle(
                color = if (selected) colors.accent else colors.secondaryText,
                style = Stroke(width = AquaCoolingDashboardGeometry.radioStrokeWidth.toPx())
            )
            if (selected) {
                drawCircle(
                    color = colors.accent,
                    radius = AquaCoolingDashboardGeometry.radioDotRadius.toPx()
                )
            }
        }
        BasicText(
            text = modeLabel(mode),
            style = typography.body.copy(
                color = if (selected) colors.primaryText else colors.secondaryText
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoolingPowerCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier.heightIn(
            min = AquaCoolingDashboardGeometry.statusCardMinimumHeight
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.contentGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_power_title),
                style = typography.title
            )
            BasicText(
                text = powerText(state.powerWatts),
                style = typography.title.copy(
                    color = colors.primaryText,
                    fontSize = AquaCoolingDashboardTypography.metricValueSize
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)) {
                BasicText(
                    text = stringResource(R.string.device_cooling_estimated_consumption),
                    style = typography.caption.copy(color = colors.secondaryText)
                )
                BasicText(
                    text = energyText(state.estimatedKwhPerDay),
                    style = typography.body.copy(color = colors.primaryText)
                )
            }
        }
    }
}

@Composable
private fun CoolingStatusCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val ready = stringResource(R.string.device_cooling_status_ready)
    val unavailable = stringResource(R.string.device_cooling_value_unavailable)
    AquaDeviceCardSurface(
        modifier = modifier.heightIn(
            min = AquaCoolingDashboardGeometry.statusCardMinimumHeight
        )
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
            horizontalArrangement = Arrangement.spacedBy(
                AquaCoolingDashboardGeometry.statusValueGap
            )
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(AquaCoolingDashboardGeometry.statusDotSize)
                        .clip(CircleShape)
                        .background(
                            (if (positive) colors.accent else colors.secondaryText)
                                .copy(alpha = AquaCoolingDashboardAlpha.statusDot)
                        )
                )
            }
            BasicText(
                text = value,
                style = typography.micro.copy(
                    color = if (positive) colors.primaryText else colors.secondaryText
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoolingProfileCard(
    selectedProfile: CoolingProfile,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onProfileSelected: (CoolingProfile) -> Unit
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.contentGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_profile_title),
                style = typography.title
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    AquaCoolingDashboardGeometry.profileGap
                )
            ) {
                CoolingProfile.entries.forEach { profile ->
                    CoolingProfileOption(
                        profile = profile,
                        selected = profile == selectedProfile,
                        enabled = enabled,
                        colors = colors,
                        typography = typography,
                        onClick = { onProfileSelected(profile) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoolingProfileOption(
    profile: CoolingProfile,
    selected: Boolean,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AquaCoolingDashboardGeometry.profileShape
    Column(
        modifier = modifier
            .heightIn(min = AquaCoolingDashboardGeometry.profileMinimumHeight)
            .clip(shape)
            .background(
                if (selected) {
                    colors.accent.copy(alpha = AquaCoolingDashboardAlpha.selectedBackground)
                } else {
                    colors.mediaSurface.copy(alpha = AquaCoolingDashboardAlpha.chartBackground)
                }
            )
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = if (selected) {
                    colors.accent.copy(alpha = AquaCoolingDashboardAlpha.selectedOutline)
                } else {
                    colors.mediaOutline
                },
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(
                horizontal = AquaCoolingDashboardGeometry.profileHorizontalPadding,
                vertical = AquaCoolingDashboardGeometry.profileVerticalPadding
            )
            .semantics { role = Role.RadioButton },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(AquaCoolingDashboardGeometry.profileGlyphSize)) {
            val lineColor = if (selected) colors.accent else colors.secondaryText
            val stroke = AquaDeviceCardGeometry.outlineWidth.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * when (profile) {
                CoolingProfile.QUIET -> 0.20f
                CoolingProfile.BALANCED -> 0.29f
                CoolingProfile.PERFORMANCE -> 0.37f
                CoolingProfile.BOOST -> 0.44f
            }
            drawCircle(color = lineColor, radius = radius, center = center, style = Stroke(stroke))
            drawCircle(color = lineColor, radius = 1.5f, center = center)
        }
        BasicText(
            text = profileLabel(profile),
            style = typography.micro.copy(
                color = if (selected) colors.primaryText else colors.secondaryText,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoolingModeControlCard(
    state: DeviceCoolingRootUiState,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onManualFanPercentChanged: (Int) -> Unit,
    onAutomaticSettingsClick: () -> Unit,
    onProgramSettingsClick: () -> Unit
) {
    when (state.selectedMode) {
        CoolingControlMode.MANUAL -> CoolingManualControlCard(
            percent = state.manualFanPercent,
            enabled = enabled,
            colors = colors,
            typography = typography,
            onValueChanged = onManualFanPercentChanged
        )
        CoolingControlMode.AUTOMATIC -> CoolingAutomaticControlCard(
            startTemperatureC = state.autoStartTemperatureC,
            maxTemperatureC = state.autoMaxTemperatureC,
            enabled = enabled,
            colors = colors,
            typography = typography,
            onEditClick = onAutomaticSettingsClick
        )
        CoolingControlMode.PROGRAM -> CoolingProgramControlCard(
            enabled = enabled,
            colors = colors,
            typography = typography,
            onEditClick = onProgramSettingsClick
        )
    }
}

@Composable
private fun CoolingManualControlCard(
    percent: Int,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onValueChanged: (Int) -> Unit
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.controlCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            SectionHeader(
                title = stringResource(R.string.device_cooling_manual_card_title),
                trailing = stringResource(
                    R.string.device_cooling_percent_value_format,
                    percent.coerceIn(0, 100)
                ),
                colors = colors,
                typography = typography
            )
            BasicText(
                text = stringResource(R.string.device_cooling_manual_card_subtitle),
                style = typography.caption.copy(color = colors.secondaryText)
            )
            CoolingManualSlider(
                percent = percent,
                enabled = enabled,
                colors = colors,
                onValueChanged = onValueChanged
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = stringResource(R.string.device_cooling_percent_value_format, 0),
                    style = typography.micro.copy(color = colors.secondaryText),
                    modifier = Modifier.weight(1f)
                )
                BasicText(
                    text = stringResource(R.string.device_cooling_percent_value_format, 100),
                    style = typography.micro.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.End
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CoolingManualSlider(
    percent: Int,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    onValueChanged: (Int) -> Unit
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val clamped = percent.coerceIn(0, 100)
    val stateText = stringResource(R.string.device_cooling_percent_value_format, clamped)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.sliderTouchHeight)
            .onSizeChanged { size -> widthPx = size.width.toFloat() }
            .pointerInput(enabled, widthPx) {
                if (enabled) {
                    detectTapGestures { offset ->
                        onValueChanged(percentFromPosition(offset.x, widthPx))
                    }
                }
            }
            .pointerInput(enabled, widthPx) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onValueChanged(percentFromPosition(offset.x, widthPx))
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onValueChanged(percentFromPosition(change.position.x, widthPx))
                        }
                    )
                }
            }
            .semantics {
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clamped.toFloat(),
                    range = 0f..100f,
                    steps = 99
                )
                if (enabled) {
                    setProgress { requested ->
                        onValueChanged(requested.toInt().coerceIn(0, 100))
                        true
                    }
                } else {
                    disabled()
                }
            }
    ) {
        val centerY = size.height / 2f
        val startX = AquaCoolingDashboardGeometry.sliderThumbRadius.toPx()
        val endX = (size.width - startX).coerceAtLeast(startX)
        val trackWidth = (endX - startX).coerceAtLeast(1f)
        val thumbX = startX + trackWidth * clamped / 100f
        val trackStroke = AquaCoolingDashboardGeometry.sliderTrackHeight.toPx()
        drawLine(
            color = colors.secondaryText.copy(alpha = AquaCoolingDashboardAlpha.trackInactive),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        if (clamped > 0) {
            drawLine(
                color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.trackActive),
                start = Offset(startX, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = colors.surface,
            radius = AquaCoolingDashboardGeometry.sliderThumbRadius.toPx() +
                AquaCoolingDashboardGeometry.sliderThumbOutlineWidth.toPx(),
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = colors.accent,
            radius = AquaCoolingDashboardGeometry.sliderThumbRadius.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
private fun CoolingAutomaticControlCard(
    startTemperatureC: Double,
    maxTemperatureC: Double,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onEditClick: () -> Unit
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.controlCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            SectionHeader(
                title = stringResource(R.string.device_cooling_automatic_card_title),
                trailing = null,
                colors = colors,
                typography = typography
            )
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_card_subtitle),
                style = typography.caption.copy(color = colors.secondaryText)
            )
            CoolingControlValueRow(
                label = stringResource(R.string.device_cooling_fan_start_temperature),
                value = temperatureText(startTemperatureC),
                colors = colors,
                typography = typography
            )
            CoolingControlValueRow(
                label = stringResource(R.string.device_cooling_max_speed_temperature),
                value = temperatureText(maxTemperatureC),
                colors = colors,
                typography = typography
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = stringResource(R.string.device_cooling_automatic_range_help),
                    style = typography.micro.copy(color = colors.secondaryText),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(AquaDeviceCardGeometry.contentGap))
                CoolingEditAction(
                    enabled = enabled,
                    colors = colors,
                    typography = typography,
                    contentDescription = stringResource(
                        R.string.device_cooling_edit_automatic_description
                    ),
                    onClick = onEditClick
                )
            }
        }
    }
}

@Composable
private fun CoolingProgramControlCard(
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onEditClick: () -> Unit
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.controlCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            SectionHeader(
                title = stringResource(R.string.device_cooling_program_card_title),
                trailing = null,
                colors = colors,
                typography = typography
            )
            BasicText(
                text = stringResource(R.string.device_cooling_program_not_configured),
                style = typography.body.copy(color = colors.primaryText)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = stringResource(R.string.device_cooling_program_not_configured_hint),
                    style = typography.caption.copy(color = colors.secondaryText),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(AquaDeviceCardGeometry.contentGap))
                CoolingEditAction(
                    enabled = enabled,
                    colors = colors,
                    typography = typography,
                    contentDescription = stringResource(
                        R.string.device_cooling_edit_program_description
                    ),
                    onClick = onEditClick
                )
            }
        }
    }
}

@Composable
private fun CoolingControlValueRow(
    label: String,
    value: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaCoolingDashboardGeometry.controlRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = typography.caption.copy(color = colors.secondaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            style = typography.body.copy(
                color = colors.primaryText,
                fontSize = AquaCoolingDashboardTypography.controlValueSize
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun CoolingEditAction(
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    contentDescription: String,
    onClick: () -> Unit
) {
    val shape = AquaCoolingDashboardGeometry.editShape
    BasicText(
        text = stringResource(R.string.device_cooling_edit),
        style = typography.body.copy(
            color = if (enabled) colors.accent else colors.secondaryText,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .clip(shape)
            .background(colors.accent.copy(alpha = AquaCoolingDashboardAlpha.editBackground))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.selectedOutline),
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = AquaCoolingDashboardGeometry.editHorizontalPadding,
                vertical = AquaCoolingDashboardGeometry.editVerticalPadding
            )
            .semantics {
                stateDescription = contentDescription
            }
    )
}

@Composable
private fun SectionHeader(
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
private fun CoolingMetric(
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
private fun temperatureText(value: Double?): String = value?.let { temperature ->
    stringResource(R.string.device_cooling_temperature_value_format, temperature)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
private fun humidityText(value: Double?): String = value?.let { humidity ->
    stringResource(R.string.device_cooling_humidity_value_format, humidity)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
private fun powerText(value: Double?): String = value?.let { power ->
    stringResource(R.string.device_cooling_power_value_format, power)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
private fun energyText(value: Double?): String = value?.let { energy ->
    stringResource(R.string.device_cooling_energy_value_format, energy)
} ?: stringResource(R.string.device_cooling_value_unavailable)

@Composable
private fun modeLabel(mode: CoolingControlMode): String = when (mode) {
    CoolingControlMode.AUTOMATIC -> stringResource(R.string.device_cooling_mode_automatic)
    CoolingControlMode.MANUAL -> stringResource(R.string.device_cooling_mode_manual)
    CoolingControlMode.PROGRAM -> stringResource(R.string.device_cooling_mode_program)
}

@Composable
private fun profileLabel(profile: CoolingProfile): String = when (profile) {
    CoolingProfile.QUIET -> stringResource(R.string.device_cooling_profile_quiet)
    CoolingProfile.BALANCED -> stringResource(R.string.device_cooling_profile_balanced)
    CoolingProfile.PERFORMANCE -> stringResource(R.string.device_cooling_profile_performance)
    CoolingProfile.BOOST -> stringResource(R.string.device_cooling_profile_boost)
}

private fun percentFromPosition(positionX: Float, widthPx: Float): Int {
    if (widthPx <= 0f) return 0
    return ((positionX / widthPx).coerceIn(0f, 1f) * 100f).toInt()
}
