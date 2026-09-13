package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerInteractionStyle
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardColors
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard.DeviceTimerStateMessageCard
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard.TimerCardStyle
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard.TimerStatePill
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.common.toCommercialTimerError

@Composable
internal fun DeviceTimerProgramScreen(
    state: DeviceTimerProgramUiState,
    actions: DeviceTimerProgramActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaTimerDashboardColors()
    val typography = aquaTimerDashboardTypography(colors)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AquaTimerDashboardGeometry.screenHorizontalPadding),
        contentPadding = PaddingValues(
            top = AquaTimerDashboardGeometry.screenTopPadding,
            bottom = AquaTimerDashboardGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.cardGap)
    ) {
        timerProgramItems(state, actions, TimerCardStyle(colors, typography))
    }
}

private fun LazyListScope.timerProgramItems(
    state: DeviceTimerProgramUiState,
    actions: DeviceTimerProgramActions,
    style: TimerCardStyle
) {
    timerProgramFailureItem(state)
    if (state.loadState == DeviceTimerProgramLoadState.CONTENT) {
        timerProgramCapacityItem(state, style)
        timerProgramValidationItem(state)
        items(state.schedules, key = DeviceTimerProgramDraft::slotId) { schedule ->
            TimerProgramCard(
                schedule = schedule,
                enabled = state.editable && !state.saving,
                spansMidnightSupported = state.spansMidnightSupported,
                actions = actions,
                style = style
            )
        }
        item(key = "add") {
            TimerEditorButton(
                label = if (state.canAdd) {
                    stringResource(R.string.device_timer_program_add)
                } else {
                    stringResource(R.string.device_timer_program_limit_reached)
                },
                enabled = state.canAdd,
                onClick = actions.onAdd,
                style = style
            )
        }
    }
}

private fun LazyListScope.timerProgramFailureItem(state: DeviceTimerProgramUiState) {
    state.failure?.takeIf { state.loadState == DeviceTimerProgramLoadState.FAILED }?.let { failure ->
        item(key = "failure") {
            val copy = failure.toCommercialTimerError()
            DeviceTimerStateMessageCard(
                title = stringResource(copy.titleRes),
                message = stringResource(copy.messageRes)
            )
        }
    }
}

private fun LazyListScope.timerProgramCapacityItem(
    state: DeviceTimerProgramUiState,
    style: TimerCardStyle
) {
    item(key = "capacity") {
        BasicText(
            text = pluralStringResource(
                R.plurals.device_timer_program_capacity,
                state.maxSchedules,
                state.maxSchedules
            ),
            style = style.typography.caption.copy(color = style.colors.secondaryText),
            modifier = Modifier.padding(
                horizontal = AquaDeviceCardGeometry.contentHorizontalPadding,
                vertical = AquaTimerDashboardGeometry.screenTopPadding
            )
        )
    }
}

private fun LazyListScope.timerProgramValidationItem(state: DeviceTimerProgramUiState) {
    state.validationIssue?.takeIf { state.dirty }?.let { issue ->
        item(key = "validation") {
            DeviceTimerStateMessageCard(
                title = stringResource(R.string.device_timer_error_invalid_configuration_title),
                message = stringResource(
                    if (issue == DeviceTimerProgramValidationIssue.OVERLAPPING_PROGRAMS) {
                        R.string.device_timer_program_overlap
                    } else {
                        R.string.device_timer_program_invalid
                    }
                )
            )
        }
    }
}

@Composable
private fun TimerProgramCard(
    schedule: DeviceTimerProgramDraft,
    enabled: Boolean,
    spansMidnightSupported: Boolean,
    actions: DeviceTimerProgramActions,
    style: TimerCardStyle
) {
    AquaDeviceCardSurface(
        modifier = Modifier.alpha(
            if (enabled) 1f else AquaTimerInteractionStyle.disabledContentAlpha
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.editorSectionGap)
        ) {
            TimerProgramHeader(schedule, enabled, actions, style)
            TimerWeekdaySelector(schedule, enabled, actions, style)
            TimerProgramTimeRow(schedule, enabled, actions, style)
            if (schedule.spansMidnight && spansMidnightSupported) {
                BasicText(
                    text = stringResource(R.string.device_timer_program_spans_midnight),
                    style = style.typography.caption.copy(color = style.colors.accent)
                )
            }
        }
    }
}

