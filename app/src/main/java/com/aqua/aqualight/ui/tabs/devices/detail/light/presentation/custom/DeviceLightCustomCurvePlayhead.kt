package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class DeviceLightCustomCurvePlayhead(
    val availableWidth: Dp,
    val plotWidth: Dp,
    val axisWidth: Dp
)

@Composable
internal fun CurvePlayheadControls(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals,
    layout: DeviceLightCustomCurvePlayhead
) {
    val bubbleWidth = PLAYHEAD_LABEL_WIDTH_DP.dp
    val playheadFraction = state.previewTimeMs.toFloat() / MILLIS_PER_DAY.toFloat()
    val maximumBubbleX = (layout.availableWidth - bubbleWidth).coerceAtLeast(0.dp)
    val bubbleX = (layout.axisWidth + layout.plotWidth * playheadFraction - bubbleWidth / 2f)
        .coerceIn(0.dp, maximumBubbleX)
    val plotWidthPx = with(LocalDensity.current) { layout.plotWidth.toPx() }

    PlayheadTimeBubble(
        state = state,
        actions = actions,
        visuals = visuals,
        chartWidthPx = plotWidthPx,
        modifier = Modifier.offset(x = bubbleX).width(bubbleWidth)
    )
}

@Composable
private fun PlayheadTimeBubble(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals,
    chartWidthPx: Float,
    modifier: Modifier
) {
    val currentTimeMs by rememberUpdatedState(state.previewTimeMs)
    val enabled = state.contentEnabled && !state.operationInProgress
    val shape = RoundedCornerShape(PLAYHEAD_LABEL_CORNER_DP.dp)
    Box(
        modifier = modifier.height(PLAYHEAD_LABEL_TOUCH_HEIGHT_DP.dp)
            .pointerInput(enabled, chartWidthPx) {
                if (!enabled || chartWidthPx <= 0f) return@pointerInput
                var playheadX = 0f
                var moved = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        playheadX = chartX(
                            currentTimeMs,
                            chartWidthPx,
                            CHART_WINDOW.startMs,
                            CHART_WINDOW.endMs
                        )
                        moved = false
                    },
                    onDragEnd = {
                        if (moved) actions.onPlayheadChangeFinished()
                        moved = false
                    },
                    onDragCancel = { moved = false }
                ) { change, dragAmount ->
                    change.consume()
                    moved = true
                    playheadX = (playheadX + dragAmount).coerceIn(0f, chartWidthPx)
                    actions.onPlayheadChanged(playheadTimeForX(playheadX, chartWidthPx))
                }
            }.clearAndSetSemantics { contentDescription = formatTime(state.previewTimeMs) }
            .clickable(enabled = enabled, role = Role.Button, onClick = actions.onPlayheadTimeClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(PLAYHEAD_LABEL_VISUAL_HEIGHT_DP.dp)
                .clip(shape)
                .border(PLAYHEAD_LABEL_BORDER_DP.dp, visuals.colors.action, shape),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = formatTime(state.previewTimeMs),
                style = visuals.typography.caption.copy(
                    color = visuals.colors.card.primaryText,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

internal val CHART_WINDOW = DeviceLightCustomChartWindow(0L, MILLIS_PER_DAY)
internal const val PLAYHEAD_LABEL_SPACE_DP = 48
internal const val CHART_PERCENT_AXIS_WIDTH_DP = 38
internal const val CHART_HEIGHT_DP = 218
private const val PLAYHEAD_LABEL_WIDTH_DP = 58
private const val PLAYHEAD_LABEL_TOUCH_HEIGHT_DP = 48
private const val PLAYHEAD_LABEL_VISUAL_HEIGHT_DP = 30
private const val PLAYHEAD_LABEL_CORNER_DP = 8
private const val PLAYHEAD_LABEL_BORDER_DP = 1
