package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingDoseRail(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier,
    groupBreaks: Set<Int> = emptySet()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DosingDoseRailBody(state, palette, typography, groupBreaks)
        if (state.markers.isNotEmpty()) {
            DosingProgressMarkerScale(state.markers, palette, typography)
        }
    }
}

@Composable
private fun DosingDoseRailBody(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    groupBreaks: Set<Int>
) {
    val deliveredLabel = stringResource(
        R.string.device_dosing_channel_progress_delivered_format,
        state.scheduledDeliveredTodayMl
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_RAIL_HEIGHT)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawDoseRail(state.occurrences, groupBreaks, palette)
        }
        if (state.scheduledDeliveredTodayMl > 0.0 && state.dailyDoseMl > 0.0) {
            val deliveredFraction = (
                state.scheduledDeliveredTodayMl / state.dailyDoseMl
                ).coerceIn(0.0, 1.0).toFloat()
            val deliveredWidth = (maxWidth * deliveredFraction)
                .coerceAtLeast(INLINE_VALUE_MIN_WIDTH)
                .coerceAtMost(maxWidth)
            Box(
                modifier = Modifier
                    .width(deliveredWidth)
                    .height(PROGRESS_RAIL_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = deliveredLabel,
                    style = typography.micro.copy(
                        color = palette.inlineValueText,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun DosingProgressMarkerScale(
    markers: List<DosingProgressMarkerUiState>,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(MARKER_SCALE_HEIGHT)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            markers.forEach { marker ->
                val halfStrokeWidth = MARKER_TICK_WIDTH.toPx() / 2f
                val x = (size.width * marker.positionFraction.coerceIn(0f, 1f))
                    .coerceIn(halfStrokeWidth, size.width - halfStrokeWidth)
                drawLine(
                    color = palette.outline,
                    start = Offset(x, 0f),
                    end = Offset(x, MARKER_TICK_HEIGHT.toPx()),
                    strokeWidth = MARKER_TICK_WIDTH.toPx()
                )
            }
        }
        markers.forEach { marker ->
            DosingProgressMarkerLabel(marker, palette, typography, maxWidth)
        }
    }
}

@Composable
private fun DosingProgressMarkerLabel(
    marker: DosingProgressMarkerUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    availableWidth: Dp
) {
    val markerCenter = availableWidth * marker.positionFraction.coerceIn(0f, 1f)
    val markerStart = (markerCenter - MARKER_LABEL_WIDTH / 2)
        .coerceIn(0.dp, (availableWidth - MARKER_LABEL_WIDTH).coerceAtLeast(0.dp))
    BasicText(
        text = stringResource(
            R.string.device_dosing_channel_progress_marker_format,
            marker.cumulativeAmountMl
        ),
        modifier = Modifier
            .offset(x = markerStart, y = MARKER_LABEL_TOP)
            .width(MARKER_LABEL_WIDTH),
        style = typography.micro.copy(
            color = palette.valueText,
            textAlign = TextAlign.Center
        ),
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}

private fun DrawScope.drawDoseRail(
    occurrences: List<DosingProgressOccurrenceUiState>,
    groupBreaks: Set<Int>,
    palette: DosingProgressPalette
) {
    val railRadius = size.height / 2f
    drawRoundRect(
        color = palette.track,
        size = size,
        cornerRadius = CornerRadius(railRadius)
    )
    drawDoseSegments(occurrences, groupBreaks, palette)
    drawRoundRect(
        color = palette.outline,
        size = size,
        cornerRadius = CornerRadius(railRadius),
        style = Stroke(width = PROGRESS_OUTLINE_WIDTH.toPx())
    )
}

private fun DrawScope.drawDoseSegments(
    occurrences: List<DosingProgressOccurrenceUiState>,
    groupBreaks: Set<Int>,
    palette: DosingProgressPalette
) {
    occurrences.forEachIndexed { index, occurrence ->
        val leadingInset = segmentInset(index, occurrences.size, groupBreaks)
        val trailingInset = segmentInset(index + 1, occurrences.size, groupBreaks)
        val left = size.width * occurrence.startFraction.coerceIn(0f, 1f) + leadingInset
        val right = size.width * occurrence.endFraction.coerceIn(0f, 1f) - trailingInset
        if (right > left) {
            drawDoseSegment(occurrence, left, right, palette)
        }
    }
}

private fun DrawScope.drawDoseSegment(
    occurrence: DosingProgressOccurrenceUiState,
    left: Float,
    right: Float,
    palette: DosingProgressPalette
) {
    val segmentSize = Size(right - left, size.height)
    val cornerRadius = CornerRadius(SEGMENT_CORNER_RADIUS.toPx())
    drawRoundRect(
        color = palette.colorFor(occurrence.visualState),
        topLeft = Offset(left, 0f),
        size = segmentSize,
        cornerRadius = cornerRadius
    )
    if (occurrence.visualState == DosingOccurrenceVisualState.ACTIVE) {
        drawRoundRect(
            color = palette.active,
            topLeft = Offset(left, 0f),
            size = segmentSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = ACTIVE_SEGMENT_OUTLINE.toPx())
        )
    }
}

private fun DrawScope.segmentInset(
    index: Int,
    occurrenceCount: Int,
    groupBreaks: Set<Int>
): Float = when {
    index <= 0 || index >= occurrenceCount -> 0f
    index in groupBreaks -> CUSTOM_GROUP_GAP.toPx() / 2f
    else -> SEGMENT_GAP.toPx() / 2f
}

private fun DosingProgressPalette.colorFor(state: DosingOccurrenceVisualState): Color = when (state) {
    DosingOccurrenceVisualState.PENDING,
    DosingOccurrenceVisualState.ACTIVE -> pending
    DosingOccurrenceVisualState.COMPLETED -> completed
    DosingOccurrenceVisualState.SKIPPED -> skipped
    DosingOccurrenceVisualState.UNCERTAIN -> uncertain
}

private val INLINE_VALUE_MIN_WIDTH = 58.dp
private val MARKER_SCALE_HEIGHT = 18.dp
private val MARKER_TICK_HEIGHT = 4.dp
private val MARKER_TICK_WIDTH = 1.dp
private val MARKER_LABEL_TOP = 4.dp
private val MARKER_LABEL_WIDTH = 44.dp
private val SEGMENT_GAP = 1.dp
private val CUSTOM_GROUP_GAP = 5.dp
private val SEGMENT_CORNER_RADIUS = 2.dp
private val ACTIVE_SEGMENT_OUTLINE = 1.25.dp