@Composable
private fun TimerProgramHeader(
    schedule: DeviceTimerProgramDraft,
    enabled: Boolean,
    actions: DeviceTimerProgramActions,
    style: TimerCardStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.detailRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = schedule.name,
            style = style.typography.title,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled) { actions.onNameClick(schedule.slotId) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier.clickable(enabled = enabled, role = Role.Switch) {
                actions.onEnabledToggle(schedule.slotId)
            }
        ) {
            TimerStatePill(
                label = stringResource(
                    if (schedule.enabled) R.string.device_timer_program_enabled_value
                    else R.string.device_timer_program_disabled_value
                ),
                active = schedule.enabled,
                colors = style.colors,
                typography = style.typography
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_delete_24),
            contentDescription = stringResource(R.string.device_timer_program_delete),
            modifier = Modifier
                .size(AquaTimerDashboardGeometry.detailRowIconSize)
                .clickable(enabled = enabled) { actions.onDelete(schedule.slotId) },
            colorFilter = ColorFilter.tint(style.colors.danger)
        )
    }
}

@Composable
private fun TimerWeekdaySelector(
    schedule: DeviceTimerProgramDraft,
    enabled: Boolean,
    actions: DeviceTimerProgramActions,
    style: TimerCardStyle
) {
    val weekdayLabels = stringArrayResource(R.array.device_timer_weekday_short_labels)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.editorWeekdayGap)
    ) {
        weekdayLabels.forEachIndexed { index, label ->
            val selected = schedule.weekdays.getOrNull(index) == true
            Box(
                modifier = Modifier
                    .weight(1f)
                    .size(AquaTimerDashboardGeometry.editorWeekdaySize)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            style.colors.accent.copy(
                                alpha = AquaTimerDashboardAlpha.selectedBackground
                            )
                        } else {
                            style.colors.mediaSurface.copy(
                                alpha = AquaTimerDashboardAlpha.idleBackground
                            )
                        }
                    )
                    .border(
                        AquaDeviceCardGeometry.outlineWidth,
                        if (selected) style.colors.accent else style.colors.outline,
                        CircleShape
                    )
                    .clickable(enabled = enabled) {
                        actions.onWeekdayToggle(schedule.slotId, index)
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = label,
                    style = style.typography.micro.copy(
                        color = if (selected) {
                            style.colors.primaryText
                        } else {
                            style.colors.secondaryText
                        },
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TimerProgramTimeRow(
    schedule: DeviceTimerProgramDraft,
    enabled: Boolean,
    actions: DeviceTimerProgramActions,
    style: TimerCardStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.editorRowGap)
    ) {
        TimerTimeChip(
            content = TimerTimeChipContent(
                label = stringResource(R.string.device_timer_program_start),
                value = formatMinutesOfDay(schedule.startMinutesOfDay),
                enabled = enabled
            ),
            onClick = { actions.onStartTimeClick(schedule.slotId) },
            style = style,
            modifier = Modifier.weight(1f)
        )
        TimerTimeChip(
            content = TimerTimeChipContent(
                label = stringResource(R.string.device_timer_program_end),
                value = formatMinutesOfDay(schedule.endMinutesOfDay),
                enabled = enabled
            ),
            onClick = { actions.onEndTimeClick(schedule.slotId) },
            style = style,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimerTimeChip(
    content: TimerTimeChipContent,
    onClick: () -> Unit,
    style: TimerCardStyle,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .clip(AquaTimerDashboardGeometry.actionShape)
            .background(
                style.colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground)
            )
            .border(
                AquaDeviceCardGeometry.outlineWidth,
                style.colors.outline,
                AquaTimerDashboardGeometry.actionShape
            )
            .clickable(enabled = content.enabled, onClick = onClick)
            .padding(AquaTimerDashboardGeometry.editorRowPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = content.label,
            style = style.typography.micro.copy(color = style.colors.secondaryText)
        )
        BasicText(
            text = content.value,
            style = style.typography.body.copy(color = style.colors.primaryText)
        )
    }
}

@Composable
private fun TimerEditorButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    style: TimerCardStyle
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else AquaTimerInteractionStyle.disabledContentAlpha)
            .clip(AquaTimerDashboardGeometry.actionShape)
            .background(
                style.colors.accent.copy(alpha = AquaTimerDashboardAlpha.actionBackground)
            )
            .border(
                AquaDeviceCardGeometry.outlineWidth,
                style.colors.accent,
                AquaTimerDashboardGeometry.actionShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(AquaTimerDashboardGeometry.actionPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = style.typography.caption.copy(color = style.colors.accent)
        )
    }
}

private data class TimerTimeChipContent(
    val label: String,
    val value: String,
    val enabled: Boolean
)

@Composable
private fun formatMinutesOfDay(minutesOfDay: Int): String =
    LocaleFormatter.formatTimeOfDay24Hour(LocalContext.current, minutesOfDay)
