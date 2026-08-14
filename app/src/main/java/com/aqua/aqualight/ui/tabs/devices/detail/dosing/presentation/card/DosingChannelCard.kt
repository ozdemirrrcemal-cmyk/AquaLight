package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

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
    val stateLabel = stringResource(state.visualState.labelRes)
    val cardDescription = stringResource(
        R.string.device_dosing_channel_card_content_description,
        state.channelNumber,
        state.displayName,
        stateLabel
    )

    AquaDeviceCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CHANNEL_CARD_MIN_HEIGHT)
            .semantics { contentDescription = cardDescription }
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
                stateLabel = stateLabel
            )
            when (state.visualState) {
                DosingChannelVisualState.NOT_CONFIGURED -> DosingChannelEmptyState(
                    title = stringResource(R.string.device_dosing_channel_calibration_required),
                    description = stringResource(
                        R.string.device_dosing_channel_calibration_required_description
                    ),
                    colors = colors,
                    typography = typography
                )
                DosingChannelVisualState.PROGRAM_NOT_CONFIGURED -> DosingChannelEmptyState(
                    title = stringResource(R.string.device_dosing_channel_program_empty_title),
                    description = stringResource(
                        R.string.device_dosing_channel_program_empty_description
                    ),
                    colors = colors,
                    typography = typography
                )
                else -> {
                    DosingChannelSummary(
                        state = state,
                        colors = colors,
                        typography = typography
                    )
                    DosingProgramProgress(
                        state = state.programProgress,
                        colors = colors,
                        typography = typography
                    )
                }
            }
        }
    }
}

@Composable
private fun DosingChannelEmptyState(
    title: String,
    description: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(EMPTY_ICON_CORNER_RADIUS)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EMPTY_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(EMPTY_CONTENT_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(EMPTY_ICON_SIZE)
                .clip(shape)
                .background(colors.accent.copy(alpha = EMPTY_BACKGROUND_ALPHA))
                .border(
                    width = AquaDeviceCardGeometry.outlineWidth,
                    color = colors.accent.copy(alpha = EMPTY_OUTLINE_ALPHA),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            DosingEmptyStateGlyph(
                tint = colors.accent,
                badgeSurface = colors.mediaSurface,
                modifier = Modifier.size(EMPTY_GLYPH_SIZE)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EMPTY_TEXT_GAP)
        ) {
            BasicText(
                text = title,
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = description,
                style = typography.caption.copy(color = colors.secondaryText),
                maxLines = EMPTY_DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val EMPTY_BACKGROUND_ALPHA = 0.10f
private const val EMPTY_OUTLINE_ALPHA = 0.34f
private const val EMPTY_DESCRIPTION_MAX_LINES = 2
private val CHANNEL_CARD_MIN_HEIGHT = 104.dp
private val EMPTY_VERTICAL_PADDING = 4.dp
private val EMPTY_CONTENT_GAP = 12.dp
private val EMPTY_TEXT_GAP = 2.dp
private val EMPTY_ICON_SIZE = 44.dp
private val EMPTY_ICON_CORNER_RADIUS = 14.dp
private val EMPTY_GLYPH_SIZE = 28.dp
