package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingChannelHeader(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    stateLabel: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingChannelMarker(
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
        val visualState = state.visualState
        if (visualState?.showsStatusPill == true && stateLabel != null) {
            DosingStatusPill(
                label = stateLabel,
                color = visualState.statusColor(colors),
                typography = typography,
                modifier = Modifier.padding(start = AquaDeviceCardGeometry.compactGap)
            )
        }
    }
}

@Composable
private fun DosingChannelMarker(
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

private fun DosingChannelVisualState.statusColor(colors: AquaDeviceCardColors): Color = when (this) {
    DosingChannelVisualState.NOT_CONFIGURED,
    DosingChannelVisualState.PROGRAM_NOT_CONFIGURED,
    DosingChannelVisualState.AUTOMATIC_DOSING_OFF -> colors.warning
    DosingChannelVisualState.CONFIGURED,
    DosingChannelVisualState.DOSING -> colors.accent
    DosingChannelVisualState.RTC_ATTENTION,
    DosingChannelVisualState.ERROR -> colors.danger
}

private const val STATUS_BACKGROUND_ALPHA = 0.10f
private const val STATUS_OUTLINE_ALPHA = 0.38f
