package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingScheduledProgress(
    progress: DosingScheduledProgressUiState,
    manualUsage: DosingManualUsageUiState?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val amountLabel = stringResource(
        R.string.device_dosing_card_progress_amount,
        progress.completedAmountMl,
        progress.totalAmountMl
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colors.mediaSurface.copy(alpha = PROGRESS_SURFACE_ALPHA),
                shape = PROGRESS_SHAPE
            )
            .border(
                width = PROGRESS_OUTLINE_WIDTH,
                color = colors.mediaOutline.copy(alpha = PROGRESS_OUTLINE_ALPHA),
                shape = PROGRESS_SHAPE
            )
            .padding(PROGRESS_CONTENT_PADDING)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PROGRESS_CONTENT_GAP)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = amountLabel,
                    modifier = Modifier.weight(1f),
                    style = typography.micro.copy(color = colors.primaryText)
                )
                if (manualUsage != null && manualUsage.deliveredMlToday > 0.0) {
                    ManualDoseBadge(
                        manualUsage = manualUsage,
                        colors = colors,
                        typography = typography
                    )
                }
            }

            when (progress.mode) {
                DosingProgramModeUi.SINGLE -> SingleProgressTrack(progress, colors)
                DosingProgramModeUi.HOURLY_24 -> HourlyProgressTrack(progress, colors)
                DosingProgramModeUi.CUSTOM_PERIODS -> CustomPeriodProgressTrack(progress, colors)
                DosingProgramModeUi.TIMER -> TimerProgressTrack(progress, colors)
            }
        }
    }
}

@Composable
private fun ManualDoseBadge(
    manualUsage: DosingManualUsageUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(MANUAL_BADGE_CORNER_RADIUS)
    Row(
        modifier = Modifier
            .background(colors.mediaSurface, shape)
            .border(
                width = PROGRESS_OUTLINE_WIDTH,
                color = colors.mediaOutline,
                shape = shape
            )
            .padding(horizontal = MANUAL_BADGE_HORIZONTAL_PADDING, vertical = MANUAL_BADGE_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(MANUAL_BADGE_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingCardMetricGlyph(
            icon = DosingCardMetricIcon.MANUAL,
            tint = colors.secondaryText,
            modifier = Modifier.size(MANUAL_BADGE_ICON_SIZE)
        )
        BasicText(
            text = stringResource(
                R.string.device_dosing_card_manual_amount,
                manualUsage.deliveredMlToday
            ),
            style = typography.micro.copy(color = colors.secondaryText)
        )
    }
}

@Composable
private fun SingleProgressTrack(
    progress: DosingScheduledProgressUiState,
    colors: AquaDeviceCardColors
) {
    val occurrence = progress.occurrences.firstOrNull()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(SINGLE_TRACK_HEIGHT)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = colors.mediaOutline.copy(alpha = TRACK_BACKGROUND_ALPHA),
            cornerRadius = radius
        )
        val status = occurrence?.status ?: DosingOccurrenceVisualStatus.PENDING
        val inset = SINGLE_TRACK_INSET.toPx()
        drawOccurrenceBlock(
            status = status,
            colors = colors,
            topLeft = Offset(inset, inset),
            blockSize = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius((size.height - inset * 2f) / 2f)
        )
    }
}

@Composable
private fun HourlyProgressTrack(
    progress: DosingScheduledProgressUiState,
    colors: AquaDeviceCardColors
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(MICRO_TRACK_HEIGHT)
    ) {
        val count = HOUR_OCCURRENCE_COUNT
        val gap = MICRO_SEGMENT_GAP.toPx()
        val width = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        repeat(count) { index ->
            val status = progress.occurrences
                .firstOrNull { occurrence -> occurrence.index == index }
                ?.status ?: DosingOccurrenceVisualStatus.PENDING
            drawOccurrenceBlock(
                status = status,
                colors = colors,
                topLeft = Offset(index * (width + gap), 0f),
                blockSize = Size(width, size.height),
                cornerRadius = CornerRadius(width.coerceAtMost(size.height) * MICRO_CORNER_RATIO)
            )
        }
    }
}

