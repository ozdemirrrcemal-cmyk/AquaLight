package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardAlpha
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.common.timer.AquaTimerInteractionStyle
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerChannelUiState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.isPowerWriteEnabled
import com.aqua.aqualight.ui.tabs.devices.detail.timer.timerOperatingStateLabel

@Composable
internal fun TimerChannelHero(
    state: DeviceTimerChannelDetailUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onPowerClick: () -> Unit
) {
    val channel = checkNotNull(state.channel)
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
            TimerChannelHeroPower(state, channel, colors, onPowerClick)
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
private fun TimerChannelHeroPower(
    state: DeviceTimerChannelDetailUiState,
    channel: DeviceTimerChannelUiState,
    colors: AquaDeviceCardColors,
    onPowerClick: () -> Unit
) {
    val active = channel.operatingState == DeviceTimerOperatingState.ON
    val tint = if (active) colors.accent else colors.secondaryText
    val enabled = channel.isPowerWriteEnabled(
        persistentWriteEnabled = state.channelStateWriteEnabled,
        temporaryOverrideWriteEnabled = state.temporaryOverrideWriteEnabled
    ) && !state.mutationPending
    Box(
        modifier = Modifier
            .size(AquaTimerDashboardGeometry.detailHeroPowerContainerSize)
            .clip(CircleShape)
            .background(tint.copy(alpha = AquaTimerDashboardAlpha.powerSurface))
            .border(AquaTimerDashboardGeometry.channelPowerGlowWidth, tint, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onPowerClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_timer_power),
            contentDescription = stringResource(channel.powerDescriptionResource(active)),
            modifier = Modifier.size(AquaTimerDashboardGeometry.detailHeroPowerIconSize),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

private fun DeviceTimerChannelUiState.powerDescriptionResource(active: Boolean): Int = when {
    regime == DeviceTimerChannelRegime.AUTO && active ->
        R.string.device_timer_program_power_turn_off_description
    regime == DeviceTimerChannelRegime.AUTO ->
        R.string.device_timer_program_power_turn_on_description
    active -> R.string.device_timer_manual_power_turn_off_description
    else -> R.string.device_timer_manual_power_turn_on_description
}
