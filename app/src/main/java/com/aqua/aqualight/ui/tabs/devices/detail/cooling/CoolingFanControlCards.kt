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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.cooling.AquaCoolingGaugeSpec
import com.aqua.aqualight.ui.common.cooling.AquaCoolingProfileGlyphSpec
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun CoolingFanSpeedCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    AquaCoolingDashboardCardSurface(
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
            BasicText(
                text = coolingModeLabel(state.selectedMode),
                style = typography.micro.copy(color = colors.secondaryText),
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
                    sweepAngle = AquaCoolingGaugeSpec.sweepAngle * value /
                        AquaCoolingGaugeSpec.maximumPercent,
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
internal fun CoolingModeCard(
    selectedMode: CoolingControlMode,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onModeSelected: (CoolingControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    AquaCoolingDashboardCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingDashboardGeometry.compactCardMinimumHeight)
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
internal fun CoolingProfileCard(
    selectedProfile: CoolingProfile,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onProfileSelected: (CoolingProfile) -> Unit
) {
    AquaCoolingDashboardCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_profile_title),
                style = typography.title
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.profileGap)
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
                    colors.accent.copy(alpha = AquaCoolingDashboardAlpha.profileSelectedBackground)
                } else {
                    colors.mediaSurface
                }
            )
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = if (selected) colors.accent else colors.mediaOutline,
                shape = shape
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(
                horizontal = AquaCoolingDashboardGeometry.profileHorizontalPadding,
                vertical = AquaCoolingDashboardGeometry.profileVerticalPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(AquaCoolingDashboardGeometry.profileGlyphSize)) {
            val lineColor = if (selected) colors.primaryText else colors.secondaryText
            val stroke = AquaDeviceCardGeometry.outlineWidth.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val radiusFraction = when (profile) {
                CoolingProfile.QUIET -> AquaCoolingProfileGlyphSpec.quietRadiusFraction
                CoolingProfile.BALANCED -> AquaCoolingProfileGlyphSpec.balancedRadiusFraction
                CoolingProfile.PERFORMANCE -> AquaCoolingProfileGlyphSpec.performanceRadiusFraction
                CoolingProfile.BOOST -> AquaCoolingProfileGlyphSpec.boostRadiusFraction
            }
            drawCircle(
                color = lineColor,
                radius = size.minDimension * radiusFraction,
                center = center,
                style = Stroke(stroke)
            )
            drawCircle(
                color = lineColor,
                radius = AquaCoolingProfileGlyphSpec.centerDotRadius.toPx(),
                center = center
            )
        }
        BasicText(
            text = coolingProfileLabel(profile),
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
internal fun CoolingModeControlCard(
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
            onOpenClick = onAutomaticSettingsClick
        )
        CoolingControlMode.PROGRAM -> CoolingProgramControlCard(
            enabled = enabled,
            colors = colors,
            typography = typography,
            onOpenClick = onProgramSettingsClick
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
    val clamped = percent.coerceIn(
        AquaCoolingGaugeSpec.minimumPercent,
        AquaCoolingGaugeSpec.maximumPercent
    )
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.controlCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            CoolingSectionHeader(
                title = stringResource(R.string.device_cooling_manual_card_title),
                trailing = stringResource(R.string.device_cooling_percent_value_format, clamped),
                colors = colors,
                typography = typography
            )
            BasicText(
                text = stringResource(R.string.device_cooling_manual_card_subtitle),
                style = typography.micro.copy(color = colors.secondaryText)
            )
            CoolingManualSlider(
                percent = clamped,
                enabled = enabled,
                colors = colors,
                onValueChanged = onValueChanged
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = stringResource(
                        R.string.device_cooling_percent_value_format,
                        AquaCoolingGaugeSpec.minimumPercent
                    ),
                    style = typography.micro.copy(color = colors.secondaryText),
                    modifier = Modifier.weight(1f)
                )
                BasicText(
                    text = stringResource(
                        R.string.device_cooling_percent_value_format,
                        AquaCoolingGaugeSpec.maximumPercent
                    ),
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
    val clamped = percent.coerceIn(
        AquaCoolingGaugeSpec.minimumPercent,
        AquaCoolingGaugeSpec.maximumPercent
    )
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
                            onValueChanged(percentFromPosition(change.position.x, widthPx))
                        }
                    )
                }
            }
            .semantics {
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clamped.toFloat(),
                    range = AquaCoolingGaugeSpec.minimumPercent.toFloat()..
                        AquaCoolingGaugeSpec.maximumPercent.toFloat(),
                    steps = AquaCoolingGaugeSpec.maximumPercent -
                        AquaCoolingGaugeSpec.minimumPercent - 1
                )
                if (enabled) {
                    setProgress { requested ->
                        onValueChanged(
                            requested.toInt().coerceIn(
                                AquaCoolingGaugeSpec.minimumPercent,
                                AquaCoolingGaugeSpec.maximumPercent
                            )
                        )
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
        val range = (AquaCoolingGaugeSpec.maximumPercent -
            AquaCoolingGaugeSpec.minimumPercent).coerceAtLeast(1)
        val normalized = (clamped - AquaCoolingGaugeSpec.minimumPercent).toFloat() / range
        val thumbX = startX + trackWidth * normalized
        val trackStroke = AquaCoolingDashboardGeometry.sliderTrackHeight.toPx()
        drawLine(
            color = colors.secondaryText.copy(alpha = AquaCoolingDashboardAlpha.trackInactive),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        if (normalized > 0f) {
            drawLine(
                color = colors.accent,
                start = Offset(startX, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = colors.primaryText,
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
    onOpenClick: () -> Unit
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.controlCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            CoolingDetailHeader(
                title = stringResource(R.string.device_cooling_automatic_card_title),
                enabled = enabled,
                colors = colors,
                typography = typography,
                contentDescriptionText = stringResource(
                    R.string.device_cooling_edit_automatic_description
                ),
                onClick = onOpenClick
            )
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_card_subtitle),
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CoolingControlValueRow(
                label = stringResource(R.string.device_cooling_fan_start_temperature),
                value = coolingTemperatureText(startTemperatureC),
                colors = colors,
                typography = typography
            )
            CoolingControlValueRow(
                label = stringResource(R.string.device_cooling_max_speed_temperature),
                value = coolingTemperatureText(maxTemperatureC),
                colors = colors,
                typography = typography
            )
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_range_help),
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoolingProgramControlCard(
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onOpenClick: () -> Unit
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.controlCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            CoolingDetailHeader(
                title = stringResource(R.string.device_cooling_program_card_title),
                enabled = enabled,
                colors = colors,
                typography = typography,
                contentDescriptionText = stringResource(
                    R.string.device_cooling_edit_program_description
                ),
                onClick = onOpenClick
            )
            BasicText(
                text = stringResource(R.string.device_cooling_program_not_configured),
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = stringResource(R.string.device_cooling_program_not_configured_hint),
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoolingDetailHeader(
    title: String,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    contentDescriptionText: String,
    onClick: () -> Unit
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
        CoolingDetailAction(
            enabled = enabled,
            colors = colors,
            contentDescriptionText = contentDescriptionText,
            onClick = onClick
        )
    }
}

@Composable
private fun CoolingDetailAction(
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    contentDescriptionText: String,
    onClick: () -> Unit
) {
    val shape = AquaCoolingDashboardGeometry.detailActionShape
    Box(
        modifier = Modifier
            .size(AquaCoolingDashboardGeometry.detailActionSize)
            .clip(shape)
            .background(
                colors.accent.copy(alpha = AquaCoolingDashboardAlpha.detailActionBackground)
            )
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.detailActionOutline),
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentDescriptionText },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(AquaCoolingDashboardGeometry.detailActionGlyphSize)) {
            val tint = if (enabled) colors.accent else colors.secondaryText
            val strokeWidth = AquaCoolingDashboardGeometry.detailActionStrokeWidth.toPx()
            val center = Offset(size.width * 0.58f, size.height * 0.50f)
            drawLine(
                color = tint,
                start = Offset(size.width * 0.34f, size.height * 0.22f),
                end = center,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = center,
                end = Offset(size.width * 0.34f, size.height * 0.78f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
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

private fun percentFromPosition(positionX: Float, widthPx: Float): Int {
    if (widthPx <= 0f) return AquaCoolingGaugeSpec.minimumPercent
    val fraction = (positionX / widthPx).coerceIn(0f, 1f)
    val range = AquaCoolingGaugeSpec.maximumPercent - AquaCoolingGaugeSpec.minimumPercent
    return (AquaCoolingGaugeSpec.minimumPercent + fraction * range).toInt()
}
