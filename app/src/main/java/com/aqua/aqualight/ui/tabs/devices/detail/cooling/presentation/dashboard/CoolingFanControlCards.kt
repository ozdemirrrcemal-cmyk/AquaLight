@file:Suppress("LongMethod", "LongParameterList", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIcon
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIconKind
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.cooling.AquaCoolingGaugeSpec
import com.aqua.aqualight.ui.common.cooling.AquaCoolingSelectionIndicator
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.CoolingControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.DeviceCoolingRootUiState

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
    AquaCoolingDashboardCardSurface(
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
                .background(AquaCoolingDashboardPalette.success)
        )
        BasicText(
            text = stringResource(R.string.device_cooling_mode_active),
            style = typography.body.copy(color = AquaCoolingDashboardPalette.success)
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

@Composable
internal fun CoolingModeSettingsCard(
    state: DeviceCoolingRootUiState,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onAutomaticSettingsClick: () -> Unit,
    onProgramSettingsClick: () -> Unit
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.modeSettingsCardMinimumHeight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = stringResource(R.string.device_cooling_mode_settings_title),
                style = typography.title
            )
            BasicText(
                text = stringResource(R.string.device_cooling_mode_settings_description),
                style = typography.caption.copy(color = colors.secondaryText),
                modifier = Modifier.padding(
                    top = AquaCoolingDashboardGeometry.modeSettingsHeaderGap
                )
            )
            Column(
                modifier = Modifier.padding(
                    top = AquaCoolingDashboardGeometry.modeSettingsContentTopPadding
                )
            ) {
                CoolingModeSettingsRow(
                    model = CoolingModeSettingsRowModel(
                        mode = CoolingControlMode.AUTOMATIC,
                        icon = AquaCoolingDashboardIconKind.AUTOMATIC,
                        value = coolingAutomaticRangeText(state),
                        contentDescription = stringResource(
                            R.string.device_cooling_edit_automatic_description
                        ),
                        selected = state.selectedMode == CoolingControlMode.AUTOMATIC,
                        onClick = onAutomaticSettingsClick
                    ),
                    enabled = enabled && CoolingControlMode.AUTOMATIC in state.supportedModes,
                    colors = colors,
                    typography = typography
                )
                CoolingModeSettingsDivider(colors)
                CoolingModeSettingsRow(
                    model = CoolingModeSettingsRowModel(
                        mode = CoolingControlMode.MANUAL,
                        icon = AquaCoolingDashboardIconKind.MANUAL,
                        value = coolingManualTargetText(state.manualFanPercent),
                        contentDescription = stringResource(
                            R.string.device_cooling_manual_settings_description
                        ),
                        selected = state.selectedMode == CoolingControlMode.MANUAL,
                        onClick = null
                    ),
                    enabled = enabled && CoolingControlMode.MANUAL in state.supportedModes,
                    colors = colors,
                    typography = typography
                )
                CoolingModeSettingsDivider(colors)
                CoolingModeSettingsRow(
                    model = CoolingModeSettingsRowModel(
                        mode = CoolingControlMode.PROGRAM,
                        icon = AquaCoolingDashboardIconKind.PROGRAM,
                        value = coolingProgramSummaryText(state),
                        contentDescription = stringResource(
                            R.string.device_cooling_edit_program_description
                        ),
                        selected = state.selectedMode == CoolingControlMode.PROGRAM,
                        onClick = onProgramSettingsClick
                    ),
                    enabled = enabled && CoolingControlMode.PROGRAM in state.supportedModes,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

private data class CoolingModeSettingsRowModel(
    val mode: CoolingControlMode,
    val icon: AquaCoolingDashboardIconKind,
    val value: String,
    val contentDescription: String,
    val selected: Boolean,
    val onClick: (() -> Unit)?
)

@Composable
private fun CoolingModeSettingsRow(
    model: CoolingModeSettingsRowModel,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val interaction = if (model.onClick != null) {
        Modifier
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = model.onClick
            )
            .semantics { contentDescription = model.contentDescription }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.modeSettingsRowHeight)
            .then(interaction),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AquaCoolingDashboardGeometry.modeSettingsRowIconContainerSize)
                .clip(CircleShape)
                .background(
                    colors.accent.copy(
                        alpha = AquaCoolingDashboardAlpha.iconContainerBackground
                    )
                )
                .border(
                    width = AquaDeviceCardGeometry.outlineWidth,
                    color = colors.accent.copy(
                        alpha = AquaCoolingDashboardAlpha.iconContainerOutline
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AquaCoolingDashboardIcon(
                kind = model.icon,
                tint = colors.accent,
                modifier = Modifier.size(AquaCoolingDashboardGeometry.modeSettingsRowIconSize)
            )
        }
        Column(
            modifier = Modifier
                .padding(start = AquaCoolingDashboardGeometry.modeSettingsRowIconGap)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(
                AquaCoolingDashboardGeometry.modeSettingsRowValueGap
            )
        ) {
            BasicText(
                text = coolingModeLabel(model.mode),
                style = typography.body.copy(
                    color = colors.primaryText,
                    fontSize = AquaCoolingDashboardTypography.modeSettingsTitleSize
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = model.value,
                style = typography.caption.copy(
                    color = colors.secondaryText,
                    fontSize = AquaCoolingDashboardTypography.modeSettingsValueSize
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                AquaCoolingDashboardGeometry.modeSettingsTrailingGap
            )
        ) {
            if (model.selected) {
                Box(
                    modifier = Modifier
                        .clip(AquaCoolingDashboardGeometry.activeChipShape)
                        .background(
                            AquaCoolingDashboardPalette.success.copy(
                                alpha = AquaCoolingDashboardAlpha.activeChipBackground
                            )
                        )
                        .padding(
                            horizontal = AquaCoolingDashboardGeometry.activeChipHorizontalPadding,
                            vertical = AquaCoolingDashboardGeometry.activeChipVerticalPadding
                        )
                ) {
                    BasicText(
                        text = stringResource(R.string.device_cooling_mode_active_uppercase),
                        style = typography.micro.copy(
                            color = AquaCoolingDashboardPalette.success,
                            fontSize = AquaCoolingDashboardTypography.activeChipSize
                        )
                    )
                }
            }
            AquaCoolingDashboardIcon(
                kind = AquaCoolingDashboardIconKind.CHEVRON,
                tint = colors.primaryText,
                modifier = Modifier.size(AquaCoolingDashboardGeometry.modeSettingsChevronSize),
                strokeWidth = AquaCoolingDashboardGeometry.modeSettingsChevronStrokeWidth
            )
        }
    }
}

@Composable
private fun CoolingModeSettingsDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.modeSettingsDividerHeight)
            .background(colors.outline.copy(alpha = AquaCoolingDashboardAlpha.divider))
    )
}

@Composable
private fun coolingAutomaticRangeText(state: DeviceCoolingRootUiState): String {
    val minimum = state.autoStartTemperatureC
    val maximum = state.autoMaxTemperatureC
    return if (minimum != null && maximum != null) {
        stringResource(
            R.string.device_cooling_temperature_range_value_format,
            minimum,
            maximum
        )
    } else {
        stringResource(R.string.device_cooling_value_unavailable)
    }
}

@Composable
private fun coolingManualTargetText(percent: Int?): String = percent?.let { value ->
    stringResource(R.string.device_cooling_manual_target_value_format, value)
} ?: stringResource(R.string.device_cooling_manual_target_unavailable)

@Composable
private fun coolingProgramSummaryText(state: DeviceCoolingRootUiState): String {
    val count = state.programSlotCount
        ?: return stringResource(R.string.device_cooling_value_unavailable)
    if (count == 0) return stringResource(R.string.device_cooling_program_not_configured)
    val periods = pluralStringResource(
        R.plurals.device_cooling_program_period_count,
        count,
        count
    )
    val nextStart = state.nextProgramStartMinutesOfDay
        ?: return periods
    val hours = nextStart / MINUTES_PER_HOUR
    val minutes = nextStart % MINUTES_PER_HOUR
    val time = stringResource(R.string.device_cooling_time_value_format, hours, minutes)
    return stringResource(R.string.device_cooling_program_summary_with_next, periods, time)
}

private const val MINUTES_PER_HOUR = 60
