package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.common.toCommercialTimerError
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.common.toCommercialTimerStatus
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root.DeviceTimerChannelUiState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root.DeviceTimerControlUiState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root.DeviceTimerRootUiState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root.isPowerWriteEnabled

@Composable
internal fun DeviceTimerDashboardScreen(
    state: DeviceTimerRootUiState,
    onChannelClick: (String) -> Unit,
    onPowerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
        timerDashboardItems(state, onChannelClick, onPowerClick)
    }
}

private fun LazyListScope.timerDashboardItems(
    state: DeviceTimerRootUiState,
    onChannelClick: (String) -> Unit,
    onPowerClick: (String) -> Unit
) {
    timerFailureItem(state)
    timerStatusItems(state)
    state.control?.let { control -> timerControlItems(state, control, onChannelClick, onPowerClick) }
}

private fun LazyListScope.timerFailureItem(state: DeviceTimerRootUiState) {
    state.controlFailure?.let { failure ->
        item(key = "control-failure") {
            val copy = failure.toCommercialTimerError()
            DeviceTimerStateMessageCard(
                title = stringResource(copy.titleRes),
                message = stringResource(copy.messageRes)
            )
        }
    }
}

private fun LazyListScope.timerStatusItems(state: DeviceTimerRootUiState) {
    state.control?.statusNotices?.forEach { notice ->
        item(key = "status-${notice.name}") {
            val copy = notice.toCommercialTimerStatus()
            DeviceTimerStateMessageCard(
                title = stringResource(copy.titleRes),
                message = stringResource(copy.messageRes)
            )
        }
    }
}

private fun LazyListScope.timerControlItems(
    state: DeviceTimerRootUiState,
    control: DeviceTimerControlUiState,
    onChannelClick: (String) -> Unit,
    onPowerClick: (String) -> Unit
) {
    item(key = "summary") { DeviceTimerSummaryCard(control = control) }
    if (control.channels.isEmpty()) {
        item(key = "empty") {
            DeviceTimerStateMessageCard(
                title = stringResource(R.string.device_timer_channels_empty_title),
                message = stringResource(R.string.device_timer_channels_empty_message)
            )
        }
    }
    items(items = control.channels, key = DeviceTimerChannelUiState::slotId) { channel ->
        DeviceTimerChannelCard(
            channel = channel,
            actions = channel.cardActions(state, control, onChannelClick, onPowerClick),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun DeviceTimerChannelUiState.cardActions(
    state: DeviceTimerRootUiState,
    control: DeviceTimerControlUiState,
    onChannelClick: (String) -> Unit,
    onPowerClick: (String) -> Unit
) = TimerChannelCardActions(
    interactionEnabled = state.contentEnabled && !control.lockLoop,
    powerEnabled = state.contentEnabled && !control.lockLoop && isPowerWriteEnabled(
        persistentWriteEnabled = control.channelStateWriteEnabled,
        temporaryOverrideWriteEnabled = control.temporaryOverrideWriteEnabled
    ),
    mutationPending = slotId in state.pendingChannelSlotIds,
    onChannelClick = { onChannelClick(slotId) },
    onPowerClick = { onPowerClick(slotId) }
)
