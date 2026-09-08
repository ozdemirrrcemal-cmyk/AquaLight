package com.aqua.aqualight.ui.tabs.devices.detail.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry

@Composable
@Suppress("LongParameterList")
internal fun TimerChannelCardHeader(
    channel: DeviceTimerChannelUiState,
    name: String,
    stateLabel: String,
    powerEnabled: Boolean,
    onPowerClick: () -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelHeaderGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimerChannelProductIcon(colors)
        TimerChannelCardTitle(
            channel = channel,
            name = name,
            stateLabel = stateLabel,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        TimerPowerButton(
            active = channel.operatingState == DeviceTimerOperatingState.ON,
            enabled = powerEnabled,
            onClick = onPowerClick,
            colors = colors
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun TimerChannelCardTitle(
    channel: DeviceTimerChannelUiState,
    name: String,
    stateLabel: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelTextGap)
    ) {
        BasicText(
            text = name,
            style = typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelHeaderGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimerStatePill(
                label = stateLabel,
                active = channel.operatingState == DeviceTimerOperatingState.ON,
                colors = colors,
                typography = typography
            )
            BasicText(
                text = timerScheduleSummary(channel),
                style = typography.caption.copy(color = colors.secondaryText),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
