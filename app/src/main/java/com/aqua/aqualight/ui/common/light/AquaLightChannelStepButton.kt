package com.aqua.aqualight.ui.common.light

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun AquaLightChannelStepButton(
    state: AquaLightChannelStepButtonState,
    colors: AquaLightManualColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (state.enabled) ENABLED_ALPHA else AquaLightManualAlpha.disabledControl
    Box(
        modifier = modifier
            .size(AquaLightManualGeometry.channelStepTouchSize)
            .clearAndSetSemantics { contentDescription = state.contentDescription }
            .clickable(enabled = state.enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(AquaLightManualGeometry.channelStepVisualSize)
                .clip(CircleShape)
                .border(
                    AquaLightManualGeometry.channelStepOutlineWidth,
                    colors.card.mediaOutline.copy(alpha = alpha),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = state.symbol,
                style = typography.title.copy(
                    color = colors.card.primaryText.copy(alpha = alpha)
                )
            )
        }
    }
}

@Immutable
internal data class AquaLightChannelStepButtonState(
    val symbol: String,
    val contentDescription: String,
    val enabled: Boolean
)

private const val ENABLED_ALPHA = 1f
