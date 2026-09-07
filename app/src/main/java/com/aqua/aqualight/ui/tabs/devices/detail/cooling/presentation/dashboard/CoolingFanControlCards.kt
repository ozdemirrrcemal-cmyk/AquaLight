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
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.DeviceCoolingRootUiState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCoolingDisplayPercentOrNull

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
    percent: Double?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val minimumPercent = AquaCoolingGaugeSpec.minimumPercent.toDouble()
    val maximumPercent = AquaCoolingGaugeSpec.maximumPercent.toDouble()
    val validPercent = percent?.takeIf { value ->
        value.isFinite() && value in minimumPercent..maximumPercent
    }
    Box(
        modifier = Modifier.size(AquaCoolingDashboardGeometry.gaugeSize),
        contentAlignment = Alignment.Center
    ) {
        CoolingFanGaugeArc(percent = validPercent, colors = colors)
        CoolingFanGaugeValue(percent = validPercent, colors = colors, typography = typography)
        CoolingFanGaugeScale(
            colors = colors,
            typography = typography,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CoolingFanGaugeArc(percent: Double?, colors: AquaDeviceCardColors) {
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
        percent?.let { value ->
            drawArc(
                color = colors.accent,
                startAngle = AquaCoolingGaugeSpec.startAngle,
                sweepAngle = AquaCoolingGaugeSpec.sweepAngle * value.toFloat() /
                    AquaCoolingGaugeSpec.maximumPercent,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun CoolingFanGaugeValue(
    percent: Double?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val displayPercent = percent.toCoolingDisplayPercentOrNull()
    val displayText = if (displayPercent != null) {
        stringResource(R.string.device_cooling_percent_value_format, displayPercent)
    } else {
        stringResource(R.string.device_cooling_value_unavailable)
    }
    Column(
        modifier = Modifier.padding(top = AquaCoolingDashboardGeometry.gaugeCaptionTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = displayText,
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
}

@Composable
private fun CoolingFanGaugeScale(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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

@Composable
internal fun CoolingModeCard(
    state: DeviceCoolingRootUiState,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    onModeSelected: (CoolingControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = aquaCoolingDashboardTypography(colors)
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
    onClick: () -> Unit
) {
    val typography = aquaCoolingDashboardTypography(colors)
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
