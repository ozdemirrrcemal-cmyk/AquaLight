@file:Suppress("FunctionNaming", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

/**
 * Daily-dose progress bar.
 *
 * The x-axis represents cumulative delivered volume from 0 ml to the configured daily dose.
 * It never represents wall-clock time. Future channel configuration may provide cumulative
 * [DosingDoseProgressUiState.doseMilestonesMl] without changing this component's geometry.
 */
@Composable
internal fun DosingDoseProgressBar(
    state: DosingDoseProgressUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val currentDoseLabel = stringResource(
        R.string.device_dosing_channel_dose_progress_value_format,
        state.deliveredTodayMl
    )
    val description = stringResource(
        R.string.device_dosing_channel_dose_progress_description,
        state.deliveredTodayMl,
        state.dailyDoseMl
    )
    val doseScaleLabels = state.doseScaleLabels()

    Column(modifier = modifier) {
        DosingDoseProgressTrack(
            state = state,
            colors = colors,
            typography = typography,
            currentDoseLabel = currentDoseLabel,
            description = description
        )
        if (doseScaleLabels.isNotEmpty()) {
            DosingDoseScaleLabels(
                labels = doseScaleLabels,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun DosingDoseProgressUiState.doseScaleLabels(): List<String> = if (dailyDoseMl > 0.0) {
    DOSE_SCALE_FRACTIONS.map { fraction ->
        stringResource(
            R.string.device_dosing_channel_dose_scale_value_format,
            dailyDoseMl * fraction
        )
    }
} else {
    emptyList()
}

@Composable
private fun DosingDoseProgressTrack(
    state: DosingDoseProgressUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    currentDoseLabel: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DOSE_PROGRESS_TRACK_HEIGHT)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDoseProgressTrack(
                state = state,
                colors = colors,
                progressFraction = state.progressFraction()
            )
        }

        BasicText(
            text = currentDoseLabel,
            modifier = Modifier.padding(horizontal = CURRENT_DOSE_HORIZONTAL_PADDING),
            style = typography.micro.copy(color = state.progressTextColor(colors))
        )
    }
}

@Composable
private fun DosingDoseScaleLabels(
    labels: List<String>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DOSE_LABEL_TOP_PADDING),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { label ->
            BasicText(
                text = label,
                style = typography.micro.copy(color = colors.secondaryText)
            )
        }
    }
}

private fun DrawScope.drawDoseProgressTrack(
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

internal fun DosingDoseProgressUiState.progressFraction(): Float = if (dailyDoseMl <= 0.0) {
    0f
} else {
    (deliveredTodayMl / dailyDoseMl).coerceIn(0.0, 1.0).toFloat()
}

private fun DosingDoseProgressUiState.progressColor(colors: AquaDeviceCardColors): Color = when (visualState) {
    DosingDoseProgressVisualState.EMPTY,
    DosingDoseProgressVisualState.READY,
    DosingDoseProgressVisualState.ACTIVE,
    DosingDoseProgressVisualState.COMPLETE -> colors.accent
    DosingDoseProgressVisualState.ERROR -> colors.danger
}

private fun DosingDoseProgressUiState.progressTextColor(colors: AquaDeviceCardColors): Color =
    if (visualState == DosingDoseProgressVisualState.EMPTY) {
        colors.secondaryText
    } else {
        colors.primaryText
    }

private val DOSE_SCALE_FRACTIONS = listOf(0.0, 0.25, 0.50, 0.75, 1.0)
private val DOSE_MAJOR_TICK_FRACTIONS = listOf(0.25f, 0.50f, 0.75f)
private const val MILESTONE_ALPHA = 0.72f
private val DOSE_PROGRESS_TRACK_HEIGHT = 16.dp
private val CURRENT_DOSE_HORIZONTAL_PADDING = 8.dp
private val DOSE_LABEL_TOP_PADDING = 3.dp
private val DOSE_TICK_VERTICAL_PADDING = 4.dp
private val DOSE_TICK_WIDTH = 1.dp
private val MILESTONE_VERTICAL_PADDING = 2.dp
private val MILESTONE_WIDTH = 1.5.dp
