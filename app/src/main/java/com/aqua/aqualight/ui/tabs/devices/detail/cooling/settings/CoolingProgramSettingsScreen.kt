@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingProgramAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingProgramGeometry
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import java.time.LocalTime

@Composable
internal fun DeviceCoolingProgramSettingsScreen(
    state: DeviceCoolingProgramSettingsUiState,
    onSlotClick: (String) -> Unit,
    onAddSlot: () -> Unit,
    onDeleteSlot: (String) -> Unit,
    onStartTimeClick: (String) -> Unit,
    onEndTimeClick: (String) -> Unit,
    onStartTemperatureClick: (String) -> Unit,
    onMaximumTemperatureClick: (String) -> Unit,
    onFanLimitClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    val context = LocalContext.current
    val now = LocalTime.now()
    val nowMinutes = now.hour * MINUTES_PER_HOUR + now.minute
    val activeSlot = state.activeSlotAt(nowMinutes)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AquaCoolingProgramGeometry.screenHorizontalPadding,
            top = AquaCoolingProgramGeometry.screenTopPadding,
            end = AquaCoolingProgramGeometry.screenHorizontalPadding,
            bottom = AquaCoolingProgramGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.sectionGap)
    ) {
        item(key = "active") {
            ProgramActiveCard(
                activeSlot = activeSlot,
                context = context,
                colors = colors,
                typography = typography
            )
        }
        item(key = "timeline") {
            ProgramTimelineCard(
                slots = state.slots,
                nowMinutes = nowMinutes,
                colors = colors,
                typography = typography
            )
        }
        item(key = "slots-header") {
            ProgramSlotsHeader(
                canAddSlot = state.canAddSlot,
                colors = colors,
                typography = typography,
                onAddSlot = onAddSlot
            )
        }
        state.slots.forEach { slot ->
            item(key = "slot-${slot.id}") {
                ProgramSlotCard(
                    slot = slot,
                    selected = slot.id == state.selectedSlotId,
                    context = context,
                    colors = colors,
                    typography = typography,
                    onHeaderClick = { onSlotClick(slot.id) },
                    onDeleteClick = { onDeleteSlot(slot.id) },
                    onStartTimeClick = { onStartTimeClick(slot.id) },
                    onEndTimeClick = { onEndTimeClick(slot.id) },
                    onStartTemperatureClick = { onStartTemperatureClick(slot.id) },
                    onMaximumTemperatureClick = { onMaximumTemperatureClick(slot.id) },
                    onFanLimitClick = { onFanLimitClick(slot.id) }
                )
            }
        }
    }
}

@Composable
private fun ProgramActiveCard(
    activeSlot: DeviceCoolingProgramSlot?,
    context: Context,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaCoolingDashboardCardSurface(
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
                    text = if (activeSlot == null) {
                        stringResource(R.string.device_cooling_program_outside_automatic)
                    } else {
                        stringResource(
                            R.string.device_cooling_program_active_format,
                            formatProgramTimeRange(context, activeSlot),
                            programSlotLabel(activeSlot.label)
                        )
                    },
                    style = typography.caption.copy(color = colors.secondaryText),
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .size(AquaCoolingProgramGeometry.activeDotSize)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = AquaCoolingProgramAlpha.activeDot))
            )
        }
    }
}

