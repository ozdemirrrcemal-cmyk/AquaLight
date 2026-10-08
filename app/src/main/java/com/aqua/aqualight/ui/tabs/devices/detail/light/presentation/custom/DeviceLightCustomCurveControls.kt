package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R

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

private const val EDITOR_ACTION_SPACING_DP = 8
private const val LIBRARY_ACTION_SPACING_DP = 8
