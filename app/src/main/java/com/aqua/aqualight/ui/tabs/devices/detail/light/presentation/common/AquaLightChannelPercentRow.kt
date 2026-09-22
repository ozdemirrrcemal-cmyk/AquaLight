package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.compose.foundation.layout.Row
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
        BasicText(
            text = state.label,
            style = typography.body.copy(color = colors.card.primaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(AquaLightManualGeometry.channelLabelWidth)
        )
        AquaLightChannelStepButton(
            state = AquaLightChannelStepButtonState(
                symbol = stringResource(R.string.device_light_manual_minus_symbol),
                contentDescription = stringResource(
                    R.string.device_light_manual_decrease_channel_description,
                    state.label
                ),
                enabled = state.enabled &&
                    state.percent > AquaLightManualControlSpec.minimumPercent
            ),
            colors = colors,
            typography = typography,
            onClick = {
                actions.onStep(-AquaLightManualControlSpec.stepPercent)
            }
        )
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
        AquaLightChannelStepButton(
            state = AquaLightChannelStepButtonState(
                symbol = stringResource(R.string.device_light_manual_plus_symbol),
                contentDescription = stringResource(
                    R.string.device_light_manual_increase_channel_description,
                    state.label
                ),
                enabled = state.enabled &&
                    state.percent < AquaLightManualControlSpec.maximumPercent
            ),
            colors = colors,
            typography = typography,
            onClick = {
                actions.onStep(AquaLightManualControlSpec.stepPercent)
            }
        )
        BasicText(
            text = valueText,
            style = typography.body.copy(
                color = colors.card.primaryText,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.width(AquaLightManualGeometry.channelValueWidth)
        )
    }
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