@Composable
private fun ProgramTimelineCard(
    slots: List<DeviceCoolingProgramSlot>,
    nowMinutes: Int,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaCoolingDashboardCardSurface(
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
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AquaCoolingProgramGeometry.timelineHeight)
            ) {
                val y = size.height * 0.50f
                val trackStroke = AquaCoolingProgramGeometry.timelineTrackHeight.toPx()
                drawLine(
                    color = colors.secondaryText.copy(alpha = AquaCoolingProgramAlpha.timelineTrack),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round
                )
                slots.forEach { slot ->
                    drawProgramSlot(
                        slot = slot,
                        y = y,
                        trackStroke = trackStroke,
                        width = size.width,
                        colors = colors
                    )
                }
                val nowX = size.width * (nowMinutes.toFloat() / MINUTES_PER_DAY)
                drawLine(
                    color = colors.primaryText.copy(alpha = AquaCoolingProgramAlpha.timelineNow),
                    start = Offset(nowX, y - trackStroke * 1.35f),
                    end = Offset(nowX, y + trackStroke * 1.35f),
                    strokeWidth = AquaCoolingProgramGeometry.timelineNowStrokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = colors.primaryText,
                    radius = AquaCoolingProgramGeometry.timelineMarkerRadius.toPx(),
                    center = Offset(nowX, y)
                )
            }
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
            if (slots.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.timelineLegendGap)
                ) {
                    slots.take(MAX_TIMELINE_LEGEND_ITEMS).forEach { slot ->
                        BasicText(
                            text = programSlotLabel(slot.label),
                            style = typography.micro.copy(
                                color = colors.secondaryText,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                }
            }
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
    val alpha = when (slot.label) {
        DeviceCoolingProgramSlotLabel.QUIET -> AquaCoolingProgramAlpha.timelineQuiet
        DeviceCoolingProgramSlotLabel.INTENSIVE -> AquaCoolingProgramAlpha.timelineIntensive
        DeviceCoolingProgramSlotLabel.NIGHT -> AquaCoolingProgramAlpha.timelineNight
        DeviceCoolingProgramSlotLabel.CUSTOM -> AquaCoolingProgramAlpha.timelineCustom
    }
    val startX = width * (slot.startMinutes.toFloat() / MINUTES_PER_DAY)
    val endX = width * (slot.endMinutes.toFloat() / MINUTES_PER_DAY)
    val color = colors.accent.copy(alpha = alpha)
    if (slot.startMinutes < slot.endMinutes) {
        drawLine(color, Offset(startX, y), Offset(endX, y), trackStroke, StrokeCap.Round)
    } else {
        drawLine(color, Offset(startX, y), Offset(width, y), trackStroke, StrokeCap.Round)
        drawLine(color, Offset(0f, y), Offset(endX, y), trackStroke, StrokeCap.Round)
    }
}

@Composable
private fun ProgramSlotsHeader(
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
                .alpha(if (canAddSlot) 1f else AquaCoolingProgramAlpha.inlineActionDisabled)
                .padding(
                    horizontal = AquaCoolingProgramGeometry.inlineActionHorizontalPadding,
                    vertical = AquaCoolingProgramGeometry.inlineActionVerticalPadding
                ),
            maxLines = 1
        )
    }
}

