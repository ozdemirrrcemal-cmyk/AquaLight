package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R

@Composable
internal fun CurveHeaderActions(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    onDeleteDeviceProgramClick: () -> Unit,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HEADER_ACTION_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CurveHeaderActionButton(
            spec = CurveHeaderActionSpec(
                label = stringResource(R.string.device_light_custom_preview),
                description = stringResource(
                    R.string.device_light_custom_preview_at_time_description,
                    formatTime(state.previewTimeMs)
                ),
                enabled = state.canPreview,
                color = visuals.colors.action,
                leading = CurveHeaderActionLeading.PLAY
            ),
            onClick = actions.onPreviewClick,
            modifier = Modifier.width(PREVIEW_ACTION_WIDTH_DP.dp),
            visuals = visuals
        )
        if (state.deviceProgramInstalled) {
            CurveHeaderActionButton(
                spec = CurveHeaderActionSpec(
                    label = stringResource(
                        R.string.device_light_custom_delete_device_program_confirm
                    ),
                    description = stringResource(
                        R.string.device_light_custom_delete_device_program_title
                    ),
                    enabled = state.canClearDeviceProgram,
                    color = visuals.colors.card.danger,
                    leading = CurveHeaderActionLeading.DELETE
                ),
                onClick = onDeleteDeviceProgramClick,
                modifier = Modifier.width(DELETE_ACTION_WIDTH_DP.dp),
                visuals = visuals
            )
        }
    }
}

@Composable
private fun CurveHeaderActionButton(
    spec: CurveHeaderActionSpec,
    onClick: () -> Unit,
    modifier: Modifier,
    visuals: DeviceLightCustomVisuals
) {
    val alpha = if (spec.enabled) ENABLED_ALPHA else DISABLED_ALPHA
    val foreground = spec.color.copy(alpha = alpha)
    val shape = RoundedCornerShape(HEADER_ACTION_CORNER_DP.dp)
    Row(
        modifier = modifier
            .height(HEADER_ACTION_HEIGHT_DP.dp)
            .clip(shape)
            .border(HEADER_ACTION_BORDER_DP.dp, foreground, shape)
            .clearAndSetSemantics { contentDescription = spec.description }
            .clickable(enabled = spec.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = HEADER_ACTION_HORIZONTAL_PADDING_DP.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderActionLeading(spec.leading, foreground)
        Spacer(Modifier.width(HEADER_ACTION_CONTENT_GAP_DP.dp))
        BasicText(
            text = spec.label,
            style = visuals.typography.caption.copy(color = foreground),
            maxLines = 1
        )
    }
}

@Composable
private fun HeaderActionLeading(
    leading: CurveHeaderActionLeading,
    color: Color
) {
    when (leading) {
        CurveHeaderActionLeading.PLAY -> HeaderPlayGlyph(color)
        CurveHeaderActionLeading.DELETE -> Image(
            painter = painterResource(R.drawable.ic_delete_24),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(HEADER_ACTION_ICON_SIZE_DP.dp)
        )
    }
}

@Composable
private fun HeaderPlayGlyph(color: Color) {
    Canvas(Modifier.size(HEADER_ACTION_ICON_SIZE_DP.dp)) {
        val path = Path().apply {
            moveTo(size.width * PLAY_LEFT_FRACTION, size.height * PLAY_TOP_FRACTION)
            lineTo(size.width * PLAY_RIGHT_FRACTION, size.height * PLAY_CENTER_FRACTION)
            lineTo(size.width * PLAY_LEFT_FRACTION, size.height * PLAY_BOTTOM_FRACTION)
            close()
        }
        drawPath(path, color)
    }
}

@Immutable
private data class CurveHeaderActionSpec(
    val label: String,
    val description: String,
    val enabled: Boolean,
    val color: Color,
    val leading: CurveHeaderActionLeading
)

private enum class CurveHeaderActionLeading {
    PLAY,
    DELETE
}

private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.38f
private const val HEADER_ACTION_HEIGHT_DP = 33
private const val HEADER_ACTION_CORNER_DP = 10
private const val HEADER_ACTION_BORDER_DP = 1
private const val HEADER_ACTION_HORIZONTAL_PADDING_DP = 6
private const val HEADER_ACTION_CONTENT_GAP_DP = 4
private const val HEADER_ACTION_ICON_SIZE_DP = 13
private const val HEADER_ACTION_GAP_DP = 4
private const val PREVIEW_ACTION_WIDTH_DP = 74
private const val DELETE_ACTION_WIDTH_DP = 54
private const val PLAY_LEFT_FRACTION = 0.28f
private const val PLAY_TOP_FRACTION = 0.16f
private const val PLAY_RIGHT_FRACTION = 0.82f
private const val PLAY_CENTER_FRACTION = 0.5f
private const val PLAY_BOTTOM_FRACTION = 0.84f
