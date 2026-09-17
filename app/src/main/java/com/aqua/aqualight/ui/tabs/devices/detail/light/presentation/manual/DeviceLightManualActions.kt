package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualGeometry

@Composable
internal fun ManualQuickScenesCard(
    state: DeviceLightManualControlUiState,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.quickSceneButtonGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_light_manual_quick_scenes_title),
                style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
            )
            state.presets.chunked(PRESETS_PER_ROW).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        AquaLightManualGeometry.quickSceneButtonGap
                    )
                ) {
                    rowPresets.forEach { preset ->
                        ManualQuickSceneButton(
                            preset = preset,
                            state = state,
                            actions = actions,
                            visuals = visuals,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(PRESETS_PER_ROW - rowPresets.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualQuickSceneButton(
    preset: DeviceLightManualPresetUiState,
    state: DeviceLightManualControlUiState,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals,
    modifier: Modifier = Modifier
) {
    val selected = state.selectedPreset == preset.id
    val label = stringResource(preset.labelRes)
    val sceneDescription = stringResource(
        R.string.device_light_manual_preset_scene_description,
        label,
        preset.scene[DeviceLightManualChannelId.RED] ?: 0,
        preset.scene[DeviceLightManualChannelId.GREEN] ?: 0,
        preset.scene[DeviceLightManualChannelId.BLUE] ?: 0,
        preset.scene[DeviceLightManualChannelId.WHITE] ?: 0
    )
    val shape = RoundedCornerShape(AquaLightManualGeometry.quickSceneButtonCornerRadius)
    val outline = if (selected) visuals.colors.action else visuals.colors.card.mediaOutline
    Box(
        modifier = modifier
            .height(AquaLightManualGeometry.quickSceneButtonHeight)
            .clip(shape)
            .background(
                if (selected) {
                    visuals.colors.action.copy(alpha = AquaLightManualAlpha.buttonPressed)
                } else {
                    Color.Transparent
                }
            )
            .border(AquaLightManualGeometry.actionButtonOutlineWidth, outline, shape)
            .clearAndSetSemantics { contentDescription = sceneDescription }
            .clickable(
                enabled = state.controlsEnabled,
                role = Role.Button,
                onClick = { actions.onPresetClick(preset.id) }
            )
            .padding(horizontal = AquaLightManualGeometry.quickSceneHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = visuals.typography.body.copy(color = visuals.colors.card.primaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ManualLibraryActions(
    enabled: Boolean,
    actions: DeviceLightManualControlActions,
    visuals: DeviceLightManualVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.actionButtonGap)
    ) {
        ManualOutlinedActionButton(
            content = ManualActionButtonContent(
                label = stringResource(R.string.device_light_manual_load),
                description = stringResource(R.string.device_light_manual_load_description),
                iconRes = R.drawable.ic_light_library
            ),
            enabled = enabled,
            visuals = visuals,
            onClick = actions.onLoadClick,
            modifier = Modifier.weight(1f)
        )
        ManualOutlinedActionButton(
            content = ManualActionButtonContent(
                label = stringResource(R.string.device_light_manual_save_as),
                description = stringResource(R.string.device_light_manual_save_as_description),
                iconRes = R.drawable.ic_add_24
            ),
            enabled = enabled,
            visuals = visuals,
            onClick = actions.onSaveAsClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ManualOutlinedActionButton(
    content: ManualActionButtonContent,
    enabled: Boolean,
    visuals: DeviceLightManualVisuals,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else AquaLightManualAlpha.disabledControl
    val shape = RoundedCornerShape(AquaLightManualGeometry.actionButtonCornerRadius)
    Row(
        modifier = modifier
            .height(AquaLightManualGeometry.actionButtonHeight)
            .clip(shape)
            .border(
                AquaLightManualGeometry.actionButtonOutlineWidth,
                visuals.colors.action.copy(alpha = alpha),
                shape
            )
            .clearAndSetSemantics { contentDescription = content.description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AquaLightManualGeometry.actionButtonHorizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(content.iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.action.copy(alpha = alpha)),
            modifier = Modifier.size(AquaLightManualGeometry.actionButtonIconSize)
        )
        BasicText(
            text = content.label,
            style = visuals.typography.body.copy(
                color = visuals.colors.action.copy(alpha = alpha)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = AquaLightManualGeometry.actionButtonContentGap)
        )
    }
}

@Composable
internal fun ManualPowerOffAction(
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightManualVisuals
) {
    val alpha = if (enabled) 1f else AquaLightManualAlpha.disabledControl
    val shape = RoundedCornerShape(AquaDeviceCardGeometry.cornerRadius)
    val accessibilityDescription = stringResource(
        R.string.device_light_manual_power_off_description
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightManualGeometry.powerOffButtonHeight)
            .clip(shape)
            .border(
                AquaLightManualGeometry.actionButtonOutlineWidth,
                visuals.colors.card.danger.copy(alpha = alpha),
                shape
            )
            .clearAndSetSemantics { contentDescription = accessibilityDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_timer_power),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.card.danger.copy(alpha = alpha)),
            modifier = Modifier.size(AquaLightManualGeometry.powerOffIconSize)
        )
        Column(
            modifier = Modifier.padding(start = AquaLightManualGeometry.powerOffContentGap),
            verticalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.powerOffTextGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_light_manual_power_off),
                style = visuals.typography.body.copy(
                    color = visuals.colors.card.danger.copy(alpha = alpha)
                )
            )
            BasicText(
                text = stringResource(R.string.device_light_manual_power_off_summary),
                style = visuals.typography.caption.copy(
                    color = visuals.colors.card.secondaryText.copy(alpha = alpha)
                )
            )
        }
    }
}

private data class ManualActionButtonContent(
    val label: String,
    val description: String,
    @DrawableRes val iconRes: Int
)

private const val PRESETS_PER_ROW = 3
