package com.aqua.aqualight.ui.tabs.devices.detail.timer.program

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerInteractionStyle
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardColors
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerStateMessageCard
import com.aqua.aqualight.ui.tabs.devices.detail.timer.toCommercialTimerError

internal data class DeviceTimerProgramActions(
    val onAdd: () -> Unit,
    val onDelete: (Int) -> Unit,
    val onNameClick: (Int) -> Unit,
    val onEnabledToggle: (Int) -> Unit,
    val onWeekdayToggle: (Int, Int) -> Unit,
    val onStartTimeClick: (Int) -> Unit,
    val onEndTimeClick: (Int) -> Unit
)

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
        state.failure?.takeIf { state.loadState == DeviceTimerProgramLoadState.FAILED }?.let {
            item(key = "failure") {
                val copy = it.toCommercialTimerError()
                DeviceTimerStateMessageCard(
                    title = stringResource(copy.titleRes),
                    message = stringResource(copy.messageRes)
                )
            }
        }
        if (state.loadState == DeviceTimerProgramLoadState.CONTENT) {
            item(key = "summary") {
                TimerProgramSummary(state, colors, typography)
            }
            if (state.dirty && !state.valid) {
                item(key = "validation") {
                    DeviceTimerStateMessageCard(
                        title = stringResource(R.string.device_timer_error_invalid_configuration_title),
                        message = stringResource(R.string.device_timer_program_invalid)
                    )
                }
            }
            items(state.schedules, key = DeviceTimerProgramDraft::slotId) { schedule ->
                TimerProgramCard(
                    schedule = schedule,
                    enabled = state.editable && !state.saving,
                    spansMidnightSupported = state.spansMidnightSupported,
                    actions = actions,
                    colors = colors,
                    typography = typography
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
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

@Composable
private fun TimerProgramSummary(
    state: DeviceTimerProgramUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface {
        Column(verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.editorSectionGap)) {
            BasicText(text = state.channelTitle, style = typography.title)
            BasicText(
                text = pluralStringResource(
                    R.plurals.device_timer_program_capacity,
                    state.maxSchedules,
                    state.maxSchedules
                ),
                style = typography.caption.copy(color = colors.secondaryText)
            )
            BasicText(
                text = stringResource(R.string.device_timer_program_summary),
                style = typography.caption.copy(color = colors.secondaryText)
            )
        }
    }
}

@Composable
@Suppress("LongMethod", "LongParameterList")
private fun TimerProgramCard(
    schedule: DeviceTimerProgramDraft,
    enabled: Boolean,
    spansMidnightSupported: Boolean,
    actions: DeviceTimerProgramActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(modifier = Modifier.alpha(if (enabled) 1f else AquaTimerInteractionStyle.disabledContentAlpha)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.editorSectionGap)
        ) {
            TimerEditorValueRow(
                label = stringResource(R.string.device_timer_program_name),
                value = schedule.name,
                enabled = enabled,
                onClick = { actions.onNameClick(schedule.slotId) },
                colors = colors,
                typography = typography
            )
            TimerEnabledRow(
                checked = schedule.enabled,
                enabled = enabled,
                onToggle = { actions.onEnabledToggle(schedule.slotId) },
                colors = colors,
                typography = typography
            )
            TimerWeekdaySelector(schedule, enabled, actions, colors, typography)
            TimerEditorValueRow(
                label = stringResource(R.string.device_timer_program_start),
                value = formatMinutesOfDay(schedule.startMinutesOfDay),
                enabled = enabled,
                onClick = { actions.onStartTimeClick(schedule.slotId) },
                colors = colors,
                typography = typography
            )
            TimerEditorValueRow(
                label = stringResource(R.string.device_timer_program_end),
                value = formatMinutesOfDay(schedule.endMinutesOfDay),
                enabled = enabled,
                onClick = { actions.onEndTimeClick(schedule.slotId) },
                colors = colors,
                typography = typography
            )
            if (schedule.spansMidnight && spansMidnightSupported) {
                BasicText(
                    text = stringResource(R.string.device_timer_program_spans_midnight),
                    style = typography.caption.copy(color = colors.accent)
                )
            }
            TimerEditorButton(
                label = stringResource(R.string.device_timer_program_delete),
                enabled = enabled,
                onClick = { actions.onDelete(schedule.slotId) },
                colors = colors,
                typography = typography,
                danger = true
            )
        }
    }
}

@Composable
private fun TimerEnabledRow(
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(checked, enabled, Role.Switch) { onToggle() }
            .padding(vertical = AquaTimerDashboardGeometry.editorTogglePadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_timer_program_enabled),
            style = typography.body.copy(color = colors.primaryText)
        )
        BasicText(
            text = stringResource(
                if (checked) R.string.device_timer_program_enabled_value
                else R.string.device_timer_program_disabled_value
            ),
            style = typography.caption.copy(color = if (checked) colors.success else colors.secondaryText)
        )
    }
}

@Composable
private fun TimerWeekdaySelector(
    schedule: DeviceTimerProgramDraft,
    enabled: Boolean,
    actions: DeviceTimerProgramActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val weekdayLabels = stringArrayResource(R.array.device_timer_weekday_short_labels)
    Column(verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.editorSectionGap)) {
        BasicText(
            text = stringResource(R.string.device_timer_program_days),
            style = typography.caption.copy(color = colors.secondaryText)
        )
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
                        .background(
                            color = if (selected) colors.accent.copy(alpha = AquaTimerDashboardAlpha.selectedBackground)
                            else colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground),
                            shape = CircleShape
                        )
                        .border(
                            width = AquaDeviceCardGeometry.outlineWidth,
                            color = if (selected) colors.accent else colors.outline,
                            shape = CircleShape
                        )
                        .clickable(enabled = enabled) {
                            actions.onWeekdayToggle(schedule.slotId, index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = label,
                        style = typography.micro.copy(
                            color = if (selected) colors.primaryText else colors.secondaryText,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerEditorValueRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground),
                AquaTimerDashboardGeometry.actionShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(AquaTimerDashboardGeometry.editorRowPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.editorRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = typography.caption.copy(color = colors.secondaryText)
        )
        BasicText(
            text = value,
            style = typography.body.copy(color = colors.primaryText, textAlign = TextAlign.End),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun TimerEditorButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    danger: Boolean = false
) {
    val tint = if (danger) colors.danger else colors.accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = AquaTimerDashboardAlpha.actionBackground), AquaTimerDashboardGeometry.actionShape)
            .border(AquaDeviceCardGeometry.outlineWidth, tint, AquaTimerDashboardGeometry.actionShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(AquaTimerDashboardGeometry.actionPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text = label, style = typography.caption.copy(color = tint))
    }
}

@Composable
private fun formatMinutesOfDay(minutesOfDay: Int): String =
    LocaleFormatter.formatTimeOfDay24Hour(LocalContext.current, minutesOfDay)
