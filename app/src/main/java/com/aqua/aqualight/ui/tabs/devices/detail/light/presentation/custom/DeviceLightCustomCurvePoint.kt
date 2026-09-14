package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSlider
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderActions
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderState

@Composable
internal fun SelectedPointCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(POINT_CONTENT_SPACING_DP.dp)) {
            BasicText(
                text = stringResource(R.string.device_light_custom_selected_point),
                style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
            )
            state.selectedPoint?.let { point ->
                SelectedPointEditor(point, state, actions, visuals)
            } ?: BasicText(
                text = stringResource(R.string.device_light_custom_no_point_selected),
                style = visuals.typography.caption,
                modifier = Modifier.padding(vertical = EMPTY_POINT_PADDING_DP.dp)
            )
        }
    }
}

@Composable
private fun SelectedPointEditor(
    point: DeviceLightCustomPointUiState,
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(POINT_ACTION_SPACING_DP.dp)
    ) {
        PointTimeButton(
            point = point,
            enabled = state.contentEnabled,
            onClick = actions.onEditTimeClick,
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
        SquareIconButton(
            iconRes = R.drawable.ic_add_24,
            description = stringResource(R.string.device_light_custom_duplicate_point),
            enabled = state.contentEnabled && !state.operationInProgress,
            color = visuals.colors.action,
            onClick = actions.onDuplicatePointClick
        )
        SquareIconButton(
            iconRes = R.drawable.ic_delete_24,
            description = stringResource(R.string.device_light_custom_delete_point),
            enabled = !state.operationInProgress,
            color = visuals.colors.card.danger,
            onClick = actions.onDeletePointClick
        )
    }
    point.channels.forEach { (channel, percent) ->
        CustomChannelRow(channel, percent, state, actions, visuals)
    }
}

@Composable
private fun PointTimeButton(
    point: DeviceLightCustomPointUiState,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightCustomVisuals,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(POINT_BUTTON_CORNER_DP.dp)
    Row(
        modifier = modifier.height(POINT_BUTTON_HEIGHT_DP.dp).clip(shape)
            .border(BORDER_WIDTH_DP.dp, visuals.colors.card.mediaOutline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = POINT_BUTTON_HORIZONTAL_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClockGlyph(visuals.colors.card.secondaryText)
        BasicText(
            text = formatTime(point.timeMs),
            style = visuals.typography.title.copy(color = visuals.colors.card.primaryText),
            modifier = Modifier.padding(start = POINT_TIME_TEXT_PADDING_DP.dp)
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
        StepButton(
            symbol = stringResource(R.string.device_light_manual_minus_symbol),
            enabled = !state.operationInProgress && percent > MIN_LIGHT_CHANNEL_PERCENT,
            onClick = { actions.onChannelStep(channel, CHANNEL_DECREMENT) },
            visuals = visuals
        )
        ChannelSlider(channel, percent, label, state, actions, visuals)
        StepButton(
            symbol = stringResource(R.string.device_light_manual_plus_symbol),
            enabled = !state.operationInProgress && percent < MAX_LIGHT_CHANNEL_PERCENT,
            onClick = { actions.onChannelStep(channel, CHANNEL_INCREMENT) },
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
private fun ChannelSlider(
    channel: DeviceLightCustomChannelId,
    percent: Int,
    label: String,
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaLightManualPercentSlider(
        state = AquaLightManualPercentSliderState(
            percent = percent,
            enabled = state.contentEnabled && !state.operationInProgress,
            channelColor = visuals.channelColor(channel),
            stateText = "$percent%",
            accessibilityDescription = label
        ),
        actions = AquaLightManualPercentSliderActions(
            onValueChanged = { value -> actions.onChannelChanged(channel, value) },
            onValueChangeFinished = {}
        ),
        modifier = Modifier.weight(1f).padding(horizontal = CHANNEL_SLIDER_PADDING_DP.dp)
    )
}

@Composable
private fun StepButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightCustomVisuals
) {
    val alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
    Box(
        modifier = Modifier.size(STEP_TOUCH_SIZE_DP.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(STEP_BORDER_SIZE_DP.dp).border(
                BORDER_WIDTH_DP.dp,
                visuals.colors.card.mediaOutline.copy(alpha = alpha),
                RoundedCornerShape(percent = CIRCLE_SHAPE_PERCENT)
            ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                symbol,
                style = visuals.typography.title.copy(
                    color = visuals.colors.card.primaryText.copy(alpha = alpha)
                )
            )
        }
    }
}

private const val POINT_CONTENT_SPACING_DP = 5
private const val EMPTY_POINT_PADDING_DP = 10
private const val POINT_ACTION_SPACING_DP = 7
private const val POINT_BUTTON_CORNER_DP = 11
private const val POINT_BUTTON_HEIGHT_DP = 44
private const val BORDER_WIDTH_DP = 1
private const val POINT_BUTTON_HORIZONTAL_PADDING_DP = 13
private const val POINT_TIME_TEXT_PADDING_DP = 10
private const val CHANNEL_ROW_HEIGHT_DP = 38
private const val CHANNEL_LABEL_WIDTH_DP = 43
private const val CHANNEL_PERCENT_WIDTH_DP = 36
private const val CHANNEL_SLIDER_PADDING_DP = 1
private const val CHANNEL_DECREMENT = -1
private const val CHANNEL_INCREMENT = 1
private const val STEP_TOUCH_SIZE_DP = 36
private const val STEP_BORDER_SIZE_DP = 29
private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.38f
