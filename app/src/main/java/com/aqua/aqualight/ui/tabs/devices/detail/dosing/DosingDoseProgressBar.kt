@file:Suppress("FunctionNaming", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

/**
 * Daily-dose progress rail.
 *
 * The x-axis represents cumulative delivered volume from 0 ml to the configured daily target.
 * It never represents wall-clock time. Future schedule/configuration binding may provide
 * [DosingDoseProgressUiState.doseCheckpointsMl] without changing this component's geometry.
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
        state.deliveredMl
    )
    val description = stringResource(
        R.string.device_dosing_channel_dose_progress_description,
        state.deliveredMl,
        state.dailyTargetMl
    )
    val progressFraction = state.progressFraction()
    val doseScaleLabels = if (state.dailyTargetMl > 0.0) {
        DOSE_LABEL_FRACTIONS.map { fraction ->
            stringResource(
                R.string.device_dosing_channel_dose_progress_value_format,
                state.dailyTargetMl * fraction
            )
        }
    } else {
        emptyList()
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DOSE_PROGRESS_TRACK_HEIGHT)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = colors.mediaSurface,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(radius, radius)
                )

                if (progressFraction > 0f) {
                    drawRoundRect(
                        color = state.progressColor(colors),
                        size = Size(size.width * progressFraction, size.height),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }

                if (state.dailyTargetMl > 0.0) {
                    DOSE_TICK_FRACTIONS.forEach { fraction ->
                        val x = size.width * fraction
                        drawLine(
                            color = colors.mediaOutline,
                            start = Offset(x, DOSE_TICK_VERTICAL_PADDING.toPx()),
                            end = Offset(x, size.height - DOSE_TICK_VERTICAL_PADDING.toPx()),
                            strokeWidth = DOSE_TICK_WIDTH.toPx()
                        )
                    }

                    state.doseCheckpointsMl
                        .asSequence()
                        .filter { it > 0.0 && it < state.dailyTargetMl }
                        .map { (it / state.dailyTargetMl).toFloat() }
                        .distinct()
                        .forEach { fraction ->
                            val x = size.width * fraction
                            drawLine(
                                color = colors.primaryText.copy(alpha = CHECKPOINT_ALPHA),
                                start = Offset(x, CHECKPOINT_VERTICAL_PADDING.toPx()),
                                end = Offset(x, size.height - CHECKPOINT_VERTICAL_PADDING.toPx()),
                                strokeWidth = CHECKPOINT_WIDTH.toPx()
                            )
                        }
                }
            }

            BasicText(
                text = currentDoseLabel,
                style = typography.micro.copy(color = colors.primaryText)
            )
        }

        if (doseScaleLabels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DOSE_LABEL_TOP_PADDING),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                doseScaleLabels.forEach { label ->
                    BasicText(text = label, style = typography.micro)
                }
            }
        }
    }
}

internal fun DosingDoseProgressUiState.progressFraction(): Float {
    if (dailyTargetMl <= 0.0) return 0f
    return (deliveredMl / dailyTargetMl).coerceIn(0.0, 1.0).toFloat()
}

private fun DosingDoseProgressUiState.progressColor(colors: AquaDeviceCardColors): Color = when (visualState) {
    DosingDoseProgressVisualState.EMPTY,
    DosingDoseProgressVisualState.READY,
    DosingDoseProgressVisualState.ACTIVE,
    DosingDoseProgressVisualState.COMPLETE -> colors.accent
    DosingDoseProgressVisualState.ERROR -> colors.danger
}

private val DOSE_LABEL_FRACTIONS = listOf(0.0, 0.25, 0.50, 0.75, 1.0)
private val DOSE_TICK_FRACTIONS = listOf(0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f, 0.90f)
private const val CHECKPOINT_ALPHA = 0.68f
private val DOSE_PROGRESS_TRACK_HEIGHT = 18.dp
private val DOSE_LABEL_TOP_PADDING = 2.dp
private val DOSE_TICK_VERTICAL_PADDING = 4.dp
private val DOSE_TICK_WIDTH = 1.dp
private val CHECKPOINT_VERTICAL_PADDING = 2.dp
private val CHECKPOINT_WIDTH = 1.5.dp
