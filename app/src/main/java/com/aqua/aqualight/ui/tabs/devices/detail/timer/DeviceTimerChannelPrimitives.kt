package com.aqua.aqualight.ui.tabs.devices.detail.timer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardTypography

@Composable
internal fun TimerRegimeSelector(
    selected: DeviceTimerChannelRegime,
    enabled: Boolean,
    onSelected: (DeviceTimerChannelRegime) -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val optionStyle = TimerRegimeOptionStyle(colors, typography)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.modeGroupGap)
    ) {
        DeviceTimerChannelRegime.entries.forEach { regime ->
            TimerRegimeOption(
                regime = regime,
                selected = regime == selected,
                enabled = enabled,
                onClick = { onSelected(regime) },
                style = optionStyle
            )
        }
    }
}

private data class TimerRegimeOptionStyle(
    val colors: AquaDeviceCardColors,
    val typography: AquaDeviceCardTypography
)

@Composable
private fun RowScope.TimerRegimeOption(
    regime: DeviceTimerChannelRegime,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    style: TimerRegimeOptionStyle
) {
    val colors = style.colors
    val typography = style.typography
    val label = timerRegimeLabel(regime)
    val description = stringResource(R.string.device_timer_mode_content_description, label)
    val background = if (selected) {
        colors.accent.copy(alpha = AquaTimerDashboardAlpha.selectedBackground)
    } else {
        colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground)
    }
    val outline = if (selected) {
        colors.accent.copy(alpha = AquaTimerDashboardAlpha.selectedOutline)
    } else {
        colors.outline
    }
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(AquaTimerDashboardGeometry.modeShape)
            .background(background)
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = outline,
                shape = AquaTimerDashboardGeometry.modeShape
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .padding(
                horizontal = AquaTimerDashboardGeometry.modeHorizontalPadding,
                vertical = AquaTimerDashboardGeometry.modeVerticalPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.modeLabelGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AquaTimerDashboardGeometry.modeIndicatorSize)
                .clip(CircleShape)
                .background(if (selected) colors.accent else colors.secondaryText)
        )
        BasicText(
            text = label,
            style = typography.micro.copy(
                color = if (selected) colors.primaryText else colors.secondaryText,
                fontSize = AquaTimerDashboardTypography.modeSize,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TimerStatePill(
    label: String,
    active: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val stateColor = if (active) colors.success else colors.secondaryText
    Box(
        modifier = Modifier
            .clip(AquaTimerDashboardGeometry.statusShape)
            .background(stateColor.copy(alpha = AquaTimerDashboardAlpha.statusBackground))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = stateColor,
                shape = AquaTimerDashboardGeometry.statusShape
            )
            .padding(
                horizontal = AquaTimerDashboardGeometry.statusHorizontalPadding,
                vertical = AquaTimerDashboardGeometry.statusVerticalPadding
            )
    ) {
        BasicText(
            text = label,
            style = typography.micro.copy(
                color = stateColor,
                fontSize = AquaTimerDashboardTypography.statusSize
            ),
            maxLines = 1
        )
    }
}

@Composable
internal fun TimerMetadataRow(
    text: String,
    tint: Color,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.metadataTextGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_last_seen),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.metadataIconSize),
            colorFilter = ColorFilter.tint(tint)
        )
        BasicText(
            text = text,
            style = typography.caption.copy(color = tint),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TimerDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaTimerDashboardGeometry.dividerHeight)
            .background(colors.outline.copy(alpha = AquaTimerDashboardAlpha.divider))
    )
}

@Composable
@Suppress("LongParameterList")
internal fun TimerChannelActions(
    programEnabled: Boolean,
    temporaryOverrideEnabled: Boolean,
    onProgramClick: () -> Unit,
    onTemporaryOverrideClick: (DeviceTimerChannelRegime) -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.actionRowGap)
    ) {
        TimerWideAction(
            label = stringResource(R.string.device_timer_manage_programs),
            enabled = programEnabled,
            onClick = onProgramClick,
            colors = colors,
            typography = typography
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.actionButtonGap)
        ) {
            TimerCompactAction(
                label = stringResource(R.string.device_timer_timed_on),
                enabled = temporaryOverrideEnabled,
                onClick = { onTemporaryOverrideClick(DeviceTimerChannelRegime.ON) },
                colors = colors,
                typography = typography
            )
            TimerCompactAction(
                label = stringResource(R.string.device_timer_timed_off),
                enabled = temporaryOverrideEnabled,
                onClick = { onTemporaryOverrideClick(DeviceTimerChannelRegime.OFF) },
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun TimerWideAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    TimerActionSurface(
        label = label,
        enabled = enabled,
        onClick = onClick,
        outline = colors.accent,
        background = colors.accent.copy(alpha = AquaTimerDashboardAlpha.actionBackground),
        textColor = colors.primaryText,
        typography = typography,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RowScope.TimerCompactAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    TimerActionSurface(
        label = label,
        enabled = enabled,
        onClick = onClick,
        outline = colors.outline,
        background = colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground),
        textColor = colors.secondaryText,
        typography = typography,
        modifier = Modifier.weight(1f)
    )
}

@Composable
@Suppress("LongParameterList")
private fun TimerActionSurface(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    outline: Color,
    background: Color,
    textColor: Color,
    typography: AquaDeviceCardTypography,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(AquaTimerDashboardGeometry.actionShape)
            .background(background)
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = outline,
                shape = AquaTimerDashboardGeometry.actionShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(AquaTimerDashboardGeometry.actionPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = typography.caption.copy(color = textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
