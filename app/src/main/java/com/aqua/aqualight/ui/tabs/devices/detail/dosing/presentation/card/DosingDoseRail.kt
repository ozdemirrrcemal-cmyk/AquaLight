package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    grouping: DosingDoseRailGrouping = DosingDoseRailGrouping()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DosingDoseRailBody(state, palette, typography, grouping)
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
    grouping: DosingDoseRailGrouping
) {
    val deliveredLabel = stringResource(
        R.string.device_dosing_card_progress_delivered_format,
        state.scheduledDeliveredTodayMl
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_VALUE_TAG_AREA_HEIGHT + PROGRESS_RAIL_HEIGHT)
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PROGRESS_RAIL_HEIGHT)
        ) {
            drawDoseRail(state.occurrences, grouping, palette)
        }
        if (state.scheduledDeliveredTodayMl > 0.0 && state.dailyDoseMl > 0.0) {
            val deliveredFraction = (
                state.scheduledDeliveredTodayMl / state.dailyDoseMl
                ).coerceIn(0.0, 1.0).toFloat()
            val maximumTagStart = (maxWidth - VALUE_TAG_WIDTH).coerceAtLeast(0.dp)
            val tagStart = (maxWidth * deliveredFraction - VALUE_TAG_WIDTH / 2)
                .coerceIn(0.dp, maximumTagStart)
            DosingDeliveredValueTag(
                label = deliveredLabel,
                palette = palette,
                typography = typography,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = tagStart)
            )
        }
    }
}

@Composable
private fun DosingDeliveredValueTag(
    label: String,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(VALUE_TAG_CORNER_RADIUS)
    Box(
        modifier = modifier
            .width(VALUE_TAG_WIDTH)
            .height(PROGRESS_VALUE_TAG_AREA_HEIGHT)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(min = VALUE_TAG_MIN_WIDTH, max = VALUE_TAG_WIDTH)
                .height(VALUE_TAG_HEIGHT)
                .clip(shape)
                .background(palette.tagSurface)
                .border(VALUE_TAG_OUTLINE_WIDTH, palette.tagOutline, shape)
                .padding(horizontal = VALUE_TAG_HORIZONTAL_PADDING),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = label,
                style = typography.micro.copy(
                    color = palette.valueText,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(VALUE_TAG_POINTER_WIDTH)
                .height(VALUE_TAG_POINTER_HEIGHT)
        ) {
            drawValueTagPointer(palette)
        }
    }
}

private fun DrawScope.drawValueTagPointer(palette: DosingProgressPalette) {
    val pointer = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width / 2f, size.height)
        lineTo(size.width, 0f)
        close()
    }
    drawPath(pointer, palette.tagSurface)
    drawLine(
        color = palette.tagOutline,
        start = Offset(0f, 0f),
        end = Offset(size.width / 2f, size.height),
        strokeWidth = VALUE_TAG_OUTLINE_WIDTH.toPx()
    )
    drawLine(
        color = palette.tagOutline,
        start = Offset(size.width, 0f),
        end = Offset(size.width / 2f, size.height),
        strokeWidth = VALUE_TAG_OUTLINE_WIDTH.toPx()
    )
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
                val halfAccentWidth = MARKER_ACCENT_WIDTH.toPx() / 2f
                val x = (size.width * marker.positionFraction.coerceIn(0f, 1f))
                    .coerceIn(halfAccentWidth, size.width - halfAccentWidth)
                drawRoundRect(
                    color = palette.active.copy(alpha = MARKER_ACCENT_ALPHA),
                    topLeft = Offset(x - halfAccentWidth, MARKER_ACCENT_TOP.toPx()),
                    size = Size(MARKER_ACCENT_WIDTH.toPx(), MARKER_ACCENT_HEIGHT.toPx()),
                    cornerRadius = CornerRadius(MARKER_ACCENT_HEIGHT.toPx() / 2f)
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
            R.string.device_dosing_card_progress_marker_format,
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
    grouping: DosingDoseRailGrouping,
    palette: DosingProgressPalette
) {
    val railRadius = RAIL_CORNER_RADIUS.toPx().coerceAtMost(size.height / 2f)
    drawRoundRect(
        color = palette.track,
        size = size,
        cornerRadius = CornerRadius(railRadius)
    )
    occurrences.forEachIndexed { index, occurrence ->
        val leadingInset = segmentInset(index, occurrences.size, grouping)
        val trailingInset = segmentInset(index + 1, occurrences.size, grouping)
        val left = size.width * occurrence.startFraction.coerceIn(0f, 1f) + leadingInset
        val right = size.width * occurrence.endFraction.coerceIn(0f, 1f) - trailingInset
        if (right > left) {
            drawDoseSegment(
                occurrence = occurrence,
                left = left,
                right = right,
                singleSegment = occurrences.size == 1,
                palette = palette
            )
        }
    }
}

private fun DrawScope.drawDoseSegment(
    occurrence: DosingProgressOccurrenceUiState,
    left: Float,
    right: Float,
    singleSegment: Boolean,
    palette: DosingProgressPalette
) {
    val segmentSize = Size(right - left, size.height)
    val cornerRadius = CornerRadius(
        if (singleSegment) RAIL_CORNER_RADIUS.toPx() else SEGMENT_CORNER_RADIUS.toPx()
    )
    drawRoundRect(
        color = palette.colorFor(occurrence.visualState),
        topLeft = Offset(left, 0f),
        size = segmentSize,
        cornerRadius = cornerRadius
    )
}

private fun DrawScope.segmentInset(
    index: Int,
    occurrenceCount: Int,
    grouping: DosingDoseRailGrouping
): Float = when {
    index <= 0 || index >= occurrenceCount -> 0f
    index in grouping.breaks -> grouping.gap.toPx() / 2f
    else -> SEGMENT_GAP.toPx() / 2f
}

internal fun DosingProgressPalette.colorFor(state: DosingOccurrenceVisualState): Color = when (state) {
    DosingOccurrenceVisualState.PENDING -> pending
    DosingOccurrenceVisualState.ACTIVE -> active
    DosingOccurrenceVisualState.COMPLETED -> completed
    DosingOccurrenceVisualState.SKIPPED -> skipped
    DosingOccurrenceVisualState.UNCERTAIN -> uncertain
}

internal data class DosingDoseRailGrouping(
    val breaks: Set<Int> = emptySet(),
    val gap: Dp = DEFAULT_GROUP_GAP
)

private val VALUE_TAG_WIDTH = 56.dp
private val VALUE_TAG_MIN_WIDTH = 42.dp
private val VALUE_TAG_HEIGHT = 16.dp
private val VALUE_TAG_POINTER_WIDTH = 7.dp
private val VALUE_TAG_POINTER_HEIGHT = 4.dp
private val VALUE_TAG_CORNER_RADIUS = 5.dp
private val VALUE_TAG_OUTLINE_WIDTH = 0.75.dp
private val VALUE_TAG_HORIZONTAL_PADDING = 5.dp
private val MARKER_SCALE_HEIGHT = 18.dp
private val MARKER_ACCENT_WIDTH = 5.dp
private val MARKER_ACCENT_HEIGHT = 1.5.dp
private val MARKER_ACCENT_TOP = 2.dp
private val MARKER_LABEL_TOP = 4.dp
private val MARKER_LABEL_WIDTH = 44.dp
private val SEGMENT_GAP = 1.dp
private val DEFAULT_GROUP_GAP = 4.dp
private val RAIL_CORNER_RADIUS = 4.dp
private val SEGMENT_CORNER_RADIUS = 2.dp
private const val MARKER_ACCENT_ALPHA = 0.72f
