package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors

@Composable
internal fun DeviceLightSystemScreen(
    state: DeviceLightSystemUiState,
    actions: DeviceLightSystemActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val visuals = DeviceLightSystemVisuals(
        colors = colors,
        typography = aquaDeviceCardTypography(colors.card)
    )
    if (state.snapshot == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colorResource(R.color.background_color))
                .padding(DeviceLightSystemGeometry.screenHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource(R.string.device_light_system_data_unavailable),
                style = visuals.typography.body.copy(
                    color = visuals.colors.card.secondaryText,
                    textAlign = TextAlign.Center
                )
            )
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = DeviceLightSystemGeometry.screenHorizontalPadding,
                top = DeviceLightSystemGeometry.screenTopPadding,
                end = DeviceLightSystemGeometry.screenHorizontalPadding,
                bottom = DeviceLightSystemGeometry.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(DeviceLightSystemGeometry.sectionGap)
        ) {
            item(key = "status") {
                DeviceLightSystemStatusCard(state, visuals)
            }
            item(key = "mode") {
                DeviceLightFanModeCard(state, actions, visuals)
            }
            item(key = "automatic-range") {
                DeviceLightAutomaticRangeCard(state, actions, visuals)
            }
            item(key = "protection") {
                DeviceLightProtectionCard(state, actions, visuals)
            }
        }
        DeviceLightSystemSaveAction(state, actions, visuals)
    }
}

@Composable
private fun DeviceLightSystemSaveAction(
    state: DeviceLightSystemUiState,
    actions: DeviceLightSystemActions,
    visuals: DeviceLightSystemVisuals
) {
    val alpha = if (state.canSave) 1f else DeviceLightSystemAlpha.disabled
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DeviceLightSystemGeometry.screenHorizontalPadding,
                top = DeviceLightSystemGeometry.actionTopPadding,
                end = DeviceLightSystemGeometry.screenHorizontalPadding,
                bottom = DeviceLightSystemGeometry.screenBottomPadding
            )
            .height(DeviceLightSystemGeometry.actionHeight)
            .clip(DeviceLightSystemGeometry.actionShape)
            .background(visuals.colors.action.copy(alpha = alpha))
            .clickable(
                enabled = state.canSave,
                role = Role.Button,
                onClick = actions.onSaveClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(R.string.device_light_system_save_action),
            style = visuals.typography.title.copy(
                color = visuals.colors.card.primaryText.copy(alpha = alpha),
                textAlign = TextAlign.Center
            )
        )
    }
}