@Composable
private fun CustomPeriodProgressTrack(
    progress: DosingScheduledProgressUiState,
    colors: AquaDeviceCardColors
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(MICRO_TRACK_HEIGHT)
    ) {
        val groups = progress.customPeriodGroups.filter { it > 0 }
        val total = groups.sum().takeIf { it > 0 } ?: progress.occurrences.size.coerceAtLeast(1)
        val groupCount = groups.size.coerceAtLeast(1)
        val innerGaps = (total - groupCount).coerceAtLeast(0)
        val segmentGap = MICRO_SEGMENT_GAP.toPx()
        val groupGap = CUSTOM_GROUP_GAP.toPx()
        val available = size.width - segmentGap * innerGaps - groupGap * (groupCount - 1)
        val segmentWidth = (available / total).coerceAtLeast(1f)
        val effectiveGroups = if (groups.isEmpty()) listOf(total) else groups
        var x = 0f
        var occurrenceIndex = 0

        effectiveGroups.forEachIndexed { groupIndex, groupSize ->
            repeat(groupSize) { indexInGroup ->
                val status = progress.occurrences
                    .firstOrNull { occurrence -> occurrence.index == occurrenceIndex }
                    ?.status ?: DosingOccurrenceVisualStatus.PENDING
                drawOccurrenceBlock(
                    status = status,
                    colors = colors,
                    topLeft = Offset(x, 0f),
                    blockSize = Size(segmentWidth, size.height),
                    cornerRadius = CornerRadius(segmentWidth.coerceAtMost(size.height) * MICRO_CORNER_RATIO)
                )
                occurrenceIndex += 1
                x += segmentWidth
                if (indexInGroup != groupSize - 1) x += segmentGap
            }
            if (groupIndex != effectiveGroups.lastIndex) x += groupGap
        }
    }
}

@Composable
private fun TimerProgressTrack(
    progress: DosingScheduledProgressUiState,
    colors: AquaDeviceCardColors
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(TIMER_TRACK_HEIGHT)
    ) {
        val railY = size.height / 2f
        drawLine(
            color = colors.mediaOutline,
            start = Offset(0f, railY),
            end = Offset(size.width, railY),
            strokeWidth = TIMER_RAIL_WIDTH.toPx(),
            cap = StrokeCap.Round
        )

        progress.occurrences.forEach { occurrence ->
            val normalized = (occurrence.timeMs.toFloat() / DAY_MS.toFloat()).coerceIn(0f, 1f)
            val x = normalized * size.width
            drawTimerMarker(
                center = Offset(x, railY),
                status = occurrence.status,
                colors = colors
            )
        }
    }
}

