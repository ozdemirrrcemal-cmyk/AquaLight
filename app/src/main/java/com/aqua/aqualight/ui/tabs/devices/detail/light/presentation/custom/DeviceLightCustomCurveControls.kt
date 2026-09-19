package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun VirtualTimePreviewCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewGlyph(visuals)
            BasicText(
                text = stringResource(R.string.device_light_custom_virtual_preview_compact),
                style = visuals.typography.title.copy(color = visuals.colors.card.primaryText),
                modifier = Modifier.weight(1f).padding(start = PREVIEW_TITLE_GAP_DP.dp)
            )
            CustomOutlinedButton(
                button = CustomOutlinedButtonState(
                    label = stringResource(R.string.device_light_custom_preview),
                    description = stringResource(
                        R.string.device_light_custom_preview_at_time_description,
                        formatTime(state.previewTimeMs)
                    ),
                    enabled = state.canPreview
                ),
                appearance = CustomOutlinedButtonAppearance(
                    color = visuals.colors.action,
                    height = PREVIEW_BUTTON_HEIGHT_DP.dp
                ),
                onClick = actions.onPreviewClick,
                modifier = Modifier.width(PREVIEW_BUTTON_WIDTH_DP.dp)
            )
        }
    }
}

@Composable
private fun PreviewGlyph(visuals: DeviceLightCustomVisuals) {
    Canvas(
        modifier = Modifier.size(PREVIEW_GLYPH_SIZE_DP.dp).border(
            PREVIEW_GLYPH_BORDER_DP.dp,
            visuals.colors.action,
            RoundedCornerShape(percent = CIRCLE_SHAPE_PERCENT)
        )
    ) {
        val path = Path().apply {
            moveTo(size.width * PLAY_LEFT_FRACTION, size.height * PLAY_TOP_FRACTION)
            lineTo(size.width * PLAY_RIGHT_FRACTION, size.height * PLAY_CENTER_FRACTION)
            lineTo(size.width * PLAY_LEFT_FRACTION, size.height * PLAY_BOTTOM_FRACTION)
            close()
        }
        drawPath(
            path = path,
            color = visuals.colors.action,
            style = Stroke(width = PLAY_STROKE_DP.dp.toPx())
        )
    }
}

@Composable
internal fun CustomEditorActions(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EDITOR_ACTION_SPACING_DP.dp)
    ) {
        ApplyToDeviceAction(
            state = state,
            actions = actions,
            visuals = visuals
        )
        LibraryActions(
            state = state,
            actions = actions,
            visuals = visuals
        )
    }
}

@Composable
private fun ApplyToDeviceAction(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    val alreadyApplied = state.deviceProgramInstalled && !state.hasUnappliedChanges
    CustomOutlinedButton(
        button = CustomOutlinedButtonState(
            label = stringResource(
                if (alreadyApplied) {
                    R.string.device_light_custom_applied_to_device
                } else {
                    R.string.device_light_custom_apply_to_device
                }
            ),
            description = stringResource(R.string.device_light_custom_apply_to_device_description),
            enabled = state.canApplyToDevice
        ),
        appearance = CustomOutlinedButtonAppearance(
            color = visuals.colors.action,
            filled = true,
            contentColor = visuals.colors.card.primaryText
        ),
        onClick = actions.onApplyToDeviceClick,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LibraryActions(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LIBRARY_ACTION_SPACING_DP.dp)
    ) {
        CustomOutlinedButton(
            button = CustomOutlinedButtonState(
                label = stringResource(R.string.device_light_custom_profiles),
                description = stringResource(R.string.device_light_custom_profiles_description),
                enabled = state.contentEnabled && !state.operationInProgress
            ),
            appearance = CustomOutlinedButtonAppearance(
                color = visuals.colors.action,
                iconRes = R.drawable.ic_light_library
            ),
            onClick = actions.onProfilesClick,
            modifier = Modifier.weight(1f)
        )
        CustomOutlinedButton(
            button = CustomOutlinedButtonState(
                label = stringResource(R.string.device_light_custom_save_as),
                description = stringResource(R.string.device_light_custom_save_as_description),
                enabled = state.canSaveAs
            ),
            appearance = CustomOutlinedButtonAppearance(
                color = visuals.colors.action,
                iconRes = R.drawable.ic_add_24
            ),
            onClick = actions.onSaveAsClick,
            modifier = Modifier.weight(1f)
        )
    }
}

private const val PREVIEW_TITLE_GAP_DP = 12
private const val PREVIEW_BUTTON_HEIGHT_DP = 44
private const val PREVIEW_BUTTON_WIDTH_DP = 126
private const val PREVIEW_GLYPH_SIZE_DP = 42
private const val PREVIEW_GLYPH_BORDER_DP = 2
private const val PLAY_STROKE_DP = 2
private const val PLAY_LEFT_FRACTION = 0.36f
private const val PLAY_TOP_FRACTION = 0.27f
private const val PLAY_RIGHT_FRACTION = 0.72f
private const val PLAY_CENTER_FRACTION = 0.5f
private const val PLAY_BOTTOM_FRACTION = 0.73f
private const val EDITOR_ACTION_SPACING_DP = 8
private const val LIBRARY_ACTION_SPACING_DP = 8