@Composable
private fun ProgramSlotCard(
    slot: DeviceCoolingProgramSlot,
    selected: Boolean,
    context: Context,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onHeaderClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onStartTemperatureClick: () -> Unit,
    onMaximumTemperatureClick: () -> Unit,
    onFanLimitClick: () -> Unit
) {
    val shape = RoundedCornerShape(AquaCoolingDashboardGeometry.cardCornerRadius)
    val selectedModifier = if (selected) {
        Modifier.border(
            width = AquaCoolingProgramGeometry.selectedSlotOutlineWidth,
            color = colors.accent.copy(alpha = AquaCoolingProgramAlpha.slotSelectedOutline),
            shape = shape
        )
    } else {
        Modifier
    }

    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(selectedModifier)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.slotMetricGap)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AquaCoolingProgramGeometry.slotHeaderShape)
                    .clickable(role = Role.Button, onClick = onHeaderClick)
                    .padding(vertical = AquaCoolingProgramGeometry.slotHeaderVerticalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = formatProgramTimeRange(context, slot),
                    style = typography.title.copy(
                        color = if (selected) colors.accent else colors.primaryText
                    ),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(AquaCoolingProgramGeometry.slotHeaderGap))
                BasicText(
                    text = programSlotLabel(slot.label),
                    style = typography.micro.copy(color = colors.secondaryText),
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                ProgramChevron(colors = colors, expanded = selected)
            }

            BasicText(
                text = programSlotSummary(slot),
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = 1
            )

            if (selected) {
                Spacer(modifier = Modifier.height(AquaCoolingProgramGeometry.expandedSectionTopGap))
                ProgramDivider(colors)
                Spacer(modifier = Modifier.height(AquaCoolingProgramGeometry.expandedSectionTopGap))
                ProgramEditorHeader(
                    deletable = slot.label == DeviceCoolingProgramSlotLabel.CUSTOM,
                    colors = colors,
                    typography = typography,
                    onDeleteClick = onDeleteClick
                )
                ProgramEditorRow(
                    label = stringResource(R.string.device_cooling_program_start_time),
                    value = formatProgramTime(context, slot.startMinutes),
                    colors = colors,
                    typography = typography,
                    onClick = onStartTimeClick
                )
                ProgramEditorRow(
                    label = stringResource(R.string.device_cooling_program_end_time),
                    value = formatProgramTime(context, slot.endMinutes),
                    colors = colors,
                    typography = typography,
                    onClick = onEndTimeClick
                )
                ProgramEditorRow(
                    label = stringResource(R.string.device_cooling_fan_start_temperature),
                    value = programTemperatureText(slot.startTemperatureC),
                    colors = colors,
                    typography = typography,
                    onClick = onStartTemperatureClick
                )
                ProgramEditorRow(
                    label = stringResource(R.string.device_cooling_max_speed_temperature),
                    value = programTemperatureText(slot.maximumSpeedTemperatureC),
                    colors = colors,
                    typography = typography,
                    onClick = onMaximumTemperatureClick
                )
                ProgramEditorRow(
                    label = stringResource(R.string.device_cooling_program_fan_limit),
                    value = stringResource(
                        R.string.device_cooling_percent_value_format,
                        slot.fanLimitPercent
                    ),
                    colors = colors,
                    typography = typography,
                    onClick = onFanLimitClick
                )
            }
        }
    }
}

