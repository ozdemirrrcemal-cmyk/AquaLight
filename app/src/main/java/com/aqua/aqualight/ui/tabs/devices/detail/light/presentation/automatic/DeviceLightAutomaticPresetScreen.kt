package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.aquaLightManualColors

@Composable
internal fun DeviceLightAutomaticPresetScreen(
    state: DeviceLightAutomaticPresetUiState,
    actions: DeviceLightAutomaticPresetActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val visuals = DeviceLightAutomaticPresetVisuals(
        colors = colors,
        typography = aquaDeviceCardTypography(colors.card)
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
    ) {
        PresetIntroduction(visuals)
        Spacer(Modifier.height(DeviceLightAutomaticPresetGeometry.sectionGap))
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMN_COUNT),
            modifier = Modifier
                .weight(GRID_WEIGHT)
                .selectableGroup()
                .padding(horizontal = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticPresetGeometry.gridGap),
            verticalArrangement = Arrangement.spacedBy(DeviceLightAutomaticPresetGeometry.gridGap)
        ) {
            items(state.presets, key = { preset -> preset.id.name }) { preset ->
                AutomaticPresetCard(
                    preset = preset,
                    selected = preset.id == state.selectedPresetId,
                    onClick = { actions.onPresetClick(preset.id) },
                    visuals = visuals
                )
            }
        }
        PresetActions(state, actions, visuals)
    }
}

@Composable
private fun PresetIntroduction(visuals: DeviceLightAutomaticPresetVisuals) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding,
                top = DeviceLightAutomaticPresetGeometry.screenTopPadding,
                end = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding
            ),
        contentPadding = DeviceLightAutomaticPresetGeometry.introductionPadding
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(visuals.colors.card.secondaryText),
                modifier = Modifier.size(DeviceLightAutomaticPresetGeometry.introductionIconSize)
            )
            Spacer(Modifier.width(DeviceLightAutomaticPresetGeometry.introductionIconGap))
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    DeviceLightAutomaticPresetGeometry.introductionTextGap
                )
            ) {
                BasicText(
                    text = stringResource(R.string.device_light_auto_preset_intro_title),
                    style = visuals.typography.title
                )
                BasicText(
                    text = stringResource(R.string.device_light_auto_preset_intro_summary),
                    style = visuals.typography.caption
                )
                BasicText(
                    text = stringResource(R.string.device_light_auto_preset_intro_safety),
                    style = visuals.typography.caption.copy(
                        color = visuals.colors.card.secondaryText
                    )
                )
            }
        }
    }
}

@Composable
private fun PresetActions(
    state: DeviceLightAutomaticPresetUiState,
    actions: DeviceLightAutomaticPresetActions,
    visuals: DeviceLightAutomaticPresetVisuals
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding,
                top = DeviceLightAutomaticPresetGeometry.actionTopPadding,
                end = DeviceLightAutomaticPresetGeometry.screenHorizontalPadding,
                bottom = DeviceLightAutomaticPresetGeometry.screenBottomPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticPresetGeometry.actionGap)
    ) {
        PresetActionButton(
            label = stringResource(R.string.cancel),
            filled = false,
            onClick = actions.onCancelClick,
            visuals = visuals,
            modifier = Modifier.weight(ACTION_WEIGHT)
        )
        PresetActionButton(
            label = stringResource(R.string.device_light_auto_preset_use),
            filled = true,
            onClick = { actions.onUseClick(state.selectedPresetId) },
            visuals = visuals,
            modifier = Modifier.weight(ACTION_WEIGHT)
        )
    }
}

@Composable
private fun PresetActionButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightAutomaticPresetVisuals,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .height(DeviceLightAutomaticPresetGeometry.actionHeight)
            .clip(DeviceLightAutomaticPresetGeometry.actionShape)
            .background(if (filled) visuals.colors.action else Color.Transparent)
            .border(
                DeviceLightAutomaticPresetGeometry.actionBorderWidth,
                visuals.colors.action,
                DeviceLightAutomaticPresetGeometry.actionShape
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = visuals.typography.title.copy(
                color = if (filled) {
                    visuals.colors.card.primaryText
                } else {
                    visuals.colors.action
                },
                textAlign = TextAlign.Center
            )
        )
    }
}

private const val GRID_COLUMN_COUNT = 2
private const val GRID_WEIGHT = 1f
private const val ACTION_WEIGHT = 1f
