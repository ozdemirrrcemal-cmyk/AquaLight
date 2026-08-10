package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors

internal fun DrawScope.drawDoseProgressTrack(
    state: DosingDoseProgressUiState,
    colors: AquaDeviceCardColors,
    progressFraction: Float
) {
    val radius = size.height / 2f
    drawRoundRect(
        color = colors.mediaSurface,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(radius, radius)
    )
    drawDoseProgressFill(
        state = state,
        colors = colors,
        progressFraction = progressFraction,
        radius = radius
    )
    if (state.dailyDoseMl > 0.0) {
        drawDoseMajorTicks(colors)
        drawDoseMilestones(state, colors)
    }
}

private fun DrawScope.drawDoseProgressFill(
    state: DosingDoseProgressUiState,
    colors: AquaDeviceCardColors,
    progressFraction: Float,
    radius: Float
) {
    if (progressFraction <= 0f) return
    drawRoundRect(
        color = state.progressColor(colors),
        size = Size(size.width * progressFraction, size.height),
        cornerRadius = CornerRadius(radius, radius)
    )
}

private fun DrawScope.drawDoseMajorTicks(colors: AquaDeviceCardColors) {
    DOSE_MAJOR_TICK_FRACTIONS.forEach { fraction ->
        val x = size.width * fraction
        drawLine(
            color = colors.mediaOutline,
            start = Offset(x, DOSE_TICK_VERTICAL_PADDING.toPx()),
            end = Offset(x, size.height - DOSE_TICK_VERTICAL_PADDING.toPx()),
            strokeWidth = DOSE_TICK_WIDTH.toPx()
        )
    }
}

private fun DrawScope.drawDoseMilestones(
    state: DosingDoseProgressUiState,
    colors: AquaDeviceCardColors
) {
    state.doseMilestonesMl
        .asSequence()
        .filter { it > 0.0 && it < state.dailyDoseMl }
        .map { (it / state.dailyDoseMl).toFloat() }
        .distinct()
        .forEach { fraction ->
            val x = size.width * fraction
            drawLine(
                color = colors.primaryText.copy(alpha = MILESTONE_ALPHA),
                start = Offset(x, MILESTONE_VERTICAL_PADDING.toPx()),
                end = Offset(x, size.height - MILESTONE_VERTICAL_PADDING.toPx()),
                strokeWidth = MILESTONE_WIDTH.toPx()
            )
        }
}

private fun DosingDoseProgressUiState.progressColor(colors: AquaDeviceCardColors): Color = when (visualState) {
    DosingDoseProgressVisualState.EMPTY,
    DosingDoseProgressVisualState.READY,
    DosingDoseProgressVisualState.ACTIVE,
    DosingDoseProgressVisualState.COMPLETE -> colors.accent
    DosingDoseProgressVisualState.ERROR -> colors.danger
}

private const val QUARTER_PROGRESS_FRACTION = 0.25f
private const val HALF_PROGRESS_FRACTION = 0.50f
private const val THREE_QUARTER_PROGRESS_FRACTION = 0.75f
private const val MILESTONE_ALPHA = 0.72f
private const val DOSE_TICK_VERTICAL_PADDING_DP = 4
private const val DOSE_TICK_WIDTH_DP = 1
private const val MILESTONE_VERTICAL_PADDING_DP = 2
private const val MILESTONE_WIDTH_DP = 1.5f
private val DOSE_MAJOR_TICK_FRACTIONS = listOf(
    QUARTER_PROGRESS_FRACTION,
    HALF_PROGRESS_FRACTION,
    THREE_QUARTER_PROGRESS_FRACTION
)
private val DOSE_TICK_VERTICAL_PADDING = DOSE_TICK_VERTICAL_PADDING_DP.dp
private val DOSE_TICK_WIDTH = DOSE_TICK_WIDTH_DP.dp
private val MILESTONE_VERTICAL_PADDING = MILESTONE_VERTICAL_PADDING_DP.dp
private val MILESTONE_WIDTH = MILESTONE_WIDTH_DP.dp
