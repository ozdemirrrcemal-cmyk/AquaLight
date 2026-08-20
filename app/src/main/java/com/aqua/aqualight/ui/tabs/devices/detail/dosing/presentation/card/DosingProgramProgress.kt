package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val mode = state.mode
    if (mode == null) {
        if (state.manualDeliveredTodayMl > 0.0) {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DosingManualDosePill(
                    amountMl = state.manualDeliveredTodayMl,
                    colors = colors,
                    typography = typography
                )
            }
        }
        return
    }
    val description = pluralStringResource(
        R.plurals.device_dosing_channel_progress_description,
        state.totalOccurrences,
        stringResource(mode.labelRes),
        state.completedOccurrences,
        state.totalOccurrences,
        state.scheduledDeliveredTodayMl,
        state.scheduledAmountTodayMl
    )
    val palette = dosingProgressPalette(colors, state.visualState)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(PROGRESS_TO_MANUAL_GAP),
        verticalAlignment = Alignment.Top
    ) {
        DosingModeProgressGraphic(
            state = state,
            palette = palette,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        if (state.manualDeliveredTodayMl > 0.0) {
            Box(modifier = Modifier.padding(top = PROGRESS_VALUE_TAG_AREA_HEIGHT)) {
                DosingManualDosePill(
                    amountMl = state.manualDeliveredTodayMl,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

@Composable
private fun DosingModeProgressGraphic(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    if (!state.scheduledToday || state.occurrences.isEmpty()) {
        DosingEmptyProgramProgress(
            disabled = state.visualState == DosingDoseProgressVisualState.DISABLED,
            palette = palette,
            typography = typography,
            modifier = modifier
        )
        return
    }
    when (state.mode) {
        DosingProgramModeUiState.SINGLE ->
            DosingSingleProgramProgress(state, palette, typography, modifier)
        DosingProgramModeUiState.HOURLY_24 ->
            DosingHourlyProgramProgress(state, palette, typography, modifier)
        DosingProgramModeUiState.CUSTOM_PERIODS ->
            DosingCustomProgramProgress(state, palette, typography, modifier)
        DosingProgramModeUiState.TIMER ->
            DosingTimerProgramProgress(state, palette, typography, modifier)
        null -> Unit
    }
}

@Composable
private fun DosingEmptyProgramProgress(
    disabled: Boolean,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(PROGRESS_CORNER_RADIUS)
    val emptyLabel = if (disabled) {
        null
    } else {
        stringResource(R.string.device_dosing_channel_no_scheduled_dose_today)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(PROGRESS_VALUE_TAG_AREA_HEIGHT))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_RAIL_HEIGHT)
                .clip(shape)
                .background(palette.track)
                .border(width = PROGRESS_OUTLINE_WIDTH, color = palette.outline, shape = shape),
            contentAlignment = Alignment.Center
        ) {
            emptyLabel?.let { label ->
                BasicText(
                    text = label,
                    style = typography.micro.copy(color = palette.valueText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DosingManualDosePill(
    amountMl: Double,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(MANUAL_PILL_CORNER_RADIUS)
    val description = stringResource(
        R.string.device_dosing_channel_manual_description,
        amountMl
    )
    Box(
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
            .padding(horizontal = MANUAL_HORIZONTAL_PADDING)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(R.string.device_dosing_channel_manual_amount_format, amountMl),
            style = typography.micro.copy(color = colors.accent),
            maxLines = 1,
            overflow = TextOverflow.Clip
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
    val valueText: Color,
    val tagSurface: Color,
    val tagOutline: Color
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
        valueText = if (disabled) colors.secondaryText else colors.primaryText,
        tagSurface = colors.surface,
        tagOutline = if (error) colors.danger else colors.mediaOutline
    )
}

private const val MANUAL_BACKGROUND_ALPHA = 0.04f
private const val MANUAL_OUTLINE_ALPHA = 0.62f
private const val ERROR_OUTLINE_ALPHA = 0.58f
private const val DISABLED_ALPHA = 0.48f
private const val DISABLED_PENDING_ALPHA = 0.24f
private const val PENDING_ALPHA = 0.36f
private const val COMPLETED_ALPHA = 0.76f
internal val PROGRESS_RAIL_HEIGHT = 16.dp
internal val PROGRESS_CORNER_RADIUS = 8.dp
internal val PROGRESS_OUTLINE_WIDTH = 1.dp
internal val PROGRESS_VALUE_TAG_AREA_HEIGHT = 20.dp
private val PROGRESS_TO_MANUAL_GAP = 8.dp
private val MANUAL_PILL_MIN_WIDTH = 78.dp
private val MANUAL_PILL_MAX_WIDTH = 92.dp
private val MANUAL_PILL_HEIGHT = PROGRESS_RAIL_HEIGHT
private val MANUAL_PILL_CORNER_RADIUS = PROGRESS_CORNER_RADIUS
private val MANUAL_HORIZONTAL_PADDING = 7.dp
