package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import android.content.Context
import androidx.annotation.StringRes
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
import androidx.compose.ui.layout.Layout
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
import kotlin.math.roundToInt

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
            ProgramTimelineAxis(colors = colors, typography = typography)
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
private fun ProgramTimelineAxis(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val ticks = coolingProgramTimelineTicks()
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            ticks.forEach { tick ->
                BasicText(
                    text = stringResource(tick.labelRes),
                    style = typography.micro.copy(color = colors.secondaryText),
                    maxLines = 1
                )
            }
        }
    ) { measurables, constraints ->
        val labelConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable -> measurable.measure(labelConstraints) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { placeable -> placeable.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val tickCenter = (
                    width * (ticks[index].minutesOfDay.toFloat() / MINUTES_PER_DAY)
                ).roundToInt()
                val left = (tickCenter - placeable.width / 2)
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(left, (height - placeable.height) / 2)
            }
        }
    }
}

internal data class CoolingProgramTimelineTick(
    val minutesOfDay: Int,
    @StringRes val labelRes: Int
)

internal fun coolingProgramTimelineTicks(): List<CoolingProgramTimelineTick> = listOf(
    CoolingProgramTimelineTick(0, R.string.device_cooling_program_axis_00),
    CoolingProgramTimelineTick(TIMELINE_06_MINUTES, R.string.device_cooling_program_axis_06),
    CoolingProgramTimelineTick(TIMELINE_12_MINUTES, R.string.device_cooling_program_axis_12),
    CoolingProgramTimelineTick(TIMELINE_18_MINUTES, R.string.device_cooling_program_axis_18),
    CoolingProgramTimelineTick(MINUTES_PER_DAY, R.string.device_cooling_program_axis_24)
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
private const val TIMELINE_06_MINUTES = 6 * MINUTES_PER_HOUR
private const val TIMELINE_12_MINUTES = 12 * MINUTES_PER_HOUR
private const val TIMELINE_18_MINUTES = 18 * MINUTES_PER_HOUR
