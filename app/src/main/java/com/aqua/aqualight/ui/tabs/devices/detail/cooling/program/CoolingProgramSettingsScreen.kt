@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

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
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSlider
import com.aqua.aqualight.ui.common.cooling.AquaCoolingFanPercentSliderState
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import java.time.LocalTime

private data class ProgramSlotCardModel(
    val slot: DeviceCoolingProgramSlot,
    val selected: Boolean
)

private data class ProgramSlotCardActions(
    val onHeaderClick: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onStartTimeClick: () -> Unit,
    val onEndTimeClick: () -> Unit,
    val onFanLimitChange: (Int) -> Unit
)

@Composable
internal fun DeviceCoolingProgramSettingsScreen(
    state: DeviceCoolingProgramSettingsUiState,
    actions: DeviceCoolingProgramSettingsActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    val context = LocalContext.current
    val now = LocalTime.now()
    val nowMinutes = now.hour * MINUTES_PER_HOUR + now.minute

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
                activeSlot = state.activeSlotAt(nowMinutes),
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
                onAddSlot = actions.onAddSlot
            )
        }
        state.slots.forEachIndexed { slotIndex, slot ->
            item(key = "slot-$slotIndex") {
                ProgramSlotCard(
                    model = ProgramSlotCardModel(
                        slot = slot,
                        selected = slotIndex == state.selectedSlotIndex
                    ),
                    context = context,
                    colors = colors,
                    typography = typography,
                    actions = ProgramSlotCardActions(
                        onHeaderClick = { actions.onSlotClick(slotIndex) },
                        onDeleteClick = { actions.onDeleteSlot(slotIndex) },
                        onStartTimeClick = { actions.onStartTimeClick(slotIndex) },
                        onEndTimeClick = { actions.onEndTimeClick(slotIndex) },
                        onFanLimitChange = { percent ->
                            actions.onFanLimitChange(slotIndex, percent)
                        }
                    )
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
                    drawProgramSlot(slot, y, trackStroke, size.width, colors)
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
    model: ProgramSlotCardModel,
    context: Context,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    actions: ProgramSlotCardActions
) {
    val slot = model.slot
    val selected = model.selected
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
                    .clickable(role = Role.Button, onClick = actions.onHeaderClick)
                    .padding(vertical = AquaCoolingProgramGeometry.slotHeaderVerticalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = formatProgramTimeRange(context, slot),
                    style = typography.title.copy(
                        color = if (selected) colors.accent else colors.primaryText
                    ),
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
                    colors = colors,
                    typography = typography,
                    onDeleteClick = actions.onDeleteClick
                )
                ProgramEditorRow(
                    label = stringResource(R.string.device_cooling_program_start_time),
                    value = formatProgramTime(context, slot.startMinutes),
                    colors = colors,
                    typography = typography,
                    onClick = actions.onStartTimeClick
                )
                ProgramEditorRow(
                    label = stringResource(R.string.device_cooling_program_end_time),
                    value = formatProgramTime(context, slot.endMinutes),
                    colors = colors,
                    typography = typography,
                    onClick = actions.onEndTimeClick
                )
                ProgramFanLimitEditor(
                    value = slot.fanLimitPercent,
                    colors = colors,
                    typography = typography,
                    onValueChange = actions.onFanLimitChange
                )
            }
        }
    }
}

@Composable
private fun ProgramEditorHeader(
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

@Composable
private fun ProgramFanLimitEditor(
    value: Int,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AquaCoolingProgramGeometry.editorRowShape)
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
                shape = AquaCoolingProgramGeometry.editorRowShape
            )
            .padding(
                horizontal = AquaCoolingProgramGeometry.editorRowHorizontalPadding,
                vertical = AquaCoolingProgramGeometry.editorRowVerticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingProgramGeometry.editorRowGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_program_fan_limit),
                style = typography.body.copy(color = colors.primaryText),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            BasicText(
                text = stringResource(R.string.device_cooling_percent_value_format, value),
                style = typography.body.copy(
                    color = colors.primaryText,
                    textAlign = TextAlign.End
                ),
                maxLines = 1
            )
        }
        AquaCoolingFanPercentSlider(
            state = AquaCoolingFanPercentSliderState(
                percent = value,
                enabled = true,
                stepPercent = DeviceCoolingProgramPolicy.fanLimitStepPercent
            ),
            colors = colors,
            onValueChanged = onValueChange
        )
    }
}

@Composable
private fun programSlotSummary(slot: DeviceCoolingProgramSlot): String = buildString {
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
private fun timelineAxisLabels(): List<String> = listOf(
    stringResource(R.string.device_cooling_program_axis_00),
    stringResource(R.string.device_cooling_program_axis_08),
    stringResource(R.string.device_cooling_program_axis_14),
    stringResource(R.string.device_cooling_program_axis_20),
    stringResource(R.string.device_cooling_program_axis_24)
)

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
