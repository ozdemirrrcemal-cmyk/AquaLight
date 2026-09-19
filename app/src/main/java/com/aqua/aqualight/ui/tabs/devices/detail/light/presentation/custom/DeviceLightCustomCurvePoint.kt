package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualPercentSlider
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualPercentSliderActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualPercentSliderState

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
    val label = stringResource(channel.labelRes)
    Row(
        modifier = Modifier.fillMaxWidth().height(CHANNEL_ROW_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = visuals.typography.body,
            modifier = Modifier.width(CHANNEL_LABEL_WIDTH_DP.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ChannelSlider(
            state = ChannelSliderState(
                channel = channel,
                percent = percent,
                label = label,
                enabled = state.contentEnabled && !state.operationInProgress
            ),
            actions = actions,
            visuals = visuals
        )
        BasicText(
            text = stringResource(R.string.device_light_library_channel_percent_format, percent),
            style = visuals.typography.body.copy(textAlign = TextAlign.End),
            modifier = Modifier.width(CHANNEL_PERCENT_WIDTH_DP.dp)
        )
    }
}

@Composable
private fun RowScope.ChannelSlider(
    state: ChannelSliderState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaLightManualPercentSlider(
        state = AquaLightManualPercentSliderState(
            percent = state.percent,
            enabled = state.enabled,
            channelColor = visuals.channelColor(state.channel),
            stateText = "${state.percent}%",
            accessibilityDescription = state.label
        ),
        actions = AquaLightManualPercentSliderActions(
            onValueChanged = { value -> actions.onChannelChanged(state.channel, value) },
            onValueChangeFinished = {}
        ),
        modifier = Modifier.weight(1f).padding(horizontal = CHANNEL_SLIDER_PADDING_DP.dp)
    )
}

private data class ChannelSliderState(
    val channel: DeviceLightCustomChannelId,
    val percent: Int,
    val label: String,
    val enabled: Boolean
)

private const val POINT_CONTENT_SPACING_DP = 3
private const val EMPTY_POINT_PADDING_DP = 10
private const val CHANNEL_ROW_HEIGHT_DP = 41
private const val CHANNEL_LABEL_WIDTH_DP = 58
private const val CHANNEL_PERCENT_WIDTH_DP = 42
private const val CHANNEL_SLIDER_PADDING_DP = 6