@Composable
private fun ProgramEditorHeader(
    deletable: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val menuOffsetY = with(LocalDensity.current) {
        AquaCoolingProgramGeometry.slotChevronHeight.roundToPx()
    }
    val moreActionsDescription = stringResource(R.string.device_cooling_program_more_actions)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_cooling_program_editor_title),
            style = typography.body.copy(color = colors.primaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (deletable) {
            Box {
                Box(
                    modifier = Modifier
                        .width(AquaCoolingProgramGeometry.slotChevronWidth)
                        .height(AquaCoolingProgramGeometry.slotChevronHeight)
                        .clip(AquaCoolingProgramGeometry.inlineActionShape)
                        .clickable(
                            role = Role.Button,
                            onClick = { menuExpanded = !menuExpanded }
                        )
                        .semantics { contentDescription = moreActionsDescription },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "⋮",
                        style = typography.title.copy(color = colors.secondaryText),
                        maxLines = 1
                    )
                }
                if (menuExpanded) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, menuOffsetY),
                        onDismissRequest = { menuExpanded = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        BasicText(
                            text = stringResource(R.string.device_cooling_program_delete_slot),
                            style = typography.body.copy(color = colors.danger),
                            modifier = Modifier
                                .clip(AquaCoolingProgramGeometry.editorRowShape)
                                .background(colors.surface)
                                .border(
                                    width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                                    color = colors.outline,
                                    shape = AquaCoolingProgramGeometry.editorRowShape
                                )
                                .clickable(role = Role.Button) {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                                .padding(
                                    horizontal = AquaCoolingProgramGeometry.editorRowHorizontalPadding,
                                    vertical = AquaCoolingProgramGeometry.editorRowVerticalPadding
                                ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun programSlotSummary(slot: DeviceCoolingProgramSlot): String = buildString {
    append(programTemperatureText(slot.startTemperatureC))
    append(" → ")
    append(programTemperatureText(slot.maximumSpeedTemperatureC))
    append(" • ")
    append(stringResource(R.string.device_cooling_program_fan_limit))
    append(" ")
    append(stringResource(R.string.device_cooling_percent_value_format, slot.fanLimitPercent))
}

@Composable
private fun ProgramEditorRow(
    label: String,
    value: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit
) {
    val shape = AquaCoolingProgramGeometry.editorRowShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                AquaCoolingDashboardPalette.insetSurface.copy(
                    alpha = AquaCoolingProgramAlpha.editorRowBackground
                )
            )
            .border(
                width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                color = AquaCoolingDashboardPalette.insetOutline.copy(
                    alpha = AquaCoolingProgramAlpha.editorRowOutline
                ),
                shape = shape
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = AquaCoolingProgramGeometry.editorRowHorizontalPadding,
                vertical = AquaCoolingProgramGeometry.editorRowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = typography.body.copy(color = colors.primaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        BasicText(
            text = value,
            style = typography.body.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(AquaCoolingProgramGeometry.editorRowGap))
        ProgramChevron(colors = colors, expanded = false)
    }
}

@Composable
private fun ProgramChevron(
    colors: AquaDeviceCardColors,
    expanded: Boolean
) {
    Canvas(
        modifier = Modifier
            .width(AquaCoolingProgramGeometry.slotChevronWidth)
            .height(AquaCoolingProgramGeometry.slotChevronHeight)
    ) {
        val stroke = AquaCoolingProgramGeometry.chevronStrokeWidth.toPx()
        if (expanded) {
            drawLine(
                color = colors.accent,
                start = Offset(size.width * 0.28f, size.height * 0.42f),
                end = Offset(size.width * 0.50f, size.height * 0.64f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = colors.accent,
                start = Offset(size.width * 0.50f, size.height * 0.64f),
                end = Offset(size.width * 0.72f, size.height * 0.42f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        } else {
            drawLine(
                color = colors.accent,
                start = Offset(size.width * 0.38f, size.height * 0.28f),
                end = Offset(size.width * 0.62f, size.height * 0.50f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = colors.accent,
                start = Offset(size.width * 0.62f, size.height * 0.50f),
                end = Offset(size.width * 0.38f, size.height * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ProgramDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingProgramGeometry.expandedDividerHeight)
            .background(colors.outline.copy(alpha = AquaCoolingProgramAlpha.expandedDivider))
    )
}

@Composable
private fun programSlotLabel(label: DeviceCoolingProgramSlotLabel): String = when (label) {
    DeviceCoolingProgramSlotLabel.QUIET ->
        stringResource(R.string.device_cooling_program_label_quiet)
    DeviceCoolingProgramSlotLabel.INTENSIVE ->
        stringResource(R.string.device_cooling_program_label_intensive)
    DeviceCoolingProgramSlotLabel.NIGHT ->
        stringResource(R.string.device_cooling_program_label_night)
    DeviceCoolingProgramSlotLabel.CUSTOM ->
        stringResource(R.string.device_cooling_program_label_custom)
}

@Composable
private fun timelineAxisLabels(): List<String> = listOf(
    stringResource(R.string.device_cooling_program_axis_00),
    stringResource(R.string.device_cooling_program_axis_08),
    stringResource(R.string.device_cooling_program_axis_14),
    stringResource(R.string.device_cooling_program_axis_20),
    stringResource(R.string.device_cooling_program_axis_24)
)

@Composable
private fun programTemperatureText(value: Double): String =
    stringResource(R.string.device_cooling_temperature_value_format, value)

private fun formatProgramTimeRange(
    context: Context,
    slot: DeviceCoolingProgramSlot
): String = context.getString(
    R.string.device_cooling_program_time_range_format,
    formatProgramTime(context, slot.startMinutes),
    formatProgramTime(context, slot.endMinutes)
)

private fun formatProgramTime(context: Context, minutesOfDay: Int): String =
    LocaleFormatter.formatTimeOfDay24Hour(context, minutesOfDay)

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val MAX_TIMELINE_LEGEND_ITEMS = 3