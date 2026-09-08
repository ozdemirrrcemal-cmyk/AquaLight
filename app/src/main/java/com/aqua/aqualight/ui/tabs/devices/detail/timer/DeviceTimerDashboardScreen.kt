package com.aqua.aqualight.ui.tabs.devices.detail.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.ui.common.timer.AquaTimerDashboardGeometry

@Composable
@Suppress("LongMethod")
internal fun DeviceTimerDashboardScreen(
    state: DeviceTimerRootUiState,
    onRegimeSelected: (String, DeviceTimerChannelRegime, Long?) -> Unit,
    onProgramClick: (String) -> Unit,
    onTemporaryOverrideClick: (String, DeviceTimerChannelRegime) -> Unit,
    modifier: Modifier = Modifier
) {
    val control = state.control
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
        state.controlFailure?.let { failure ->
            item(key = "control-failure") {
                val copy = failure.toCommercialTimerError()
                DeviceTimerStateMessageCard(
                    title = stringResource(copy.titleRes),
                    message = stringResource(copy.messageRes)
                )
            }
        }
        control?.statusNotices?.forEach { notice ->
            item(key = "status-${notice.name}") {
                val copy = notice.toCommercialTimerStatus()
                DeviceTimerStateMessageCard(
                    title = stringResource(copy.titleRes),
                    message = stringResource(copy.messageRes)
                )
            }
        }
        control?.let { timerControl ->
            item(key = "summary") {
                DeviceTimerSummaryCard(control = timerControl)
            }
            if (timerControl.channels.isEmpty()) {
                item(key = "empty") {
                    DeviceTimerStateMessageCard(
                        title = stringResource(R.string.device_timer_channels_empty_title),
                        message = stringResource(R.string.device_timer_channels_empty_message)
                    )
                }
            }
            items(
                items = timerControl.channels,
                key = DeviceTimerChannelUiState::slotId
            ) { channel ->
                DeviceTimerChannelCard(
                    channel = channel,
                    interactionEnabled = timerControl.interactionEnabled(state.contentEnabled),
                    programEnabled = state.contentEnabled &&
                        timerControl.scheduleReadEnabled &&
                        !timerControl.lockLoop,
                    temporaryOverrideEnabled = state.contentEnabled &&
                        timerControl.temporaryOverrideWriteEnabled &&
                        !timerControl.lockLoop,
                    mutationPending = channel.slotId in state.pendingChannelSlotIds,
                    onRegimeSelected = { regime ->
                        onRegimeSelected(channel.slotId, regime, null)
                    },
                    onProgramClick = { onProgramClick(channel.slotId) },
                    onTemporaryOverrideClick = { regime ->
                        onTemporaryOverrideClick(channel.slotId, regime)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun DeviceTimerControlUiState.interactionEnabled(contentEnabled: Boolean): Boolean =
    contentEnabled && channelStateWriteEnabled && !lockLoop
