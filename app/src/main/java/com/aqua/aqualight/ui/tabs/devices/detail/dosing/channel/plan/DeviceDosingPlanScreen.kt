package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuChoiceChip
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuDivider
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuEditableValueRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuGeometry
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSection
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuSelectionRow
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuToggle
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuValueRow
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuColors
import com.aqua.aqualight.ui.common.devicemenu.aquaDeviceMenuTypography
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.model.DosingWeekday

@Immutable
internal data class DeviceDosingPlanUiState(
    val dailyDoseMicroliters: Long,
    val selectedScheduleMode: DosingPlanScheduleMode,
    val scheduleEnabled: Boolean,
    val recurrenceState: DosingPlanRecurrenceState,
    val supportedModes: Set<DosingPlanScheduleMode>,
    val recurrenceSupported: Boolean,
    val editorEnabled: Boolean,
    val canSave: Boolean
)

internal data class DosingPlanRecurrenceActions(
    val onEveryDayClick: () -> Unit,
    val onWeekdaySelectionChange: (DosingWeekday, Boolean) -> Unit
)

internal data class DeviceDosingPlanActions(
    val onScheduleOptionClick: (DosingPlanScheduleMode) -> Unit,
    val onDailyDoseClick: (() -> Unit)?,
    val onScheduleEnabledChange: (Boolean) -> Unit,
    val recurrence: DosingPlanRecurrenceActions,
    val onSaveClick: (() -> Unit)?
)

/** Dosing Plan child feature rendered from a firmware-independent application state. */
@Composable
internal fun DeviceDosingPlanScreen(
    state: DeviceDosingPlanUiState,
    actions: DeviceDosingPlanActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceMenuColors()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = AquaDeviceMenuGeometry.screenHorizontalPadding,
            top = AquaDeviceMenuGeometry.screenTopPadding,
            end = AquaDeviceMenuGeometry.screenHorizontalPadding,
            bottom = AquaDeviceMenuGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.sectionGap)
    ) {
        item(key = PLAN_STATUS_KEY) {
            PlanStatusSection(
                scheduleEnabled = state.scheduleEnabled,
                enabled = state.editorEnabled,
                onScheduleEnabledChange = actions.onScheduleEnabledChange
            )
        }
        item(key = PLAN_AMOUNT_KEY) {
            PlanAmountSection(state = state, onDailyDoseClick = actions.onDailyDoseClick)
        }
        item(key = PLAN_SCHEDULE_KEY) {
            PlanScheduleSection(state = state, onScheduleOptionClick = actions.onScheduleOptionClick)
        }
        if (state.recurrenceSupported) {
            item(key = PLAN_RECURRENCE_KEY) {
                PlanRecurrenceSection(state = state, actions = actions.recurrence)
            }
        }
        item(key = PLAN_SAVE_KEY) {
            PlanSaveAction(state = state, onSaveClick = actions.onSaveClick)
        }
    }
}

@Composable
private fun PlanStatusSection(
    scheduleEnabled: Boolean,
    enabled: Boolean,
    onScheduleEnabledChange: (Boolean) -> Unit
) {
    PlanSection(R.string.device_dosing_detail_schedule_status_section) {
        PlanToggleRow(
            titleRes = R.string.device_dosing_detail_activate_schedule,
            descriptionRes = R.string.device_dosing_detail_activate_schedule_description,
            checked = scheduleEnabled,
            enabled = enabled,
            onCheckedChange = onScheduleEnabledChange
        )
    }
}

@Composable
private fun PlanAmountSection(
    state: DeviceDosingPlanUiState,
    onDailyDoseClick: (() -> Unit)?
) {
    PlanSection(
        titleRes = R.string.device_dosing_detail_amount_section,
        enabled = state.scheduleEnabled && state.editorEnabled
    ) {
        val dailyDoseValue = stringResource(
            R.string.device_dosing_channel_daily_dose_format,
            state.dailyDoseMicroliters.toDouble() / MICROLITERS_PER_MILLILITER
        )
        val description = stringResource(
            if (state.selectedScheduleMode == DosingPlanScheduleMode.TIMER) {
                R.string.device_dosing_detail_timer_daily_dose_description
            } else {
                R.string.device_dosing_detail_daily_dose_description
            }
        )
        if (onDailyDoseClick == null) {
            AquaDeviceMenuValueRow(
                label = stringResource(R.string.device_dosing_detail_daily_dose),
                value = dailyDoseValue,
                description = description,
                tone = AquaDeviceMenuTone.ACCENT
            )
        } else {
            AquaDeviceMenuEditableValueRow(
                label = stringResource(R.string.device_dosing_detail_daily_dose),
                value = dailyDoseValue,
                description = description,
                iconRes = R.drawable.ic_dosing_schedule_24,
                onClick = onDailyDoseClick,
                enabled = state.scheduleEnabled && state.editorEnabled
            )
        }
    }
}

