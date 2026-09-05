package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.cooling.AquaCoolingGaugeSpec
import com.aqua.aqualight.ui.common.cooling.AquaCoolingSelectionIndicator
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState

@Composable
internal fun CoolingFanSpeedCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingDashboardGeometry.compactCardMinimumHeight)
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
                    sweepAngle = AquaCoolingGaugeSpec.sweepAngle * value /
                        AquaCoolingGaugeSpec.maximumPercent,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(
            modifier = Modifier.padding(top = AquaCoolingDashboardGeometry.gaugeCaptionTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            BasicText(
                text = stringResource(R.string.device_cooling_fan_speed_caption),
                style = typography.caption.copy(
                    color = colors.secondaryText,
                    fontSize = AquaCoolingDashboardTypography.gaugeCaptionSize,
                    textAlign = TextAlign.Center
                )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = AquaCoolingDashboardGeometry.gaugeLabelsBottomPadding)
        ) {
            BasicText(
                text = stringResource(
                    R.string.device_cooling_percent_value_format,
                    AquaCoolingGaugeSpec.minimumPercent
                ),
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    fontSize = AquaCoolingDashboardTypography.gaugeScaleSize
                ),
                modifier = Modifier.weight(1f)
            )
            BasicText(
                text = stringResource(
                    R.string.device_cooling_percent_value_format,
                    AquaCoolingGaugeSpec.maximumPercent
                ),
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    fontSize = AquaCoolingDashboardTypography.gaugeScaleSize,
                    textAlign = TextAlign.End
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun CoolingModeCard(
    state: DeviceCoolingRootUiState,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onModeSelected: (CoolingControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    AquaDeviceCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingDashboardGeometry.compactCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.optionGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_operating_mode_title),
                style = typography.title
            )
            CoolingControlMode.entries.forEach { mode ->
                CoolingModeOption(
                    mode = mode,
                    selected = mode == state.selectedMode,
                    enabled = enabled && mode in state.supportedModes,
                    colors = colors,
                    typography = typography,
                    onClick = { onModeSelected(mode) }
                )
            }
            if (state.selectedMode != null) {
                CoolingActiveModeSummary(
                    fanPercent = state.fanPercentNow,
                    colors = colors,
                    typography = typography
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
                    colors.mediaSurface
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
            .selectable(
                selected = selected,
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
        AquaCoolingSelectionIndicator(
            selected = selected,
            selectedColor = colors.accent,
            idleColor = colors.secondaryText,
            checkColor = colors.primaryText,
            modifier = Modifier.size(AquaCoolingDashboardGeometry.radioSize)
        )
        BasicText(
            text = coolingModeLabel(mode),
            style = typography.body.copy(
                color = if (selected) colors.primaryText else colors.secondaryText
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoolingActiveModeSummary(
    fanPercent: Int?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.padding(top = AquaCoolingDashboardGeometry.modeStatusTopPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.modeStatusGap)
    ) {
        Box(
            modifier = Modifier
                .size(AquaCoolingDashboardGeometry.modeStatusDotSize)
                .clip(CircleShape)
                .background(colors.success)
        )
        BasicText(
            text = stringResource(R.string.device_cooling_mode_active),
            style = typography.body.copy(color = colors.success)
        )
        BasicText(
            text = stringResource(R.string.device_cooling_inline_separator),
            style = typography.micro.copy(color = colors.secondaryText)
        )
        BasicText(
            text = fanPercent?.let { value ->
                stringResource(R.string.device_cooling_percent_value_format, value)
            } ?: stringResource(R.string.device_cooling_value_unavailable),
            style = typography.body.copy(color = colors.accent)
        )
    }
}
