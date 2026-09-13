package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOperatingState
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root.DeviceTimerChannelUiState

@Composable
internal fun TimerChannelCardHeader(
    channel: DeviceTimerChannelUiState,
    presentation: TimerChannelHeaderPresentation,
    power: TimerPowerButtonState,
    onPowerClick: () -> Unit,
    style: TimerCardStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelHeaderGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimerChannelProductIcon(style.colors)
        TimerChannelCardTitle(
            channel = channel,
            presentation = presentation,
            style = style,
            modifier = Modifier.weight(1f)
        )
        TimerPowerButton(
            state = power,
            onClick = onPowerClick,
            colors = style.colors
        )
    }
}

@Composable
private fun TimerChannelCardTitle(
    channel: DeviceTimerChannelUiState,
    presentation: TimerChannelHeaderPresentation,
    style: TimerCardStyle,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelTextGap)
    ) {
        BasicText(
            text = presentation.name,
            style = style.typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AquaTimerDashboardGeometry.channelHeaderGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimerStatePill(
                label = presentation.stateLabel,
                active = channel.operatingState == DeviceTimerOperatingState.ON,
                colors = style.colors,
                typography = style.typography,
                activeColor = style.colors.accent
            )
            BasicText(
                text = timerScheduleSummary(channel),
                style = style.typography.caption.copy(color = style.colors.secondaryText),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal data class TimerChannelHeaderPresentation(
    val name: String,
    val stateLabel: String
)
