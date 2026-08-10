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
import androidx.compose.ui.graphics.Color
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

internal fun DosingDoseProgressUiState.progressFraction(): Float = if (dailyDoseMl <= 0.0) {
    0f
} else {
    (deliveredTodayMl / dailyDoseMl).coerceIn(0.0, 1.0).toFloat()
}

private fun DosingDoseProgressUiState.progressTextColor(colors: AquaDeviceCardColors): Color =
    if (visualState == DosingDoseProgressVisualState.EMPTY) {
        colors.secondaryText
    } else {
        colors.primaryText
    }

private const val QUARTER_DOSE_FRACTION = 0.25
private const val HALF_DOSE_FRACTION = 0.50
private const val THREE_QUARTER_DOSE_FRACTION = 0.75
private const val DOSE_PROGRESS_TRACK_HEIGHT_DP = 16
private const val CURRENT_DOSE_HORIZONTAL_PADDING_DP = 8
private const val DOSE_LABEL_TOP_PADDING_DP = 3
private val DOSE_SCALE_FRACTIONS = listOf(
    0.0,
    QUARTER_DOSE_FRACTION,
    HALF_DOSE_FRACTION,
    THREE_QUARTER_DOSE_FRACTION,
    1.0
)
private val DOSE_PROGRESS_TRACK_HEIGHT = DOSE_PROGRESS_TRACK_HEIGHT_DP.dp
private val CURRENT_DOSE_HORIZONTAL_PADDING = CURRENT_DOSE_HORIZONTAL_PADDING_DP.dp
private val DOSE_LABEL_TOP_PADDING = DOSE_LABEL_TOP_PADDING_DP.dp
