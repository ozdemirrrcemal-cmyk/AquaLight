package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
internal fun ProgramTimelineCard(
    slots: List<DeviceCoolingProgramSlot>,
    nowMinutes: Int?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingProgramGeometry.timelineCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.timelineAxisGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_program_timeline_title),
                style = typography.title.copy(color = colors.primaryText)
            )
            ProgramTimelineTrack(slots = slots, nowMinutes = nowMinutes, colors = colors)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timelineAxisLabels().forEach { label ->
                    BasicText(
                        text = label,
                        style = typography.micro.copy(color = colors.secondaryText),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramTimelineTrack(
    slots: List<DeviceCoolingProgramSlot>,
    nowMinutes: Int?,
    colors: AquaDeviceCardColors
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingProgramGeometry.timelineHeight)
    ) {
        val y = size.height * AquaCoolingProgramGeometry.timelineCenterFraction
        val trackStroke = AquaCoolingProgramGeometry.timelineTrackHeight.toPx()
        drawLine(
            color = colors.secondaryText.copy(alpha = AquaCoolingProgramAlpha.timelineTrack),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        slots.forEach { slot ->
            drawProgramSlot(slot, y, trackStroke, size.width, colors)
        }
        nowMinutes?.let { minute ->
            val nowX = size.width * (minute.toFloat() / MINUTES_PER_DAY)
            val markerExtent = trackStroke * AquaCoolingProgramGeometry.timelineNowExtentMultiplier
            drawLine(
                color = colors.primaryText.copy(alpha = AquaCoolingProgramAlpha.timelineNow),
                start = Offset(nowX, y - markerExtent),
                end = Offset(nowX, y + markerExtent),
                strokeWidth = AquaCoolingProgramGeometry.timelineNowStrokeWidth.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = colors.primaryText,
                radius = AquaCoolingProgramGeometry.timelineMarkerRadius.toPx(),
                center = Offset(nowX, y)
            )
        }
    }
}

private fun DrawScope.drawProgramSlot(
    slot: DeviceCoolingProgramSlot,
    y: Float,
    trackStroke: Float,
    width: Float,
    colors: AquaDeviceCardColors
) {
    val startX = width * (slot.startMinutes.toFloat() / MINUTES_PER_DAY)
    val endX = width * (slot.endMinutes.toFloat() / MINUTES_PER_DAY)
    val color = colors.accent.copy(alpha = AquaCoolingProgramAlpha.timelinePeriod)
    drawLine(color, Offset(startX, y), Offset(endX, y), trackStroke, StrokeCap.Round)
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

@Composable
private fun timelineAxisLabels(): List<String> = listOf(
    stringResource(R.string.device_cooling_program_axis_00),
    stringResource(R.string.device_cooling_program_axis_08),
    stringResource(R.string.device_cooling_program_axis_14),
    stringResource(R.string.device_cooling_program_axis_20),
    stringResource(R.string.device_cooling_program_axis_24)
)

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

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
