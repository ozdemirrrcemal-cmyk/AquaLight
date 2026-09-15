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
    val handleWidth = PLAYHEAD_HANDLE_WIDTH_DP.dp
    val maximumHandleX = (layout.availableWidth - handleWidth).coerceAtLeast(0.dp)
    val handleX = (layout.axisWidth + layout.plotWidth * playheadFraction - handleWidth / 2f)
        .coerceIn(0.dp, maximumHandleX)
    val plotWidthPx = with(LocalDensity.current) { layout.plotWidth.toPx() }
    val enabled = state.contentEnabled && !state.operationInProgress

    PlayheadTimeBubble(
        timeMs = state.previewTimeMs,
        enabled = enabled,
        onClick = actions.onPlayheadTimeClick,
        visuals = visuals,
        modifier = Modifier.offset(x = bubbleX).width(bubbleWidth)
    )
    PlayheadDragHandle(
        state = DeviceLightCustomPlayheadState(state.previewTimeMs, enabled, plotWidthPx),
        actions = actions,
        modifier = Modifier.offset(
            x = handleX,
            y = (PLAYHEAD_LABEL_SPACE_DP + CHART_HEIGHT_DP - PLAYHEAD_HANDLE_HEIGHT_DP).dp
        ).width(handleWidth).height(PLAYHEAD_HANDLE_HEIGHT_DP.dp)
    )
}

@Composable
private fun PlayheadDragHandle(
    state: DeviceLightCustomPlayheadState,
    actions: DeviceLightCustomCurveActions,
    modifier: Modifier
) {
    val currentTimeMs by rememberUpdatedState(state.timeMs)
    Box(
        modifier = modifier.pointerInput(state.enabled, state.chartWidthPx) {
            if (!state.enabled || state.chartWidthPx <= 0f) return@pointerInput
            var playheadX = 0f
            var moved = false
            detectHorizontalDragGestures(
                onDragStart = {
                    playheadX = chartX(
                        currentTimeMs,
                        state.chartWidthPx,
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
                playheadX = (playheadX + dragAmount).coerceIn(0f, state.chartWidthPx)
                actions.onPlayheadChanged(playheadTimeForX(playheadX, state.chartWidthPx))
            }
        }.clearAndSetSemantics { contentDescription = formatTime(state.timeMs) }
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

internal val CHART_WINDOW = DeviceLightCustomChartWindow(0L, MILLIS_PER_DAY)
internal const val PLAYHEAD_LABEL_SPACE_DP = 48
internal const val CHART_PERCENT_AXIS_WIDTH_DP = 38
internal const val CHART_HEIGHT_DP = 218
private const val PLAYHEAD_LABEL_WIDTH_DP = 58
private const val PLAYHEAD_LABEL_TOUCH_HEIGHT_DP = 48
private const val PLAYHEAD_LABEL_VISUAL_HEIGHT_DP = 30
private const val PLAYHEAD_LABEL_CORNER_DP = 8
private const val PLAYHEAD_LABEL_BORDER_DP = 1
private const val PLAYHEAD_HANDLE_WIDTH_DP = 56
private const val PLAYHEAD_HANDLE_HEIGHT_DP = 48
