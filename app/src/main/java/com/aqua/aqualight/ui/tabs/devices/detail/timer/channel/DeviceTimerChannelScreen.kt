package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
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
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerStatusNotice
import com.aqua.aqualight.ui.tabs.devices.detail.timer.TimerDivider
import com.aqua.aqualight.ui.tabs.devices.detail.timer.effectiveName
import com.aqua.aqualight.ui.tabs.devices.detail.timer.timerOperatingStateLabel
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
private fun TimerChannelHero(
    state: DeviceTimerChannelDetailUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onPowerClick: () -> Unit
) {
    val channel = checkNotNull(state.channel)
    val active = channel.operatingState == DeviceTimerOperatingState.ON
    val tint = if (active) colors.accent else colors.secondaryText
    AquaDeviceCardSurface(
        modifier = Modifier.alpha(
            if (state.mutationPending) AquaTimerInteractionStyle.disabledContentAlpha else 1f
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.detailHeroGap)
        ) {
            Box(
                modifier = Modifier
                    .size(AquaTimerDashboardGeometry.detailHeroPowerContainerSize)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = AquaTimerDashboardAlpha.powerSurface))
                    .border(
                        AquaTimerDashboardGeometry.channelPowerGlowWidth,
                        tint,
                        CircleShape
                    )
                    .clickable(
                        enabled = state.channelStateWriteEnabled && !state.mutationPending,
                        role = Role.Button,
                        onClick = onPowerClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_timer_power),
                    contentDescription = stringResource(
                        if (active) R.string.device_timer_manual_power_turn_off_description
                        else R.string.device_timer_manual_power_turn_on_description
                    ),
                    modifier = Modifier.size(AquaTimerDashboardGeometry.detailHeroPowerIconSize),
                    colorFilter = ColorFilter.tint(tint)
                )
            }
            BasicText(
                text = timerOperatingStateLabel(channel.operatingState),
                style = typography.title.copy(textAlign = TextAlign.Center)
            )
            BasicText(
                text = timerWorkModeLabel(channel.regime),
                style = typography.caption.copy(
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun timerWorkModeLabel(regime: DeviceTimerChannelRegime): String =
    stringResource(
        if (regime == DeviceTimerChannelRegime.AUTO) {
            R.string.device_timer_work_mode_program
        } else {
            R.string.device_timer_work_mode_manual
        }
    )

@Composable
@Suppress("LongParameterList")
private fun TimerChannelDetailRow(
    @DrawableRes iconRes: Int,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AquaTimerDashboardGeometry.actionShape)
            .background(
                colors.mediaSurface.copy(alpha = AquaTimerDashboardAlpha.idleBackground)
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else AquaTimerInteractionStyle.disabledContentAlpha)
            .padding(AquaTimerDashboardGeometry.detailRowPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.detailRowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.detailRowIconSize),
            colorFilter = ColorFilter.tint(colors.primaryText)
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = value,
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(AquaTimerDashboardGeometry.metadataIconSize),
            colorFilter = ColorFilter.tint(colors.secondaryText)
        )
    }
}
