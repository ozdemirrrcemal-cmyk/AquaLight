package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)
    val statusLabel = stringResource(state.visualState.labelRes)
    val contentDescriptionText = stringResource(
        R.string.device_dosing_channel_card_content_description,
        state.channelNumber,
        state.displayName,
        statusLabel
    )

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CHANNEL_CARD_MIN_HEIGHT)
            .semantics { contentDescription = contentDescriptionText }
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
                DosingChannelEmptyState(
                    colors = colors,
                    typography = typography
                )
            } else {
                DosingChannelSummary(
                    state = state,
                    colors = colors,
                    typography = typography
                )
                DosingDoseProgressBar(
                    state = state.doseProgress,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

@Composable
private fun DosingChannelHeader(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    statusLabel: String
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
        DosingStatusPill(
            label = statusLabel,
            color = state.visualState.statusColor(colors),
            typography = typography,
            modifier = Modifier.padding(start = AquaDeviceCardGeometry.compactGap)
        )
    }
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
private fun DosingChannelSummary(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val scheduleSummary = state.scheduleDays.summaryLabel()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SUMMARY_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingSummaryItem(
            icon = DosingSummaryIcon.DOSE,
            label = stringResource(
                R.string.device_dosing_channel_daily_dose_format,
                state.doseProgress.dailyDoseMl
            ),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        DosingSummaryItem(
            icon = DosingSummaryIcon.DAYS,
            label = scheduleSummary,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DosingScheduleDaysUiState.summaryLabel(): String = when {
    selectedDays.isEmpty() -> stringResource(R.string.device_dosing_channel_no_days_selected)
    isEveryDay -> stringResource(R.string.device_dosing_channel_every_day)
    else -> selectedDays
        .map { day -> stringResource(day.shortLabelRes) }
        .joinToString(separator = DAY_SEPARATOR)
}

@Composable
private fun DosingSummaryItem(
    icon: DosingSummaryIcon,
    label: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val iconTint = when (icon) {
        DosingSummaryIcon.DOSE -> colors.accent
        DosingSummaryIcon.DAYS -> colors.secondaryText
    }
    val textColor = when (icon) {
        DosingSummaryIcon.DOSE -> colors.primaryText
        DosingSummaryIcon.DAYS -> colors.secondaryText
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SUMMARY_ICON_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingSummaryGlyph(
            icon = icon,
            tint = iconTint,
            modifier = Modifier.size(SUMMARY_ICON_SIZE)
        )
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = typography.caption.copy(color = textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun DosingChannelVisualState.statusColor(colors: AquaDeviceCardColors): Color = when (this) {
    DosingChannelVisualState.NOT_CONFIGURED -> colors.warning
    DosingChannelVisualState.READY,
    DosingChannelVisualState.SCHEDULED,
    DosingChannelVisualState.DOSING -> colors.accent
    DosingChannelVisualState.ERROR -> colors.danger
}

private const val DAY_SEPARATOR = " · "
private const val STATUS_BACKGROUND_ALPHA = 0.10f
private const val STATUS_OUTLINE_ALPHA = 0.38f
private const val EMPTY_STATE_ICON_BACKGROUND_ALPHA = 0.08f
private const val EMPTY_STATE_ICON_OUTLINE_ALPHA = 0.28f
private const val EMPTY_STATE_DESCRIPTION_MAX_LINES = 2
private const val CHANNEL_CARD_MIN_HEIGHT_DP = 104
private const val SUMMARY_GAP_DP = 18
private const val SUMMARY_ICON_GAP_DP = 6
private const val SUMMARY_ICON_SIZE_DP = 16
private const val EMPTY_STATE_VERTICAL_PADDING_DP = 4
private const val EMPTY_STATE_CONTENT_GAP_DP = 12
private const val EMPTY_STATE_TEXT_GAP_DP = 2
private const val EMPTY_STATE_ICON_CONTAINER_SIZE_DP = 44
private const val EMPTY_STATE_ICON_CORNER_RADIUS_DP = 14
private const val EMPTY_STATE_GLYPH_SIZE_DP = 28
private val CHANNEL_CARD_MIN_HEIGHT = CHANNEL_CARD_MIN_HEIGHT_DP.dp
private val SUMMARY_GAP = SUMMARY_GAP_DP.dp
private val SUMMARY_ICON_GAP = SUMMARY_ICON_GAP_DP.dp
private val SUMMARY_ICON_SIZE = SUMMARY_ICON_SIZE_DP.dp
private val EMPTY_STATE_VERTICAL_PADDING = EMPTY_STATE_VERTICAL_PADDING_DP.dp
private val EMPTY_STATE_CONTENT_GAP = EMPTY_STATE_CONTENT_GAP_DP.dp
private val EMPTY_STATE_TEXT_GAP = EMPTY_STATE_TEXT_GAP_DP.dp
private val EMPTY_STATE_ICON_CONTAINER_SIZE = EMPTY_STATE_ICON_CONTAINER_SIZE_DP.dp
private val EMPTY_STATE_ICON_CORNER_RADIUS = EMPTY_STATE_ICON_CORNER_RADIUS_DP.dp
private val EMPTY_STATE_GLYPH_SIZE = EMPTY_STATE_GLYPH_SIZE_DP.dp
