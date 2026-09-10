package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardColors
import com.aqua.aqualight.ui.common.timer.aquaTimerDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerStateMessageCard
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerStatusNotice
import com.aqua.aqualight.ui.tabs.devices.detail.timer.TimerDivider
import com.aqua.aqualight.ui.tabs.devices.detail.timer.effectiveName
import com.aqua.aqualight.ui.tabs.devices.detail.timer.timerRuntimeSummary
import com.aqua.aqualight.ui.tabs.devices.detail.timer.toCommercialTimerError
import com.aqua.aqualight.ui.tabs.devices.detail.timer.toCommercialTimerStatus

@Composable
internal fun DeviceTimerChannelScreen(
    state: DeviceTimerChannelDetailUiState,
    actions: DeviceTimerChannelActions,
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
        timerChannelStatusItems(state)
        timerChannelContentItems(state, actions, colors, typography)
    }
}

private fun LazyListScope.timerChannelStatusItems(state: DeviceTimerChannelDetailUiState) {
    state.failure?.takeIf { state.loadState == DeviceTimerChannelLoadState.FAILED }?.let {
        item(key = "failure") {
            val copy = it.toCommercialTimerError()
            DeviceTimerStateMessageCard(
                title = stringResource(copy.titleRes),
                message = stringResource(copy.messageRes)
            )
        }
    }
    val channel = state.channel ?: return
    if (!channel.clockReady) {
        item(key = "clock-unavailable") {
            val copy = DeviceTimerStatusNotice.CLOCK_UNAVAILABLE.toCommercialTimerStatus()
            DeviceTimerStateMessageCard(
                title = stringResource(copy.titleRes),
                message = stringResource(copy.messageRes)
            )
        }
    }
    if (state.runtimeLocked) {
        item(key = "runtime-locked") {
            val copy = DeviceTimerStatusNotice.RUNTIME_LOCKED.toCommercialTimerStatus()
            DeviceTimerStateMessageCard(
                title = stringResource(copy.titleRes),
                message = stringResource(copy.messageRes)
            )
        }
    }
    if (channel.outputHealth == DeviceTimerOutputHealth.HARDWARE_FAULT) {
        item(key = "output-fault") {
            DeviceTimerStateMessageCard(
                title = stringResource(R.string.device_timer_error_hardware_failure_title),
                message = stringResource(R.string.device_timer_error_hardware_failure_message)
            )
        }
    }
}

private fun LazyListScope.timerChannelContentItems(
    state: DeviceTimerChannelDetailUiState,
    actions: DeviceTimerChannelActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    if (state.channel == null) return
    item(key = "hero") {
        TimerChannelHero(
            state = state,
            colors = colors,
            typography = typography,
            onPowerClick = actions.onPowerClick
        )
    }
    item(key = "actions") {
        TimerChannelActionCard(state, actions, colors, typography)
    }
}

@Composable
private fun TimerChannelActionCard(
    state: DeviceTimerChannelDetailUiState,
    actions: DeviceTimerChannelActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface {
        Column(modifier = Modifier.fillMaxWidth()) {
            TimerProgramsRow(state, actions, colors, typography)
            TimerDivider(colors)
            TimerTimedControlRow(state, actions, colors, typography)
            TimerDivider(colors)
            TimerWorkModeRow(state, actions, colors, typography)
            TimerDivider(colors)
            TimerChannelNameRow(state, actions, colors, typography)
        }
    }
}

@Composable
private fun TimerProgramsRow(
    state: DeviceTimerChannelDetailUiState,
    actions: DeviceTimerChannelActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val scheduleCount = checkNotNull(state.channel).scheduleCount
    TimerChannelDetailRow(
        iconRes = R.drawable.ic_dosing_schedule_24,
        title = stringResource(R.string.device_timer_channel_programs),
        value = if (scheduleCount == 0) {
            stringResource(R.string.device_timer_schedule_none)
        } else {
            pluralStringResource(R.plurals.device_timer_schedule_count, scheduleCount, scheduleCount)
        },
        enabled = state.scheduleReadEnabled && !state.mutationPending,
        onClick = actions.onProgramsClick,
        colors = colors,
        typography = typography
    )
}

@Composable
private fun TimerTimedControlRow(
    state: DeviceTimerChannelDetailUiState,
    actions: DeviceTimerChannelActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val channel = checkNotNull(state.channel)
    TimerChannelDetailRow(
        iconRes = R.drawable.ic_last_seen,
        title = stringResource(R.string.device_timer_channel_temporary_control),
        value = if (channel.temporaryOverrideActive) {
            timerRuntimeSummary(channel, false)
        } else {
            stringResource(R.string.device_timer_temporary_inactive)
        },
        enabled = state.temporaryOverrideWriteEnabled && !state.mutationPending,
        onClick = actions.onTimedControlClick,
        colors = colors,
        typography = typography
    )
}

@Composable
private fun TimerWorkModeRow(
    state: DeviceTimerChannelDetailUiState,
    actions: DeviceTimerChannelActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val channel = checkNotNull(state.channel)
    TimerChannelDetailRow(
        iconRes = R.drawable.ic_settings,
        title = stringResource(R.string.device_timer_channel_work_mode),
        value = timerWorkModeLabel(channel.regime),
        enabled = state.channelStateWriteEnabled && !state.mutationPending,
        onClick = actions.onWorkModeClick,
        colors = colors,
        typography = typography
    )
}

@Composable
private fun TimerChannelNameRow(
    state: DeviceTimerChannelDetailUiState,
    actions: DeviceTimerChannelActions,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val channel = checkNotNull(state.channel)
    TimerChannelDetailRow(
        iconRes = R.drawable.ic_edit_24,
        title = stringResource(R.string.device_timer_channel_name),
        value = channel.effectiveName,
        enabled = state.displayNameWriteEnabled && !state.mutationPending,
        onClick = actions.onChannelNameClick,
        colors = colors,
        typography = typography
    )
}

@Composable
internal fun timerWorkModeLabel(regime: DeviceTimerChannelRegime): String =
    stringResource(
        if (regime == DeviceTimerChannelRegime.AUTO) {
            R.string.device_timer_work_mode_program
        } else {
            R.string.device_timer_work_mode_manual
        }
    )
