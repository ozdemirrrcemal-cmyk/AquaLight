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
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry

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
            DeviceLightPlanCard(
                data = state.planCardData(),
                enabled = state.contentEnabled,
                onActionClick = {
                    state.hero.mode.planDestination(state.activeAutomaticProgramId)
                        ?.let(actions.onPlanClick)
                }
            )
        }
        item(key = "light-live-output") {
            DeviceLightLiveOutputCard(channels = state.channels)
        }
        item(key = "light-controls-header") {
            DeviceLightControlsHeader(
                enabled = state.contentEnabled,
                onQuickSetupClick = actions.onQuickSetupClick
            )
        }
        item(key = "light-control-screens") {
            DeviceLightControlScreensCard(
                state = state.controlScreensState(),
                enabled = state.contentEnabled,
                onMenuClick = actions.onMenuClick
            )
        }
        item(key = "light-secondary-screens") {
            DeviceLightSecondaryScreensRow(
                enabled = state.contentEnabled,
                adaptation = state.adaptation,
                onMenuClick = actions.onMenuClick
            )
        }
    }
}

private fun DeviceLightRootUiState.planCardData() = DeviceLightPlanCardData(
    mode = hero.mode,
    plan = plan,
    channels = channels
)

private fun DeviceLightRootUiState.controlScreensState() = DeviceLightControlScreensState(
    mode = hero.mode,
    automaticProgramCount = automaticProgramCount,
    customCurvePointCount = customCurvePointCount
)

internal fun DeviceLightControlMode?.planDestination(
    activeAutomaticProgramId: String?
): DeviceLightPlanDestination? = when (this) {
    DeviceLightControlMode.MANUAL -> DeviceLightPlanDestination.AutomaticPrograms
    DeviceLightControlMode.AUTOMATIC -> activeAutomaticProgramId
        ?.takeIf(String::isNotBlank)
        ?.let { programId -> DeviceLightPlanDestination.AutomaticProgramEditor(programId) }
        ?: DeviceLightPlanDestination.AutomaticPrograms
    DeviceLightControlMode.CUSTOM -> DeviceLightPlanDestination.CustomCurveEditor
    null -> null
}
