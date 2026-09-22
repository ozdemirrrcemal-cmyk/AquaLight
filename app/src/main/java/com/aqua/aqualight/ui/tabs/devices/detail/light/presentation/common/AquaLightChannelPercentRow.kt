package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun AquaLightChannelPercentRow(
    state: AquaLightChannelPercentRowState,
    actions: AquaLightChannelPercentRowActions,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val valueText = stringResource(
        R.string.device_light_live_output_percent_format,
        state.percent
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AquaLightManualGeometry.channelRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLabel(state.label, colors, typography)
        ChannelStepButton(state, actions, colors, typography, increase = false)
        ChannelPercentSlider(state, actions, valueText)
        ChannelStepButton(state, actions, colors, typography, increase = true)
        ChannelValue(valueText, colors, typography)
    }
}

@Composable
private fun ChannelLabel(
    label: String,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = label,
        style = typography.body.copy(color = colors.card.primaryText),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(AquaLightManualGeometry.channelLabelWidth)
    )
}

@Composable
private fun ChannelStepButton(
    state: AquaLightChannelPercentRowState,
    actions: AquaLightChannelPercentRowActions,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography,
    increase: Boolean
) {
    val step = if (increase) {
        AquaLightManualControlSpec.stepPercent
    } else {
        -AquaLightManualControlSpec.stepPercent
    }
    val enabled = state.enabled && if (increase) {
        state.percent < AquaLightManualControlSpec.maximumPercent
    } else {
        state.percent > AquaLightManualControlSpec.minimumPercent
    }
    val symbolRes = if (increase) {
        R.string.device_light_manual_plus_symbol
    } else {
        R.string.device_light_manual_minus_symbol
    }
    val descriptionRes = if (increase) {
        R.string.device_light_manual_increase_channel_description
    } else {
        R.string.device_light_manual_decrease_channel_description
    }

    AquaLightChannelStepButton(
        state = AquaLightChannelStepButtonState(
            symbol = stringResource(symbolRes),
            contentDescription = stringResource(descriptionRes, state.label),
            enabled = enabled
        ),
        colors = colors,
        typography = typography,
        onClick = { actions.onStep(step) }
    )
}

@Composable
private fun RowScope.ChannelPercentSlider(
    state: AquaLightChannelPercentRowState,
    actions: AquaLightChannelPercentRowActions,
    valueText: String
) {
    AquaLightManualPercentSlider(
        state = AquaLightManualPercentSliderState(
            percent = state.percent,
            enabled = state.enabled,
            channelColor = deviceLightChannelColor(state.channelWireKey),
            stateText = valueText,
            accessibilityDescription = state.accessibilityDescription
        ),
        actions = AquaLightManualPercentSliderActions(
            onValueChanged = actions.onValueChanged,
            onValueChangeFinished = actions.onValueChangeFinished
        ),
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = AquaLightManualGeometry.channelSliderHorizontalInset)
    )
}

@Composable
private fun ChannelValue(
    valueText: String,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography
) {
    BasicText(
        text = valueText,
        style = typography.body.copy(
            color = colors.card.primaryText,
            textAlign = TextAlign.End
        ),
        modifier = Modifier.width(AquaLightManualGeometry.channelValueWidth)
    )
}

@Immutable
internal data class AquaLightChannelPercentRowState(
    val label: String,
    val percent: Int,
    val enabled: Boolean,
    val channelWireKey: String,
    val accessibilityDescription: String
)

internal data class AquaLightChannelPercentRowActions(
    val onValueChanged: (Int) -> Unit,
    val onValueChangeFinished: () -> Unit,
    val onStep: (Int) -> Unit
)
