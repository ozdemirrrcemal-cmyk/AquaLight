package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.channel

internal data class DeviceTimerChannelActions(
    val onPowerClick: () -> Unit,
    val onTimedControlClick: () -> Unit,
    val onProgramsClick: () -> Unit,
    val onWorkModeClick: () -> Unit,
    val onChannelNameClick: () -> Unit
)
