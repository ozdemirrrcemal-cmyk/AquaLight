package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.light.AquaLightDashboardGeometry

@Composable
internal fun DeviceLightDashboardScreen(
    state: DeviceLightRootUiState,
    actions: DeviceLightDashboardActions,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color)),
        contentPadding = PaddingValues(
            start = AquaLightDashboardGeometry.screenHorizontalPadding,
            top = AquaLightDashboardGeometry.screenTopPadding,
            end = AquaLightDashboardGeometry.screenHorizontalPadding,
            bottom = AquaLightDashboardGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaLightDashboardGeometry.cardGap)
    ) {
        item(key = "light-hero") {
            DeviceLightHero(
                state = state.hero,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item(key = "light-plan") {
            DeviceLightPlanCard()
        }
        item(key = "light-live-output") {
            DeviceLightLiveOutputCard()
        }
        item(key = "light-controls-header") {
            DeviceLightControlsHeader(
                enabled = state.contentEnabled,
                onQuickSetupClick = actions.onQuickSetupClick
            )
        }
        item(key = "light-control-screens") {
            DeviceLightControlScreensCard(
                enabled = state.contentEnabled,
                onMenuClick = actions.onMenuClick
            )
        }
        item(key = "light-secondary-screens") {
            DeviceLightSecondaryScreensRow(
                enabled = state.contentEnabled,
                onMenuClick = actions.onMenuClick
            )
        }
    }
}
