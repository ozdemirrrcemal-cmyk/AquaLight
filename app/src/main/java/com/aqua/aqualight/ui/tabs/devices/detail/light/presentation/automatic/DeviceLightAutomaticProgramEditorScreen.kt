package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.common.light.aquaLightManualColors

@Composable
internal fun DeviceLightAutomaticProgramEditorScreen(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticProgramEditorActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val visuals = DeviceLightAutomaticEditorVisuals(
        colors = colors,
        typography = aquaDeviceCardTypography(colors.card)
    )
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color)),
        contentPadding = PaddingValues(
            start = DeviceLightAutomaticEditorGeometry.screenPadding,
            top = DeviceLightAutomaticEditorGeometry.screenTopPadding,
            end = DeviceLightAutomaticEditorGeometry.screenPadding,
            bottom = DeviceLightAutomaticEditorGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(
            DeviceLightAutomaticEditorGeometry.sectionGap
        )
    ) {
        item(key = "simulation") {
            DeviceLightAutomaticDaySimulationCard(state, visuals)
        }
        item(key = "days") {
            DeviceLightAutomaticEditorDaysCard(state, actions.days, visuals)
        }
        item(key = "times") {
            DeviceLightAutomaticEditorTimeRow(state, actions.schedule, visuals)
        }
        item(key = "ramp") {
            DeviceLightAutomaticEditorRampCard(state, actions.schedule, visuals)
        }
        item(key = "preset") {
            DeviceLightAutomaticEditorPresetCard(state, actions.onPresetClick, visuals)
        }
        item(key = "channels") {
            DeviceLightAutomaticEditorChannelsCard(state, actions.onChannelChanged, visuals)
        }
        item(key = "chart") {
            DeviceLightAutomaticEditorChartCard(state, visuals)
        }
        item(key = "actions") {
            DeviceLightAutomaticEditorActionRow(state, actions, visuals)
        }
    }
}

@Immutable
internal data class DeviceLightAutomaticEditorVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)
