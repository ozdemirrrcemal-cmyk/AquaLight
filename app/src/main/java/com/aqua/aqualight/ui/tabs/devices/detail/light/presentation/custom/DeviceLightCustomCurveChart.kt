package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun CurveCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CURVE_CONTENT_SPACING_DP.dp)) {
            CurveHeader(state, actions, visuals)
            EditableCurveChart(state, actions.onGraphTimeClick, visuals)
            CurveLegend(state.channels, visuals)
        }
    }
}

@Composable
private fun CurveHeader(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) {
            SectionHeading(
                title = stringResource(R.string.device_light_custom_curve_heading),
                subtitle = stringResource(R.string.device_light_custom_curve_helper),
                visuals = visuals
            )
        }
        CompactActionButton(
            label = stringResource(R.string.device_light_custom_add_point),
            enabled = state.contentEnabled && !state.operationInProgress,
            onClick = actions.onAddPointClick,
            visuals = visuals
        )
    }
}

@Composable
private fun EditableCurveChart(
    state: DeviceLightCustomCurveUiState,
    onTimeClick: (Long) -> Unit,
    visuals: DeviceLightCustomVisuals
) {
    val description = pluralStringResource(
        R.plurals.device_light_library_chart_description,
        state.draft.points.size,
        state.draft.points.size
    )
    Column {
        SelectedTimeLabel(state.selectedTimeMs, visuals)
        Row(Modifier.fillMaxWidth()) {
            PercentAxis(visuals)
            CurveCanvas(
                state = state,
                onTimeClick = onTimeClick,
                description = description,
                visuals = visuals,
                modifier = Modifier.weight(1f)
            )
        }
        HourAxis(visuals)
    }
}

@Composable
private fun SelectedTimeLabel(timeMs: Long?, visuals: DeviceLightCustomVisuals) {
    timeMs?.let { selected ->
        BasicText(
            text = formatTime(selected),
            style = visuals.typography.micro.copy(color = visuals.colors.card.primaryText),
            modifier = Modifier.fillMaxWidth().padding(start = CHART_SELECTED_LABEL_PADDING_DP.dp)
        )
    }
}

@Composable
private fun PercentAxis(visuals: DeviceLightCustomVisuals) {
    Column(
        modifier = Modifier.width(CHART_PERCENT_AXIS_WIDTH_DP.dp).height(CHART_HEIGHT_DP.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        CHART_PERCENT_LABELS.forEach { value ->
            BasicText(
                text = stringResource(R.string.device_light_library_channel_percent_format, value),
                style = visuals.typography.micro
            )
        }
    }
}

@Composable
private fun CurveCanvas(
    state: DeviceLightCustomCurveUiState,
    onTimeClick: (Long) -> Unit,
    description: String,
    visuals: DeviceLightCustomVisuals,
    modifier: Modifier
) {
    Canvas(
        modifier = modifier.height(CHART_HEIGHT_DP.dp)
            .pointerInput(state.contentEnabled, state.operationInProgress, state.draft.points) {
                if (state.contentEnabled && !state.operationInProgress) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onTimeClick(((MILLIS_PER_DAY - MILLIS_PER_MINUTE) * fraction).toLong())
                    }
                }
            }
            .clearAndSetSemantics { contentDescription = description }
    ) {
        drawCurveGrid(visuals.colors.card)
        state.selectedTimeMs?.let { time -> drawSelectedGuide(time, visuals) }
        state.channels.forEach { channel ->
            drawChannelCurve(state.draft.points, channel, visuals)
        }
    }
}

@Composable
private fun HourAxis(visuals: DeviceLightCustomVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = CHART_PERCENT_AXIS_WIDTH_DP.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (0..HOURS_PER_DAY step CHART_HOUR_STEP).forEach { hour ->
            BasicText(text = hour.toString().padStart(TIME_DIGITS, '0'), style = visuals.typography.micro)
        }
    }
}

@Composable
private fun CurveLegend(
    channels: List<DeviceLightCustomChannelId>,
    visuals: DeviceLightCustomVisuals
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        channels.forEach { channel ->
            Row(
                modifier = Modifier.padding(horizontal = LEGEND_ITEM_PADDING_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(LEGEND_DOT_SIZE_DP.dp).background(
                        visuals.channelColor(channel),
                        RoundedCornerShape(percent = CIRCLE_SHAPE_PERCENT)
                    )
                )
                BasicText(
                    text = channel.name.first().toString(),
                    style = visuals.typography.micro,
                    modifier = Modifier.padding(start = LEGEND_TEXT_PADDING_DP.dp)
                )
            }
        }
    }
}

private val CHART_PERCENT_LABELS = listOf(
    MAX_LIGHT_CHANNEL_PERCENT,
    THREE_QUARTER_PERCENT,
    HALF_PERCENT,
    QUARTER_PERCENT,
    MIN_LIGHT_CHANNEL_PERCENT
)
private const val HOURS_PER_DAY = 24
private const val CHART_HOUR_STEP = 2
private const val CURVE_CONTENT_SPACING_DP = 7
private const val CHART_SELECTED_LABEL_PADDING_DP = 46
private const val CHART_PERCENT_AXIS_WIDTH_DP = 34
private const val CHART_HEIGHT_DP = 134
private const val TIME_DIGITS = 2
private const val LEGEND_ITEM_PADDING_DP = 9
private const val LEGEND_DOT_SIZE_DP = 9
private const val LEGEND_TEXT_PADDING_DP = 5
private const val THREE_QUARTER_PERCENT = 75
private const val HALF_PERCENT = 50
private const val QUARTER_PERCENT = 25
