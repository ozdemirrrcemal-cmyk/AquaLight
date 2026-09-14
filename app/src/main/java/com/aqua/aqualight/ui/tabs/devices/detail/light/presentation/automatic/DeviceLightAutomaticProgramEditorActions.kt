package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticWeekday

internal data class DeviceLightAutomaticProgramEditorActions(
    val days: DeviceLightAutomaticDayActions,
    val schedule: DeviceLightAutomaticScheduleActions,
    val onChannelChanged: (DeviceLightAutomaticChannel, Int) -> Unit,
    val onPresetClick: () -> Unit,
    val onCancelClick: () -> Unit,
    val onSaveClick: () -> Unit
)

internal data class DeviceLightAutomaticDayActions(
    val onEveryDayClick: () -> Unit,
    val onWeekdaysClick: () -> Unit,
    val onWeekendClick: () -> Unit,
    val onDayClick: (DeviceLightAutomaticWeekday) -> Unit
)

internal data class DeviceLightAutomaticScheduleActions(
    val onStartTimeClick: () -> Unit,
    val onEndTimeClick: () -> Unit,
    val onRampClick: (Long) -> Unit
)
