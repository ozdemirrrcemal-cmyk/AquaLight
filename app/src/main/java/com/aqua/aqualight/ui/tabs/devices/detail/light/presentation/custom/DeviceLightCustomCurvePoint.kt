package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelPercentRow
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelPercentRowActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelPercentRowState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.deviceLightChannelNameResource

@Composable
internal fun SelectedPointCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        state.valuesPoint?.let { point ->
            Column(verticalArrangement = Arrangement.spacedBy(POINT_CONTENT_SPACING_DP.dp)) {
                BasicText(
                    text = stringResource(
                        R.string.device_light_custom_point_values_title,
                        formatTime(point.timeMs)
                    ),
                    style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
                )
                state.channels.forEach { channel ->
                    point.channels[channel]?.let { percent ->
                        CustomChannelRow(channel, percent, state, actions, visuals)
                    }
                }
            }
        } ?: BasicText(
            text = stringResource(R.string.device_light_custom_no_point_selected),
            style = visuals.typography.caption,
            modifier = Modifier.padding(vertical = EMPTY_POINT_PADDING_DP.dp)
        )
    }
}

@Composable
private fun CustomChannelRow(
    channel: DeviceLightCustomChannelId,
    percent: Int,
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    val label = stringResource(deviceLightChannelNameResource(channel.wireKey))
    AquaLightChannelPercentRow(
        state = AquaLightChannelPercentRowState(
            label = label,
            percent = percent,
            enabled = state.contentEnabled && !state.operationInProgress,
            channelWireKey = channel.wireKey,
            accessibilityDescription = stringResource(
                R.string.device_light_manual_slider_description,
                label
            )
        ),
        actions = AquaLightChannelPercentRowActions(
            onValueChanged = { value -> actions.onChannelChanged(channel, value) },
            onValueChangeFinished = {},
            onStep = { delta ->
                actions.onChannelChanged(
                    channel,
                    (percent + delta).coerceIn(
                        MIN_LIGHT_CHANNEL_PERCENT,
                        MAX_LIGHT_CHANNEL_PERCENT
                    )
                )
            }
        ),
        colors = visuals.colors,
        typography = visuals.typography
    )
}

private const val POINT_CONTENT_SPACING_DP = 3
private const val EMPTY_POINT_PADDING_DP = 10
