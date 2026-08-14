package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography

@Composable
internal fun DosingChannelCard(
    state: DosingChannelCardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val statusLabel = state.visualState.labelRes?.let { labelRes -> stringResource(labelRes) }
    val contentDescriptionText = state.cardContentDescription(statusLabel)

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CHANNEL_CARD_MIN_HEIGHT)
            .semantics { contentDescription = contentDescriptionText }
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            DosingChannelHeader(
                state = state,
                colors = colors,
                typography = typography,
                statusLabel = statusLabel
            )
            if (state.visualState == DosingChannelVisualState.NOT_CONFIGURED) {
                DosingChannelEmptyState(colors = colors, typography = typography)
            } else {
                DosingConfiguredChannelContent(
                    state = state,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

@Composable
private fun DosingChannelCardUiState.cardContentDescription(statusLabel: String?): String =
    if (statusLabel != null) {
        stringResource(
            R.string.device_dosing_channel_card_content_description,
            channelNumber,
            displayName,
            statusLabel
        )
    } else {
        stringResource(
            R.string.device_dosing_pump_channel_content_description,
            channelNumber,
            displayName
        )
    }

@Composable
private fun DosingChannelHeader(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    statusLabel: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelMarker(
            channelNumber = state.channelNumber,
            colors = colors,
            typography = typography
        )
        BasicText(
            text = state.displayName,
            modifier = Modifier
                .weight(1f)
                .padding(start = AquaDeviceCardGeometry.compactGap),
            style = typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        statusLabel?.let { label ->
            DosingStatusPill(
                label = label,
                color = state.visualState.statusColor(colors),
                typography = typography,
                modifier = Modifier.padding(start = AquaDeviceCardGeometry.compactGap)
            )
        }
    }
}

private fun DosingChannelVisualState.statusColor(colors: AquaDeviceCardColors): Color = when (this) {
    DosingChannelVisualState.NOT_CONFIGURED -> colors.warning
    DosingChannelVisualState.IDLE -> colors.secondaryText
    DosingChannelVisualState.DOSING -> colors.accent
    DosingChannelVisualState.ERROR -> colors.danger
}

@Composable
private fun ChannelMarker(
    channelNumber: Int,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(AquaDeviceCardGeometry.markerCornerRadius)
    Box(
        modifier = Modifier
            .size(AquaDeviceCardGeometry.markerSize)
            .clip(shape)
            .background(colors.mediaSurface)
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = colors.mediaOutline,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = channelNumber.toString(),
            style = typography.body.copy(color = colors.accent)
        )
    }
}

@Composable
private fun DosingStatusPill(
    label: String,
    color: Color,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AquaDeviceCardGeometry.statusCornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = STATUS_BACKGROUND_ALPHA))
            .border(
                width = AquaDeviceCardGeometry.outlineWidth,
                color = color.copy(alpha = STATUS_OUTLINE_ALPHA),
                shape = shape
            )
            .padding(
                horizontal = AquaDeviceCardGeometry.statusHorizontalPadding,
                vertical = AquaDeviceCardGeometry.statusVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = typography.micro.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingChannelEmptyState(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val iconShape = RoundedCornerShape(EMPTY_STATE_ICON_CORNER_RADIUS)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(EMPTY_STATE_CONTENT_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(EMPTY_STATE_ICON_CONTAINER_SIZE)
                .clip(iconShape)
                .background(colors.mediaSurface)
                .background(colors.accent.copy(alpha = EMPTY_STATE_ICON_BACKGROUND_ALPHA))
                .border(
                    width = AquaDeviceCardGeometry.outlineWidth,
                    color = colors.accent.copy(alpha = EMPTY_STATE_ICON_OUTLINE_ALPHA),
                    shape = iconShape
                ),
            contentAlignment = Alignment.Center
        ) {
            DosingEmptyStateGlyph(
                tint = colors.accent,
                badgeSurface = colors.mediaSurface,
                modifier = Modifier.size(EMPTY_STATE_GLYPH_SIZE)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EMPTY_STATE_TEXT_GAP)
        ) {
            BasicText(
                text = stringResource(R.string.device_dosing_channel_empty_title),
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = stringResource(R.string.device_dosing_channel_empty_description),
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = EMPTY_STATE_DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DosingConfiguredChannelContent(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val hasSummary = state.dailyDoseMl != null ||
        state.programMode != null ||
        state.scheduledProgress != null ||
        state.reservoir != null

    if (hasSummary) {
        DosingChannelMetrics(
            state = state,
            colors = colors,
            typography = typography
        )
    }

    state.scheduledProgress?.let { progress ->
        DosingScheduledProgress(
            progress = progress,
            manualUsage = state.manualUsage,
            colors = colors,
            typography = typography,
            modifier = Modifier.padding(top = PROGRESS_TOP_PADDING)
        )
    }
}

@Composable
private fun DosingChannelMetrics(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(METRIC_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.dailyDoseMl?.let { dailyDose ->
            val days = state.scheduleDays.summaryLabel()
            DosingMetricItem(
                icon = DosingCardMetricIcon.DOSE,
                label = stringResource(R.string.device_dosing_channel_daily_dose_format, dailyDose),
                supportingLabel = days,
                tint = colors.accent,
                colors = colors,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
        }

        state.programMode?.let { mode ->
            val progress = state.scheduledProgress
            val primary = if (progress != null) {
                stringResource(
                    R.string.device_dosing_card_occurrence_count,
                    progress.completedCount,
                    progress.totalCount
                )
            } else {
                mode.label()
            }
            val supporting = if (progress != null) mode.label() else null
            DosingMetricItem(
                icon = DosingCardMetricIcon.SCHEDULE,
                label = primary,
                supportingLabel = supporting,
                tint = colors.secondaryText,
                colors = colors,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
        }

        state.reservoir?.let { reservoir ->
            val tint = if (reservoir.level == DosingReservoirLevelUiState.LOW) {
                colors.danger
            } else {
                colors.secondaryText
            }
            DosingMetricItem(
                icon = DosingCardMetricIcon.RESERVOIR,
                label = reservoir.daysLabel(),
                supportingLabel = stringResource(
                    R.string.device_dosing_card_reservoir_amount,
                    reservoir.remainingMl
                ),
                tint = tint,
                colors = colors,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DosingProgramModeUi.label(): String = stringResource(
    when (this) {
        DosingProgramModeUi.SINGLE -> R.string.device_dosing_card_mode_single
        DosingProgramModeUi.HOURLY_24 -> R.string.device_dosing_card_mode_hourly24
        DosingProgramModeUi.CUSTOM_PERIODS -> R.string.device_dosing_card_mode_custom_periods
        DosingProgramModeUi.TIMER -> R.string.device_dosing_card_mode_timer
    }
)

@Composable
private fun DosingReservoirSummaryUiState.daysLabel(): String = estimatedDaysRemaining?.let { days ->
    stringResource(R.string.device_dosing_card_reservoir_days, days)
} ?: stringResource(R.string.device_dosing_card_reservoir_unknown_days)

@Composable
private fun DosingScheduleDaysUiState.summaryLabel(): String = when {
    selectedDays.isEmpty() -> stringResource(R.string.device_dosing_channel_no_days_selected)
    isEveryDay -> stringResource(R.string.device_dosing_channel_every_day)
    else -> selectedDays
        .map { day -> stringResource(day.shortLabelRes) }
        .joinToString(separator = DAY_SEPARATOR)
}

@Composable
private fun DosingMetricItem(
    icon: DosingCardMetricIcon,
    label: String,
    supportingLabel: String?,
    tint: Color,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(METRIC_ICON_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(METRIC_ICON_CONTAINER_SIZE)
                .clip(RoundedCornerShape(METRIC_ICON_CORNER_RADIUS))
                .background(tint.copy(alpha = METRIC_ICON_BACKGROUND_ALPHA)),
            contentAlignment = Alignment.Center
        ) {
            DosingCardMetricGlyph(
                icon = icon,
                tint = tint,
                modifier = Modifier.size(METRIC_ICON_SIZE)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(METRIC_TEXT_GAP)
        ) {
            BasicText(
                text = label,
                style = typography.caption.copy(color = colors.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            supportingLabel?.let { supporting ->
                BasicText(
                    text = supporting,
                    style = typography.micro.copy(color = tint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val DAY_SEPARATOR = " · "
private const val STATUS_BACKGROUND_ALPHA = 0.10f
private const val STATUS_OUTLINE_ALPHA = 0.38f
private const val EMPTY_STATE_ICON_BACKGROUND_ALPHA = 0.08f
private const val EMPTY_STATE_ICON_OUTLINE_ALPHA = 0.28f
private const val METRIC_ICON_BACKGROUND_ALPHA = 0.09f
private const val EMPTY_STATE_DESCRIPTION_MAX_LINES = 2
private const val CHANNEL_CARD_MIN_HEIGHT_DP = 104
private const val METRIC_GAP_DP = 10
private const val METRIC_ICON_GAP_DP = 6
private const val METRIC_ICON_CONTAINER_SIZE_DP = 24
private const val METRIC_ICON_SIZE_DP = 14
private const val METRIC_ICON_CORNER_RADIUS_DP = 8
private const val METRIC_TEXT_GAP_DP = 1
private const val PROGRESS_TOP_PADDING_DP = 2
private const val EMPTY_STATE_VERTICAL_PADDING_DP = 4
private const val EMPTY_STATE_CONTENT_GAP_DP = 12
private const val EMPTY_STATE_TEXT_GAP_DP = 2
private const val EMPTY_STATE_ICON_CONTAINER_SIZE_DP = 44
private const val EMPTY_STATE_ICON_CORNER_RADIUS_DP = 14
private const val EMPTY_STATE_GLYPH_SIZE_DP = 28
private val CHANNEL_CARD_MIN_HEIGHT = CHANNEL_CARD_MIN_HEIGHT_DP.dp
private val METRIC_GAP = METRIC_GAP_DP.dp
private val METRIC_ICON_GAP = METRIC_ICON_GAP_DP.dp
private val METRIC_ICON_CONTAINER_SIZE = METRIC_ICON_CONTAINER_SIZE_DP.dp
private val METRIC_ICON_SIZE = METRIC_ICON_SIZE_DP.dp
private val METRIC_ICON_CORNER_RADIUS = METRIC_ICON_CORNER_RADIUS_DP.dp
private val METRIC_TEXT_GAP = METRIC_TEXT_GAP_DP.dp
private val PROGRESS_TOP_PADDING = PROGRESS_TOP_PADDING_DP.dp
private val EMPTY_STATE_VERTICAL_PADDING = EMPTY_STATE_VERTICAL_PADDING_DP.dp
private val EMPTY_STATE_CONTENT_GAP = EMPTY_STATE_CONTENT_GAP_DP.dp
private val EMPTY_STATE_TEXT_GAP = EMPTY_STATE_TEXT_GAP_DP.dp
private val EMPTY_STATE_ICON_CONTAINER_SIZE = EMPTY_STATE_ICON_CONTAINER_SIZE_DP.dp
private val EMPTY_STATE_ICON_CORNER_RADIUS = EMPTY_STATE_ICON_CORNER_RADIUS_DP.dp
private val EMPTY_STATE_GLYPH_SIZE = EMPTY_STATE_GLYPH_SIZE_DP.dp
