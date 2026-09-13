package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.light.AquaLightManualAlpha
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.common.light.AquaLightManualGeometry
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSlider
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderActions
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderState
import com.aqua.aqualight.ui.common.light.AquaLightManualPreviewSpec

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
    ManualChannelStepButton(
        symbol = stringResource(R.string.device_light_manual_minus_symbol),
        contentDescription = stringResource(
            R.string.device_light_manual_decrease_channel_description,
            content.label
        ),
        enabled = enabled && content.channel.percent > PERCENT_RANGE.first,
        visuals = visuals,
        onClick = {
            actions.channels.onChannelStep(
                content.channel.id,
                -AquaLightManualPreviewSpec.stepPercent
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
    ManualChannelStepButton(
        symbol = stringResource(R.string.device_light_manual_plus_symbol),
        contentDescription = stringResource(
            R.string.device_light_manual_increase_channel_description,
            content.label
        ),
        enabled = enabled && content.channel.percent < PERCENT_RANGE.last,
        visuals = visuals,
        onClick = {
            actions.channels.onChannelStep(
                content.channel.id,
                AquaLightManualPreviewSpec.stepPercent
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

@Composable
private fun ManualChannelStepButton(
    symbol: String,
    contentDescription: String,
    enabled: Boolean,
    visuals: DeviceLightManualVisuals,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else AquaLightManualAlpha.disabledControl
    Box(
        modifier = Modifier
            .size(AquaLightManualGeometry.channelStepTouchSize)
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(AquaLightManualGeometry.channelStepVisualSize)
                .clip(RoundedCornerShape(percent = PERCENT_SHAPE))
                .border(
                    AquaLightManualGeometry.channelStepOutlineWidth,
                    visuals.colors.card.mediaOutline.copy(alpha = alpha),
                    RoundedCornerShape(percent = PERCENT_SHAPE)
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = symbol,
                style = visuals.typography.title.copy(
                    color = visuals.colors.card.primaryText.copy(alpha = alpha)
                )
            )
        }
    }
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

private const val PERCENT_SHAPE = 50
