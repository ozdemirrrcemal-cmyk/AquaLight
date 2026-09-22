package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelPercentRow
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelPercentRowActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelPercentRowState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.deviceLightChannelNameResource

@Composable
internal fun ManualChannelRow(
    channel: DeviceLightManualChannelUiState,
    enabled: Boolean,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals
) {
    val label = stringResource(deviceLightChannelNameResource(channel.id.wireKey))
    AquaLightChannelPercentRow(
        state = AquaLightChannelPercentRowState(
            label = label,
            percent = channel.percent,
            enabled = enabled,
            channelWireKey = channel.id.wireKey,
            accessibilityDescription = stringResource(
                R.string.device_light_manual_slider_description,
                label
            )
        ),
        actions = AquaLightChannelPercentRowActions(
            onValueChanged = { percent ->
                actions.channels.onChannelValueChanged(channel.id, percent)
            },
            onValueChangeFinished = {
                actions.channels.onChannelValueChangeFinished(channel.id)
            },
            onStep = { delta ->
                actions.channels.onChannelStep(channel.id, delta)
            }
        ),
        colors = visuals.colors,
        typography = visuals.typography
    )
}
