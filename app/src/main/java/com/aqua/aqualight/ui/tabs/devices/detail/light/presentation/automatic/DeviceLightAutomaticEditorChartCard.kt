package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.aquaLightPlanChartColors

@Composable
internal fun DeviceLightAutomaticEditorChartCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAutomaticEditorGeometry.cardPadding
    ) {
        Column {
            EditorSectionHeading(
                title = stringResource(R.string.device_light_auto_editor_chart),
                subtitle = state.chartSummary(),
                visuals = visuals,
                icon = { color -> TimelineWaveIcon(color) }
            )
            Spacer(Modifier.height(DeviceLightAutomaticEditorGeometry.chartTopGap))
            val program = state.previewProgram
            val source = state.source
            if (program != null && source != null) {
                DeviceLightAutomaticProgramChart(
                    program = program,
                    channels = source.channels,
                    colors = visuals.colors.card,
                    chartColors = aquaLightPlanChartColors(visuals.colors.card),
                    typography = visuals.typography
                )
            } else {
                EditorChartPlaceholder(visuals)
            }
        }
    }
}

@Composable
private fun DeviceLightAutomaticProgramEditorUiState.chartSummary(): String {
    val start = draft.startTimeMs
    val end = draft.endTimeMs
    val ramp = draft.rampDurationMs
    return if (start == null || end == null || ramp == null || start == end) {
        stringResource(R.string.device_light_auto_editor_complete_settings)
    } else {
        stringResource(
            R.string.device_light_auto_editor_chart_summary,
            automaticEditorTimeText(start),
            automaticEditorTimeText(end),
            (ramp / MILLIS_PER_MINUTE).toInt()
        )
    }
}

@Composable
private fun EditorChartPlaceholder(visuals: DeviceLightAutomaticEditorVisuals) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticEditorGeometry.chartHeight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = DeviceLightAutomaticGeometry.chartGridStrokeWidth.toPx()
            repeat(CHART_VERTICAL_GRID_COUNT) { index ->
                val x = size.width * index / (CHART_VERTICAL_GRID_COUNT - 1)
                drawLine(
                    color = visuals.colors.card.outline.copy(
                        alpha = DeviceLightAutomaticEditorAlpha.neutralDial
                    ),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke
                )
            }
            repeat(CHART_HORIZONTAL_GRID_COUNT) { index ->
                val y = size.height * index / (CHART_HORIZONTAL_GRID_COUNT - 1)
                drawLine(
                    color = visuals.colors.card.outline.copy(
                        alpha = DeviceLightAutomaticEditorAlpha.neutralDial
                    ),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke
                )
            }
        }
        BasicText(
            text = stringResource(R.string.device_light_auto_editor_chart_placeholder),
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.secondaryText,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun TimelineWaveIcon(color: Color) {
    Canvas(Modifier.size(DeviceLightAutomaticEditorGeometry.sectionIconSize)) {
        val path = Path().apply {
            moveTo(0f, size.height * WAVE_CENTER_Y)
            cubicTo(
                size.width * WAVE_FIRST_CONTROL_X,
                size.height * WAVE_TOP_Y,
                size.width * WAVE_SECOND_CONTROL_X,
                size.height * WAVE_TOP_Y,
                size.width * WAVE_CENTER_X,
                size.height * WAVE_CENTER_Y
            )
            cubicTo(
                size.width * WAVE_THIRD_CONTROL_X,
                size.height * WAVE_BOTTOM_Y,
                size.width * WAVE_FOURTH_CONTROL_X,
                size.height * WAVE_BOTTOM_Y,
                size.width,
                size.height * WAVE_CENTER_Y
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = DeviceLightAutomaticGeometry.iconStrokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val CHART_VERTICAL_GRID_COUNT = 7
private const val CHART_HORIZONTAL_GRID_COUNT = 3
private const val WAVE_CENTER_Y = 0.50f
private const val WAVE_TOP_Y = 0.12f
private const val WAVE_BOTTOM_Y = 0.88f
private const val WAVE_FIRST_CONTROL_X = 0.12f
private const val WAVE_SECOND_CONTROL_X = 0.34f
private const val WAVE_CENTER_X = 0.50f
private const val WAVE_THIRD_CONTROL_X = 0.66f
private const val WAVE_FOURTH_CONTROL_X = 0.88f
