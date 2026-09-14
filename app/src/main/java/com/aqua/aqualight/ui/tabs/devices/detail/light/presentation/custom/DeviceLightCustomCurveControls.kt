package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSlider
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderActions
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderState

@Composable
internal fun VirtualTimePreviewCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PREVIEW_CONTENT_SPACING_DP.dp)) {
            SectionHeading(
                title = stringResource(R.string.device_light_custom_virtual_preview),
                subtitle = stringResource(R.string.device_light_custom_virtual_preview_summary),
                visuals = visuals
            )
            PreviewControls(state, actions, visuals)
        }
    }
}

@Composable
private fun PreviewControls(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PreviewTimeSlider(state, actions, visuals, Modifier.weight(1f))
        Spacer(Modifier.width(PREVIEW_BUTTON_GAP_DP.dp))
        CustomOutlinedButton(
            button = CustomOutlinedButtonState(
                label = stringResource(R.string.device_light_custom_preview),
                description = stringResource(R.string.device_light_custom_preview_description),
                enabled = state.contentEnabled && !state.operationInProgress
            ),
            appearance = CustomOutlinedButtonAppearance(
                color = visuals.colors.action,
                height = PREVIEW_BUTTON_HEIGHT_DP.dp,
                showPlayIcon = true
            ),
            onClick = actions.onPreviewClick,
            modifier = Modifier.width(PREVIEW_BUTTON_WIDTH_DP.dp)
        )
    }
}

@Composable
private fun PreviewTimeSlider(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals,
    modifier: Modifier
) {
    Column(modifier) {
        BasicText(
            text = formatTime(state.previewTimeMs),
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.primaryText,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
        AquaLightManualPercentSlider(
            state = AquaLightManualPercentSliderState(
                percent = state.previewTimeMs.toDayPercent(),
                enabled = state.contentEnabled && !state.operationInProgress,
                channelColor = visuals.colors.action,
                stateText = formatTime(state.previewTimeMs),
                accessibilityDescription = stringResource(
                    R.string.device_light_custom_virtual_time_description
                )
            ),
            actions = AquaLightManualPercentSliderActions(
                onValueChanged = { percent -> actions.onPreviewTimeChanged(percent.toDayTime()) },
                onValueChangeFinished = {}
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PREVIEW_TIME_LABELS.forEach { label ->
                BasicText(text = label, style = visuals.typography.micro)
            }
        }
    }
}

@Composable
internal fun LibraryActions(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LIBRARY_ACTION_SPACING_DP.dp)
    ) {
        CustomOutlinedButton(
            button = CustomOutlinedButtonState(
                label = stringResource(R.string.device_light_custom_load),
                description = stringResource(R.string.device_light_custom_load_description),
                enabled = state.contentEnabled && !state.operationInProgress
            ),
            appearance = CustomOutlinedButtonAppearance(
                color = visuals.colors.action,
                iconRes = R.drawable.ic_light_library
            ),
            onClick = actions.onLoadClick,
            modifier = Modifier.weight(1f)
        )
        CustomOutlinedButton(
            button = CustomOutlinedButtonState(
                label = stringResource(R.string.device_light_custom_save_as),
                description = stringResource(R.string.device_light_custom_save_as_description),
                enabled = state.canSaveAs
            ),
            appearance = CustomOutlinedButtonAppearance(
                color = visuals.colors.action,
                iconRes = R.drawable.ic_add_24
            ),
            onClick = actions.onSaveAsClick,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Long.toDayPercent(): Int =
    ((this * MAX_LIGHT_CHANNEL_PERCENT) / (MILLIS_PER_DAY - MILLIS_PER_MINUTE)).toInt()

private fun Int.toDayTime(): Long =
    (MILLIS_PER_DAY - MILLIS_PER_MINUTE) * this / MAX_LIGHT_CHANNEL_PERCENT

private val PREVIEW_TIME_LABELS = listOf("00", "06", "12", "18", "24")
private const val PREVIEW_CONTENT_SPACING_DP = 8
private const val PREVIEW_BUTTON_GAP_DP = 10
private const val PREVIEW_BUTTON_HEIGHT_DP = 36
private const val PREVIEW_BUTTON_WIDTH_DP = 84
private const val LIBRARY_ACTION_SPACING_DP = 8
