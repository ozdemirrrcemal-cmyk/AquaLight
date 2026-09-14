package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R

@Composable
internal fun DeviceLightAutomaticEditorActionRow(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticProgramEditorActions,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticEditorGeometry.actionGap)
    ) {
        EditorActionButton(
            spec = EditorActionSpec(
                label = stringResource(R.string.cancel),
                enabled = !state.operationInProgress,
                filled = false,
                onClick = actions.onCancelClick
            ),
            modifier = Modifier.weight(ACTION_WEIGHT),
            visuals = visuals
        )
        EditorActionButton(
            spec = EditorActionSpec(
                label = stringResource(R.string.device_light_auto_editor_save),
                enabled = state.canSave,
                filled = true,
                onClick = actions.onSaveClick
            ),
            modifier = Modifier.weight(ACTION_WEIGHT),
            visuals = visuals
        )
    }
}

@Composable
private fun EditorActionButton(
    spec: EditorActionSpec,
    modifier: Modifier,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val color = visuals.colors.action
    val alpha = if (spec.enabled) ENABLED_ALPHA else DeviceLightAutomaticEditorAlpha.disabled
    Box(
        modifier = modifier
            .height(DeviceLightAutomaticEditorGeometry.actionHeight)
            .clip(DeviceLightAutomaticEditorGeometry.actionShape)
            .background(if (spec.filled) color.copy(alpha = alpha) else Color.Transparent)
            .border(
                DeviceLightAutomaticEditorGeometry.actionBorderWidth,
                color.copy(alpha = alpha),
                DeviceLightAutomaticEditorGeometry.actionShape
            )
            .clickable(enabled = spec.enabled, role = Role.Button, onClick = spec.onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = spec.label,
            style = visuals.typography.title.copy(
                color = if (spec.filled) {
                    visuals.colors.card.primaryText.copy(alpha = alpha)
                } else {
                    color.copy(alpha = alpha)
                },
                textAlign = TextAlign.Center
            )
        )
    }
}

private data class EditorActionSpec(
    val label: String,
    val enabled: Boolean,
    val filled: Boolean,
    val onClick: () -> Unit
)

private const val ACTION_WEIGHT = 1f
private const val ENABLED_ALPHA = 1f
