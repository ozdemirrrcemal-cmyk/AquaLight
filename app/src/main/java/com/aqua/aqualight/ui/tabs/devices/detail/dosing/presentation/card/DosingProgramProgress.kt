package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingProgramProgress(
    state: DosingProgramProgressUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val mode = state.mode ?: return
    val amountLabel = stringResource(
        R.string.device_dosing_channel_progress_amount_format,
        state.scheduledDeliveredTodayMl,
        state.dailyDoseMl
    )
    val description = stringResource(
        R.string.device_dosing_channel_progress_description,
        stringResource(mode.labelRes),
        state.completedOccurrences,
        state.totalOccurrences,
        state.scheduledDeliveredTodayMl,
        state.dailyDoseMl
    )
    val palette = dosingProgressPalette(colors, state.visualState)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(PROGRESS_TO_MANUAL_GAP),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PROGRESS_HEADER_GAP)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = stringResource(R.string.device_dosing_channel_progress_today),
                    modifier = Modifier.weight(1f),
                    style = typography.micro.copy(color = colors.secondaryText),
                    maxLines = 1
                )
                BasicText(
                    text = amountLabel,
                    style = typography.micro.copy(color = palette.valueText),
                    maxLines = 1
                )
            }
            DosingModeProgressGraphic(
                state = state,
                palette = palette,
                typography = typography
            )
        }
        if (state.manualDeliveredTodayMl > 0.0) {
            DosingManualDosePill(
                amountMl = state.manualDeliveredTodayMl,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun DosingModeProgressGraphic(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography
) {
    if (state.occurrences.isEmpty()) {
        DosingNoDoseTodayProgress(palette, typography)
        return
    }
    when (state.mode) {
        DosingProgramModeUiState.SINGLE -> DosingSingleProgramProgress(state, palette)
        DosingProgramModeUiState.HOURLY_24 -> DosingHourlyProgramProgress(state, palette)
        DosingProgramModeUiState.CUSTOM_PERIODS -> DosingCustomProgramProgress(state, palette)
        DosingProgramModeUiState.TIMER -> DosingTimerProgramProgress(state, palette)
        null -> Unit
    }
}

@Composable
private fun DosingNoDoseTodayProgress(
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(PROGRESS_CORNER_RADIUS)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_GRAPHIC_HEIGHT)
            .clip(shape)
            .background(palette.track)
            .border(width = PROGRESS_OUTLINE_WIDTH, color = palette.outline, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(R.string.device_dosing_channel_no_scheduled_dose_today),
            style = typography.micro.copy(color = palette.valueText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingManualDosePill(
    amountMl: Double,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(MANUAL_PILL_CORNER_RADIUS)
    Column(
        modifier = Modifier
            .widthIn(min = MANUAL_PILL_MIN_WIDTH, max = MANUAL_PILL_MAX_WIDTH)
            .height(MANUAL_PILL_HEIGHT)
            .clip(shape)
            .background(colors.accent.copy(alpha = MANUAL_BACKGROUND_ALPHA))
            .border(
                width = PROGRESS_OUTLINE_WIDTH,
                color = colors.accent.copy(alpha = MANUAL_OUTLINE_ALPHA),
                shape = shape
            )
            .padding(horizontal = MANUAL_HORIZONTAL_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MANUAL_ICON_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DosingManualDoseGlyph(
                tint = colors.accent,
                modifier = Modifier.size(MANUAL_ICON_SIZE)
            )
            BasicText(
                text = stringResource(R.string.device_dosing_channel_manual_label),
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1
            )
        }
        BasicText(
            text = stringResource(R.string.device_dosing_channel_manual_amount_format, amountMl),
            modifier = Modifier.fillMaxWidth(),
            style = typography.micro.copy(
                color = colors.accent,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

@Immutable
internal data class DosingProgressPalette(
    val track: Color,
    val outline: Color,
    val completed: Color,
    val active: Color,
    val pending: Color,
    val skipped: Color,
    val uncertain: Color,
    val valueText: Color
)

private fun dosingProgressPalette(
    colors: AquaDeviceCardColors,
    visualState: DosingDoseProgressVisualState
): DosingProgressPalette {
    val disabled = visualState == DosingDoseProgressVisualState.DISABLED
    val error = visualState == DosingDoseProgressVisualState.ERROR
    return DosingProgressPalette(
        track = colors.mediaSurface,
        outline = if (error) colors.danger.copy(alpha = ERROR_OUTLINE_ALPHA) else colors.mediaOutline,
        completed = when {
            error -> colors.danger
            disabled -> colors.secondaryText.copy(alpha = DISABLED_ALPHA)
            else -> colors.accent.copy(alpha = COMPLETED_ALPHA)
        },
        active = if (error) colors.danger else colors.accent,
        pending = colors.secondaryText.copy(
            alpha = if (disabled) DISABLED_PENDING_ALPHA else PENDING_ALPHA
        ),
        skipped = colors.warning,
        uncertain = colors.danger,
        valueText = if (disabled) colors.secondaryText else colors.primaryText
    )
}

private const val MANUAL_BACKGROUND_ALPHA = 0.12f
private const val MANUAL_OUTLINE_ALPHA = 0.40f
private const val ERROR_OUTLINE_ALPHA = 0.58f
private const val DISABLED_ALPHA = 0.48f
private const val DISABLED_PENDING_ALPHA = 0.24f
private const val PENDING_ALPHA = 0.36f
private const val COMPLETED_ALPHA = 0.76f
internal val PROGRESS_GRAPHIC_HEIGHT = 24.dp
internal val PROGRESS_CORNER_RADIUS = 8.dp
internal val PROGRESS_OUTLINE_WIDTH = 1.dp
private val PROGRESS_HEADER_GAP = 3.dp
private val PROGRESS_TO_MANUAL_GAP = 8.dp
private val MANUAL_PILL_MIN_WIDTH = 78.dp
private val MANUAL_PILL_MAX_WIDTH = 92.dp
private val MANUAL_PILL_HEIGHT = 39.dp
private val MANUAL_PILL_CORNER_RADIUS = 12.dp
private val MANUAL_HORIZONTAL_PADDING = 7.dp
private val MANUAL_ICON_GAP = 3.dp
private val MANUAL_ICON_SIZE = 11.dp
