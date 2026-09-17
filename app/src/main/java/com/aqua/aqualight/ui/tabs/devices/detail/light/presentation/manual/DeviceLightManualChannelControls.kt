package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelStepButton
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightChannelStepButtonState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualPercentSlider
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualPercentSliderActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualPercentSliderState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualControlSpec

@Composable
internal fun ManualChannelRow(
    channel: DeviceLightManualChannelUiState,
    enabled: Boolean,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals
) {
    val content = ManualChannelContent(
        channel = channel,
        label = stringResource(channel.labelRes),
        value = stringResource(R.string.device_light_live_output_percent_format, channel.percent)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightManualGeometry.channelRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ManualChannelLabel(content, visuals)
        ManualChannelDecreaseButton(content, enabled, actions, visuals)
        ManualChannelSlider(
            content = content,
            enabled = enabled,
            actions = actions,
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
        ManualChannelIncreaseButton(content, enabled, actions, visuals)
        ManualChannelValue(content, visuals)
    }
}

@Composable
private fun ManualChannelLabel(
    content: ManualChannelContent,
    visuals: DeviceLightManualVisuals
) {
    BasicText(
        text = content.label,
        style = visuals.typography.body.copy(color = visuals.colors.card.primaryText),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(AquaLightManualGeometry.channelLabelWidth)
    )
}

@Composable
private fun ManualChannelDecreaseButton(
    content: ManualChannelContent,
    enabled: Boolean,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals
) {
    AquaLightChannelStepButton(
        state = AquaLightChannelStepButtonState(
            symbol = stringResource(R.string.device_light_manual_minus_symbol),
            contentDescription = stringResource(
                R.string.device_light_manual_decrease_channel_description,
                content.label
            ),
            enabled = enabled && content.channel.percent > PERCENT_RANGE.first
        ),
        colors = visuals.colors,
        typography = visuals.typography,
        onClick = {
            actions.channels.onChannelStep(
                content.channel.id,
                -AquaLightManualControlSpec.stepPercent
            )
        }
    )
}

@Composable
private fun ManualChannelSlider(
    content: ManualChannelContent,
    enabled: Boolean,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals,
    modifier: Modifier = Modifier
) {
    AquaLightManualPercentSlider(
        state = AquaLightManualPercentSliderState(
            percent = content.channel.percent,
            enabled = enabled,
            channelColor = visuals.colors.channelColor(content.channel.id),
            stateText = content.value,
            accessibilityDescription = stringResource(
                R.string.device_light_manual_slider_description,
                content.label
            )
        ),
        actions = AquaLightManualPercentSliderActions(
            onValueChanged = { percent ->
                actions.channels.onChannelValueChanged(content.channel.id, percent)
            },
            onValueChangeFinished = {
                actions.channels.onChannelValueChangeFinished(content.channel.id)
            }
        ),
        modifier = modifier
            .padding(horizontal = AquaLightManualGeometry.channelSliderHorizontalInset)
    )
}

@Composable
private fun ManualChannelIncreaseButton(
    content: ManualChannelContent,
    enabled: Boolean,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals
) {
    AquaLightChannelStepButton(
        state = AquaLightChannelStepButtonState(
            symbol = stringResource(R.string.device_light_manual_plus_symbol),
            contentDescription = stringResource(
                R.string.device_light_manual_increase_channel_description,
                content.label
            ),
            enabled = enabled && content.channel.percent < PERCENT_RANGE.last
        ),
        colors = visuals.colors,
        typography = visuals.typography,
        onClick = {
            actions.channels.onChannelStep(
                content.channel.id,
                AquaLightManualControlSpec.stepPercent
            )
        }
    )
}

@Composable
private fun ManualChannelValue(
    content: ManualChannelContent,
    visuals: DeviceLightManualVisuals
) {
    BasicText(
        text = content.value,
        style = visuals.typography.body.copy(
            color = visuals.colors.card.primaryText,
            textAlign = TextAlign.End
        ),
        modifier = Modifier.width(AquaLightManualGeometry.channelValueWidth)
    )
}

private fun AquaLightManualColors.channelColor(channel: DeviceLightManualChannelId): Color =
    when (channel) {
        DeviceLightManualChannelId.RED -> red
        DeviceLightManualChannelId.GREEN -> green
        DeviceLightManualChannelId.BLUE -> blue
        DeviceLightManualChannelId.WHITE -> white
    }

private data class ManualChannelContent(
    val channel: DeviceLightManualChannelUiState,
    val label: String,
    val value: String
)
