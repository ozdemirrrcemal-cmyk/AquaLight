package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
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
            CurveHeader(state, visuals)
            EditableCurveChart(state, actions, visuals)
            CurveLegend(state.channels, visuals)
        }
    }
}

@Composable
private fun CurveHeader(
    state: DeviceLightCustomCurveUiState,
    visuals: DeviceLightCustomVisuals
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CurveHeadingGlyph(visuals.colors.action)
        Column(
            modifier = Modifier.weight(1f).padding(start = HEADER_ICON_GAP_DP.dp)
        ) {
            BasicText(
                text = stringResource(R.string.device_light_custom_curve_heading),
                style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
            )
            BasicText(
                text = stringResource(R.string.device_light_custom_curve_helper),
                style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText)
            )
        }
        BasicText(
            text = stringResource(
                R.string.device_light_custom_point_capacity_format,
                state.draft.points.size,
                state.maxPoints
            ),
            style = visuals.typography.caption.copy(color = visuals.colors.card.primaryText),
            modifier = Modifier.padding(start = HEADER_CAPACITY_GAP_DP.dp)
        )
    }
}

@Composable
private fun CurveHeadingGlyph(color: Color) {
    Canvas(Modifier.size(HEADER_ICON_SIZE_DP.dp)) {
        val stroke = HEADER_ICON_STROKE_DP.dp.toPx()
        drawLine(
            color = color,
            start = Offset(stroke, stroke),
            end = Offset(stroke, size.height - stroke),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(stroke, size.height - stroke),
            end = Offset(size.width - stroke, size.height - stroke),
            strokeWidth = stroke
        )
        val path = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.68f)
            lineTo(size.width * 0.40f, size.height * 0.43f)
            lineTo(size.width * 0.62f, size.height * 0.58f)
            lineTo(size.width * 0.88f, size.height * 0.23f)
        }
        drawPath(path, color, style = Stroke(width = stroke))
    }
}

@Composable
private fun EditableCurveChart(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    val description = pluralStringResource(
        R.plurals.device_light_library_chart_description,
        state.draft.points.size,
        state.draft.points.size
    )
    Column {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
                .height((PLAYHEAD_LABEL_SPACE_DP + CHART_HEIGHT_DP).dp)
        ) {
            val axisWidth = CHART_PERCENT_AXIS_WIDTH_DP.dp
            val bubbleWidth = PLAYHEAD_LABEL_WIDTH_DP.dp
            val plotWidth = (maxWidth - axisWidth).coerceAtLeast(0.dp)
            val playheadFraction = state.previewTimeMs.toFloat() / MILLIS_PER_DAY.toFloat()
            val maximumBubbleX = (maxWidth - bubbleWidth).coerceAtLeast(0.dp)
            val bubbleX = (axisWidth + plotWidth * playheadFraction - bubbleWidth / 2f)
                .coerceIn(0.dp, maximumBubbleX)
            val handleWidth = PLAYHEAD_HANDLE_WIDTH_DP.dp
            val maximumHandleX = (maxWidth - handleWidth).coerceAtLeast(0.dp)
            val handleX = (axisWidth + plotWidth * playheadFraction - handleWidth / 2f)
                .coerceIn(0.dp, maximumHandleX)
            val plotWidthPx = with(LocalDensity.current) { plotWidth.toPx() }

            Row(
                modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)
                    .align(Alignment.BottomStart)
            ) {
                PercentAxis(visuals)
                CurveCanvas(
                    state = state,
                    actions = actions,
                    description = description,
                    visuals = visuals,
                    modifier = Modifier.weight(1f)
                )
            }
            PlayheadTimeBubble(
                timeMs = state.previewTimeMs,
                enabled = state.contentEnabled && !state.operationInProgress,
                onClick = actions.onPlayheadTimeClick,
                visuals = visuals,
                modifier = Modifier.offset(x = bubbleX).width(bubbleWidth)
            )
            PlayheadDragHandle(
                timeMs = state.previewTimeMs,
                enabled = state.contentEnabled && !state.operationInProgress,
                chartWidthPx = plotWidthPx,
                onTimeChanged = actions.onPlayheadChanged,
                onDragFinished = actions.onPlayheadChangeFinished,
                modifier = Modifier.offset(
                    x = handleX,
                    y = (PLAYHEAD_LABEL_SPACE_DP + CHART_HEIGHT_DP -
                        PLAYHEAD_HANDLE_HEIGHT_DP).dp
                ).width(handleWidth).height(PLAYHEAD_HANDLE_HEIGHT_DP.dp)
            )
        }
        HourAxis(visuals)
    }
}

@Composable
private fun PlayheadDragHandle(
    timeMs: Long,
    enabled: Boolean,
    chartWidthPx: Float,
    onTimeChanged: (Long) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier
) {
    val currentTimeMs by rememberUpdatedState(timeMs)
    Box(
        modifier = modifier.pointerInput(enabled, chartWidthPx) {
            if (!enabled || chartWidthPx <= 0f) return@pointerInput
            var playheadX = 0f
            var moved = false
            detectHorizontalDragGestures(
                onDragStart = {
                    playheadX = chartX(currentTimeMs, chartWidthPx, 0L, MILLIS_PER_DAY)
                    moved = false
                },
                onDragEnd = {
                    if (moved) onDragFinished()
                    moved = false
                },
                onDragCancel = { moved = false }
            ) { change, dragAmount ->
                change.consume()
                moved = true
                playheadX = (playheadX + dragAmount).coerceIn(0f, chartWidthPx)
                onTimeChanged(playheadTimeForX(playheadX, chartWidthPx))
            }
        }.clearAndSetSemantics { contentDescription = formatTime(timeMs) }
    )
}