@Composable
private fun PlanScheduleSection(
    state: DeviceDosingPlanUiState,
    onScheduleOptionClick: (DosingPlanScheduleMode) -> Unit
) {
    PlanSection(
        titleRes = R.string.device_dosing_detail_schedule_section,
        enabled = state.scheduleEnabled && state.editorEnabled
    ) {
        DOSING_PLAN_SCHEDULE_OPTIONS
            .filter { option -> option.mode in state.supportedModes }
            .forEachIndexed { index, option ->
                if (index > 0) {
                    AquaDeviceMenuDivider(
                        startIndent = AquaDeviceMenuGeometry.selectionDividerIndent
                    )
                }
                AquaDeviceMenuSelectionRow(
                    text = stringResource(option.labelRes),
                    selected = option.mode == state.selectedScheduleMode,
                    onClick = if (state.scheduleEnabled && state.editorEnabled) {
                        { onScheduleOptionClick(option.mode) }
                    } else {
                        null
                    }
                )
            }
    }
}

@Composable
private fun PlanRecurrenceSection(
    state: DeviceDosingPlanUiState,
    actions: DosingPlanRecurrenceActions
) {
    PlanSection(
        titleRes = R.string.device_dosing_detail_recurrence_section,
        enabled = state.scheduleEnabled && state.editorEnabled
    ) {
        AquaDeviceMenuSelectionRow(
            text = stringResource(R.string.device_dosing_channel_every_day),
            selected = state.recurrenceState.isEveryDay,
            showTrailingIcon = false,
            onClick = actions.onEveryDayClick.takeIf {
                state.scheduleEnabled && state.editorEnabled
            }
        )
        AquaDeviceMenuDivider(startIndent = AquaDeviceMenuGeometry.selectionDividerIndent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AquaDeviceMenuGeometry.sectionContentPadding),
            horizontalArrangement = Arrangement.spacedBy(AquaDeviceMenuGeometry.compactGap)
        ) {
            DOSING_PLAN_WEEKDAYS.forEach { weekday ->
                AquaDeviceMenuChoiceChip(
                    text = stringResource(weekday.shortLabelRes),
                    selected = weekday in state.recurrenceState.selectedDays,
                    modifier = Modifier.weight(1f),
                    compact = true,
                    onSelectedChange = if (state.scheduleEnabled && state.editorEnabled) {
                        { selected -> actions.onWeekdaySelectionChange(weekday, selected) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun PlanSaveAction(
    state: DeviceDosingPlanUiState,
    onSaveClick: (() -> Unit)?
) {
    AquaGuidedFlowButton(
        text = stringResource(R.string.device_dosing_detail_save_plan),
        onClick = { onSaveClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canSave &&
            onSaveClick != null
    )
}

@Composable
private fun PlanSection(
    @StringRes titleRes: Int,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AquaDeviceMenuSection(
        title = stringResource(titleRes),
        enabled = enabled,
        content = content
    )
}

@Composable
private fun PlanToggleRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = aquaDeviceMenuColors()
    val typography = aquaDeviceMenuTypography(colors)
    val stateLabel = stringResource(
        if (checked) R.string.device_dosing_detail_state_on else R.string.device_dosing_detail_state_off
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                enabled = enabled,
                onValueChange = onCheckedChange
            )
            .padding(AquaDeviceMenuGeometry.sectionContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = AquaDeviceMenuGeometry.compactGap)
        ) {
            BasicText(text = stringResource(titleRes), style = typography.rowTitle)
            BasicText(
                text = stringResource(descriptionRes),
                modifier = Modifier.padding(top = AquaDeviceMenuGeometry.rowTextGap),
                style = typography.rowBody
            )
        }
        AquaDeviceMenuToggle(
            checked = checked,
            contentDescription = stringResource(
                R.string.device_dosing_detail_toggle_description,
                stateLabel
            )
        )
    }
}

private const val MICROLITERS_PER_MILLILITER = 1_000.0
private const val PLAN_STATUS_KEY = "dosing-plan-status"
private const val PLAN_AMOUNT_KEY = "dosing-plan-amount"
private const val PLAN_SCHEDULE_KEY = "dosing-plan-schedule"
private const val PLAN_RECURRENCE_KEY = "dosing-plan-recurrence"
private const val PLAN_SAVE_KEY = "dosing-plan-save"
