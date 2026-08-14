package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun DosingSingleProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette
) {
    val occurrence = state.occurrences.first()
    val fraction = if (state.dailyDoseMl <= 0.0) {
        0f
    } else {
        (state.scheduledDeliveredTodayMl / state.dailyDoseMl).coerceIn(0.0, 1.0).toFloat()
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_GRAPHIC_HEIGHT)
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = palette.track,
            size = size,
            cornerRadius = CornerRadius(radius)
        )
        if (fraction > 0f) {
            drawRoundRect(
                color = palette.colorFor(occurrence.visualState),
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(radius)
            )
        }
        drawRoundRect(
            color = palette.outline,
            size = size,
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = PROGRESS_OUTLINE_WIDTH.toPx())
        )
        val endX = when (occurrence.visualState) {
            DosingOccurrenceVisualState.ACTIVE ->
                (size.width * fraction).coerceIn(radius, size.width - radius)
            else -> size.width - radius
        }
        drawCircle(
            color = palette.colorFor(occurrence.visualState),
            radius = SINGLE_STATUS_RADIUS.toPx(),
            center = Offset(endX, size.height / 2f)
        )
        drawCircle(
            color = palette.outline,
            radius = SINGLE_STATUS_RADIUS.toPx(),
            center = Offset(endX, size.height / 2f),
            style = Stroke(width = SINGLE_STATUS_OUTLINE.toPx())
        )
    }
}

@Composable
internal fun DosingHourlyProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette
) {
    val rows = state.occurrences.chunked(HOURLY_CELLS_PER_ROW)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_GRAPHIC_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(HOURLY_ROW_GAP)
    ) {
        rows.forEach { occurrences ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(HOURLY_CELL_GAP)
            ) {
                occurrences.forEach { occurrence ->
                    DosingProgressCell(
                        state = occurrence.visualState,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat((HOURLY_CELLS_PER_ROW - occurrences.size).coerceAtLeast(0)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun DosingCustomProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_GRAPHIC_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(CUSTOM_PERIOD_GAP)
    ) {
        state.customPeriods.forEach { period ->
            val shape = RoundedCornerShape(CUSTOM_PERIOD_CORNER_RADIUS)
            Row(
                modifier = Modifier
                    .weight(period.occurrences.size.coerceAtLeast(1).toFloat())
                    .fillMaxSize()
                    .clip(shape)
                    .background(palette.track)
                    .border(PROGRESS_OUTLINE_WIDTH, palette.outline, shape)
                    .padding(CUSTOM_PERIOD_PADDING),
                horizontalArrangement = Arrangement.spacedBy(CUSTOM_CELL_GAP)
            ) {
                period.occurrences.forEach { occurrence ->
                    DosingProgressCell(
                        state = occurrence.visualState,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DosingTimerProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_GRAPHIC_HEIGHT)
    ) {
        val centerY = size.height / 2f
        val horizontalInset = TIMER_NODE_RADIUS.toPx()
        drawLine(
            color = palette.outline,
            start = Offset(horizontalInset, centerY),
            end = Offset(size.width - horizontalInset, centerY),
            strokeWidth = TIMER_TRACK_WIDTH.toPx()
        )
        state.occurrences.forEachIndexed { index, occurrence ->
            val x = horizontalInset +
                occurrence.timeFraction.coerceIn(0f, 1f) * (size.width - horizontalInset * 2f)
            val y = centerY + if (index % 2 == 0) -TIMER_LANE_OFFSET.toPx() else
                TIMER_LANE_OFFSET.toPx()
            drawLine(
                color = palette.outline,
                start = Offset(x, centerY),
                end = Offset(x, y),
                strokeWidth = TIMER_STEM_WIDTH.toPx()
            )
            drawCircle(
                color = palette.colorFor(occurrence.visualState),
                radius = TIMER_NODE_RADIUS.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = palette.outline,
                radius = TIMER_NODE_RADIUS.toPx(),
                center = Offset(x, y),
                style = Stroke(width = TIMER_NODE_OUTLINE.toPx())
            )
        }
    }
}

@Composable
private fun DosingProgressCell(
    state: DosingOccurrenceVisualState,
    palette: DosingProgressPalette,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(PROGRESS_CELL_CORNER_RADIUS)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(palette.colorFor(state))
            .border(
                width = if (state == DosingOccurrenceVisualState.ACTIVE) {
                    ACTIVE_CELL_OUTLINE
                } else {
                    PROGRESS_CELL_OUTLINE
                },
                color = if (state == DosingOccurrenceVisualState.ACTIVE) {
                    palette.active
                } else {
                    palette.outline
                },
                shape = shape
            )
    )
}

private fun DosingProgressPalette.colorFor(state: DosingOccurrenceVisualState): Color = when (state) {
    DosingOccurrenceVisualState.PENDING -> pending
    DosingOccurrenceVisualState.ACTIVE -> active
    DosingOccurrenceVisualState.COMPLETED -> completed
    DosingOccurrenceVisualState.SKIPPED -> skipped
    DosingOccurrenceVisualState.UNCERTAIN -> uncertain
}

private const val HOURLY_CELLS_PER_ROW = 12
private val SINGLE_STATUS_RADIUS = 4.dp
private val SINGLE_STATUS_OUTLINE = 1.dp
private val HOURLY_ROW_GAP = 4.dp
private val HOURLY_CELL_GAP = 3.dp
private val CUSTOM_PERIOD_GAP = 5.dp
private val CUSTOM_PERIOD_PADDING = 3.dp
private val CUSTOM_PERIOD_CORNER_RADIUS = 7.dp
private val CUSTOM_CELL_GAP = 2.dp
private val PROGRESS_CELL_CORNER_RADIUS = 3.dp
private val PROGRESS_CELL_OUTLINE = 0.5.dp
private val ACTIVE_CELL_OUTLINE = 1.25.dp
private val TIMER_NODE_RADIUS = 3.5.dp
private val TIMER_NODE_OUTLINE = 0.75.dp
private val TIMER_LANE_OFFSET = 3.dp
private val TIMER_TRACK_WIDTH = 1.5.dp
private val TIMER_STEM_WIDTH = 1.dp