@Composable
private fun PlayheadTimeBubble(
    timeMs: Long,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightCustomVisuals,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(PLAYHEAD_LABEL_CORNER_DP.dp)
    Box(
        modifier = modifier.height(PLAYHEAD_LABEL_TOUCH_HEIGHT_DP.dp)
            .clearAndSetSemantics { contentDescription = formatTime(timeMs) }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(PLAYHEAD_LABEL_VISUAL_HEIGHT_DP.dp)
                .clip(shape)
                .border(PLAYHEAD_LABEL_BORDER_DP.dp, visuals.colors.action, shape),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = formatTime(timeMs),
                style = visuals.typography.caption.copy(
                    color = visuals.colors.card.primaryText,
                    textAlign = TextAlign.Center
                )
            )
        }
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
    actions: DeviceLightCustomCurveActions,
    description: String,
    visuals: DeviceLightCustomVisuals,
    modifier: Modifier
) {
    Canvas(
        modifier = modifier.height(CHART_HEIGHT_DP.dp)
            .pointerInput(
                state.contentEnabled,
                state.operationInProgress,
                state.draft.points
            ) {
                if (state.contentEnabled && !state.operationInProgress) {
                    detectTapGestures(
                        onTap = { offset ->
                            nearestVisiblePointTime(
                                points = state.draft.points,
                                tapX = offset.x,
                                chartWidth = size.width.toFloat(),
                                windowStartMs = 0L,
                                windowEndMs = MILLIS_PER_DAY,
                                tolerancePx = POINT_HIT_RADIUS_DP.dp.toPx()
                            )?.let(actions.onGraphPointClick)
                        },
                        onLongPress = { offset ->
                            nearestVisiblePointTime(
                                points = state.draft.points,
                                tapX = offset.x,
                                chartWidth = size.width.toFloat(),
                                windowStartMs = 0L,
                                windowEndMs = MILLIS_PER_DAY,
                                tolerancePx = POINT_HIT_RADIUS_DP.dp.toPx()
                            )?.let(actions.onGraphPointLongClick)
                        }
                    )
                }
            }
            .clearAndSetSemantics { contentDescription = description }
    ) {
        drawCurveGrid(visuals.colors.card, CHART_TIME_DIVISIONS)
        drawPlayheadGuide(
            timeMs = state.previewTimeMs,
            windowStartMs = 0L,
            windowEndMs = MILLIS_PER_DAY,
            visuals = visuals
        )
        val samples = state.draft.points.chartSamples(
            state.channels,
            0L,
            MILLIS_PER_DAY
        )
        state.channels.forEach { channel ->
            drawChannelCurve(
                samples = samples,
                actualPoints = state.draft.points,
                channel = channel,
                selectedTimeMs = state.selectedTimeMs,
                windowStartMs = 0L,
                windowEndMs = MILLIS_PER_DAY,
                visuals = visuals
            )
        }
        drawPlayheadThumb(
            timeMs = state.previewTimeMs,
            windowStartMs = 0L,
            windowEndMs = MILLIS_PER_DAY,
            visuals = visuals
        )
    }
}

@Composable
private fun HourAxis(visuals: DeviceLightCustomVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = CHART_PERCENT_AXIS_WIDTH_DP.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CHART_HOUR_LABELS.forEach { hour ->
            BasicText(
                text = hour.toString().padStart(TIME_DIGITS, '0'),
                style = visuals.typography.micro
            )
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
private val CHART_HOUR_LABELS = listOf(0, 4, 8, 12, 16, 20, 24)
private const val CURVE_CONTENT_SPACING_DP = 8
private const val HEADER_ICON_SIZE_DP = 34
private const val HEADER_ICON_STROKE_DP = 2
private const val HEADER_ICON_GAP_DP = 10
private const val HEADER_CAPACITY_GAP_DP = 8
private const val PLAYHEAD_LABEL_SPACE_DP = 48
private const val PLAYHEAD_LABEL_WIDTH_DP = 58
private const val PLAYHEAD_LABEL_TOUCH_HEIGHT_DP = 48
private const val PLAYHEAD_LABEL_VISUAL_HEIGHT_DP = 30
private const val PLAYHEAD_LABEL_CORNER_DP = 8
private const val PLAYHEAD_LABEL_BORDER_DP = 1
private const val CHART_PERCENT_AXIS_WIDTH_DP = 38
private const val CHART_HEIGHT_DP = 218
private const val CHART_TIME_DIVISIONS = 6
private const val POINT_HIT_RADIUS_DP = 24
private const val PLAYHEAD_HANDLE_WIDTH_DP = 56
private const val PLAYHEAD_HANDLE_HEIGHT_DP = 48
private const val TIME_DIGITS = 2
private const val LEGEND_ITEM_PADDING_DP = 11
private const val LEGEND_DOT_SIZE_DP = 10
private const val LEGEND_TEXT_PADDING_DP = 5
private const val THREE_QUARTER_PERCENT = 75
private const val HALF_PERCENT = 50
private const val QUARTER_PERCENT = 25