private fun DrawScope.drawOccurrenceBlock(
    status: DosingOccurrenceVisualStatus,
    colors: AquaDeviceCardColors,
    topLeft: Offset,
    blockSize: Size,
    cornerRadius: CornerRadius
) {
    val fill = occurrenceFill(status, colors)
    val outline = occurrenceOutline(status, colors)
    drawRoundRect(
        color = fill,
        topLeft = topLeft,
        size = blockSize,
        cornerRadius = cornerRadius
    )
    if (status != DosingOccurrenceVisualStatus.COMPLETED) {
        drawRoundRect(
            color = outline,
            topLeft = topLeft,
            size = blockSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = OCCURRENCE_OUTLINE_WIDTH.toPx())
        )
    }
    if (status == DosingOccurrenceVisualStatus.SKIPPED) {
        drawLine(
            color = colors.secondaryText,
            start = Offset(topLeft.x + blockSize.width * SKIP_LINE_START, topLeft.y + blockSize.height * SKIP_LINE_END),
            end = Offset(topLeft.x + blockSize.width * SKIP_LINE_END, topLeft.y + blockSize.height * SKIP_LINE_START),
            strokeWidth = OCCURRENCE_OUTLINE_WIDTH.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawTimerMarker(
    center: Offset,
    status: DosingOccurrenceVisualStatus,
    colors: AquaDeviceCardColors
) {
    val radius = TIMER_MARKER_RADIUS.toPx()
    drawCircle(
        color = occurrenceFill(status, colors),
        radius = radius,
        center = center
    )
    drawCircle(
        color = occurrenceOutline(status, colors),
        radius = radius,
        center = center,
        style = Stroke(width = OCCURRENCE_OUTLINE_WIDTH.toPx())
    )
    if (status == DosingOccurrenceVisualStatus.RUNNING) {
        drawCircle(
            color = colors.accent.copy(alpha = RUNNING_HALO_ALPHA),
            radius = radius * RUNNING_HALO_RATIO,
            center = center,
            style = Stroke(width = OCCURRENCE_OUTLINE_WIDTH.toPx())
        )
    }
}

private fun occurrenceFill(
    status: DosingOccurrenceVisualStatus,
    colors: AquaDeviceCardColors
): Color = when (status) {
    DosingOccurrenceVisualStatus.COMPLETED -> colors.accent
    DosingOccurrenceVisualStatus.RUNNING -> colors.accent.copy(alpha = RUNNING_FILL_ALPHA)
    DosingOccurrenceVisualStatus.PENDING -> colors.mediaSurface
    DosingOccurrenceVisualStatus.SKIPPED -> colors.secondaryText.copy(alpha = SKIPPED_FILL_ALPHA)
    DosingOccurrenceVisualStatus.UNCERTAIN -> colors.warning.copy(alpha = UNCERTAIN_FILL_ALPHA)
}

private fun occurrenceOutline(
    status: DosingOccurrenceVisualStatus,
    colors: AquaDeviceCardColors
): Color = when (status) {
    DosingOccurrenceVisualStatus.COMPLETED,
    DosingOccurrenceVisualStatus.RUNNING -> colors.accent
    DosingOccurrenceVisualStatus.PENDING,
    DosingOccurrenceVisualStatus.SKIPPED -> colors.secondaryText.copy(alpha = PENDING_OUTLINE_ALPHA)
    DosingOccurrenceVisualStatus.UNCERTAIN -> colors.warning
}

private const val DAY_MS = 86_400_000L
private const val HOUR_OCCURRENCE_COUNT = 24
private const val PROGRESS_SURFACE_ALPHA = 0.55f
private const val PROGRESS_OUTLINE_ALPHA = 0.55f
private const val TRACK_BACKGROUND_ALPHA = 0.50f
private const val RUNNING_FILL_ALPHA = 0.56f
private const val RUNNING_HALO_ALPHA = 0.54f
private const val RUNNING_HALO_RATIO = 1.55f
private const val SKIPPED_FILL_ALPHA = 0.12f
private const val UNCERTAIN_FILL_ALPHA = 0.16f
private const val PENDING_OUTLINE_ALPHA = 0.55f
private const val MICRO_CORNER_RATIO = 0.34f
private const val SKIP_LINE_START = 0.25f
private const val SKIP_LINE_END = 0.75f
private const val SINGLE_TRACK_HEIGHT_DP = 18
private const val MICRO_TRACK_HEIGHT_DP = 12
private const val TIMER_TRACK_HEIGHT_DP = 18
private const val PROGRESS_CORNER_RADIUS_DP = 12
private const val PROGRESS_OUTLINE_WIDTH_DP = 1
private const val PROGRESS_CONTENT_PADDING_DP = 8
private const val PROGRESS_CONTENT_GAP_DP = 7
private const val SINGLE_TRACK_INSET_DP = 2
private const val MICRO_SEGMENT_GAP_DP = 2
private const val CUSTOM_GROUP_GAP_DP = 8
private const val TIMER_RAIL_WIDTH_DP = 2
private const val TIMER_MARKER_RADIUS_DP = 4
private const val OCCURRENCE_OUTLINE_WIDTH_DP = 1
private const val MANUAL_BADGE_CORNER_RADIUS_DP = 9
private const val MANUAL_BADGE_HORIZONTAL_PADDING_DP = 6
private const val MANUAL_BADGE_VERTICAL_PADDING_DP = 3
private const val MANUAL_BADGE_GAP_DP = 4
private const val MANUAL_BADGE_ICON_SIZE_DP = 11
private val SINGLE_TRACK_HEIGHT = SINGLE_TRACK_HEIGHT_DP.dp
private val MICRO_TRACK_HEIGHT = MICRO_TRACK_HEIGHT_DP.dp
private val TIMER_TRACK_HEIGHT = TIMER_TRACK_HEIGHT_DP.dp
private val PROGRESS_SHAPE = RoundedCornerShape(PROGRESS_CORNER_RADIUS_DP.dp)
private val PROGRESS_OUTLINE_WIDTH = PROGRESS_OUTLINE_WIDTH_DP.dp
private val PROGRESS_CONTENT_PADDING = PROGRESS_CONTENT_PADDING_DP.dp
private val PROGRESS_CONTENT_GAP = PROGRESS_CONTENT_GAP_DP.dp
private val SINGLE_TRACK_INSET = SINGLE_TRACK_INSET_DP.dp
private val MICRO_SEGMENT_GAP = MICRO_SEGMENT_GAP_DP.dp
private val CUSTOM_GROUP_GAP = CUSTOM_GROUP_GAP_DP.dp
private val TIMER_RAIL_WIDTH = TIMER_RAIL_WIDTH_DP.dp
private val TIMER_MARKER_RADIUS = TIMER_MARKER_RADIUS_DP.dp
private val OCCURRENCE_OUTLINE_WIDTH = OCCURRENCE_OUTLINE_WIDTH_DP.dp
private val MANUAL_BADGE_CORNER_RADIUS = MANUAL_BADGE_CORNER_RADIUS_DP.dp
private val MANUAL_BADGE_HORIZONTAL_PADDING = MANUAL_BADGE_HORIZONTAL_PADDING_DP.dp
private val MANUAL_BADGE_VERTICAL_PADDING = MANUAL_BADGE_VERTICAL_PADDING_DP.dp
private val MANUAL_BADGE_GAP = MANUAL_BADGE_GAP_DP.dp
private val MANUAL_BADGE_ICON_SIZE = MANUAL_BADGE_ICON_SIZE_DP.dp
