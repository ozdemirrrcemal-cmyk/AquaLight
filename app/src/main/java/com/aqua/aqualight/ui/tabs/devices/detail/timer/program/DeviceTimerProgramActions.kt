package com.aqua.aqualight.ui.tabs.devices.detail.timer.program

internal data class DeviceTimerProgramActions(
    val onAdd: () -> Unit,
    val onDelete: (Int) -> Unit,
    val onNameClick: (Int) -> Unit,
    val onEnabledToggle: (Int) -> Unit,
    val onWeekdayToggle: (Int, Int) -> Unit,
    val onStartTimeClick: (Int) -> Unit,
    val onEndTimeClick: (Int) -> Unit
)
