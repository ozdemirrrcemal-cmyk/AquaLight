package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIcon
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIconKind
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun ProgramActiveCard(
    activeSlot: DeviceCoolingProgramSlot?,
    context: Context,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingProgramGeometry.activeCardMinimumHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.activeRowGap)
            ) {
                BasicText(
                    text = stringResource(R.string.device_cooling_program_active_title),
                    style = typography.title.copy(color = colors.primaryText)
                )
                BasicText(
                    text = activeSlot?.let { slot ->
                        buildString {
                            append(formatProgramTimeRange(context, slot))
                            append(" • ")
                            append(programSlotSummary(slot))
                        }
                    } ?: stringResource(R.string.device_cooling_program_no_active_period),
                    style = typography.caption.copy(color = colors.secondaryText),
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .size(AquaCoolingProgramGeometry.activeDotSize)
                    .clip(CircleShape)
                    .background(
                        if (activeSlot == null) {
                            colors.secondaryText.copy(alpha = AquaCoolingProgramAlpha.timelineTrack)
                        } else {
                            colors.accent.copy(alpha = AquaCoolingProgramAlpha.activeDot)
                        }
                    )
            )
        }
    }
}

@Composable
internal fun ProgramSlotsHeader(
    canAddSlot: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onAddSlot: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AquaCoolingDashboardGeometry.cardHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_cooling_program_slots_title),
            style = typography.title.copy(color = colors.primaryText),
            modifier = Modifier.weight(1f)
        )
        BasicText(
            text = stringResource(R.string.device_cooling_program_add_slot),
            style = typography.caption.copy(color = colors.accent),
            modifier = Modifier
                .clip(AquaCoolingProgramGeometry.inlineActionShape)
                .clickable(enabled = canAddSlot, role = Role.Button, onClick = onAddSlot)
                .alpha(
                    if (canAddSlot) {
                        AquaCoolingProgramAlpha.inlineActionEnabled
                    } else {
                        AquaCoolingProgramAlpha.inlineActionDisabled
                    }
                )
                .padding(
                    horizontal = AquaCoolingProgramGeometry.inlineActionHorizontalPadding,
                    vertical = AquaCoolingProgramGeometry.inlineActionVerticalPadding
                ),
            maxLines = 1
        )
    }
}

@Composable
internal fun ProgramChevron(colors: AquaDeviceCardColors, expanded: Boolean) {
    AquaCoolingDashboardIcon(
        kind = AquaCoolingDashboardIconKind.CHEVRON,
        tint = colors.accent,
        modifier = Modifier
            .width(AquaCoolingProgramGeometry.slotChevronWidth)
            .height(AquaCoolingProgramGeometry.slotChevronHeight)
            .rotate(
                if (expanded) {
                    AquaCoolingProgramGeometry.expandedChevronRotationDegrees
                } else {
                    0f
                }
            ),
        strokeWidth = AquaCoolingProgramGeometry.chevronStrokeWidth
    )
}

@Composable
internal fun ProgramDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingProgramGeometry.expandedDividerHeight)
            .background(colors.outline.copy(alpha = AquaCoolingProgramAlpha.expandedDivider))
    )
}

internal fun formatProgramTimeRange(
    context: Context,
    slot: DeviceCoolingProgramSlot
): String = context.getString(
    R.string.device_cooling_program_time_range_format,
    formatProgramTime(context, slot.startMinutes),
    formatProgramTime(context, slot.endMinutes)
)

internal fun formatProgramTime(context: Context, minutesOfDay: Int): String =
    LocaleFormatter.formatTimeOfDay24Hour(context, minutesOfDay)
