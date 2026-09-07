package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingStateMessageCard

@Composable
internal fun DeviceCoolingSystemStatusScreen(
    state: DeviceCoolingSystemStatusUiState,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val visuals = CoolingSystemStatusVisuals(
        colors = colors,
        typography = aquaCoolingDashboardTypography(colors)
    )
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            top = AquaCoolingDashboardGeometry.screenTopPadding,
            end = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            bottom = AquaCoolingDashboardGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.cardGap)
    ) {
        coolingSystemStatusItems(state = state, visuals = visuals)
    }
}

private fun LazyListScope.coolingSystemStatusItems(
    state: DeviceCoolingSystemStatusUiState,
    visuals: CoolingSystemStatusVisuals
) {
    val snapshot = state.snapshot
    val telemetry = snapshot?.telemetry
    item(key = "summary") {
        CoolingSystemSummaryCard(state = state, telemetry = telemetry, visuals = visuals)
    }
    if (state.stale) {
        item(key = "stale") {
            CoolingStateMessageCard(
                title = stringResource(R.string.device_cooling_system_status_last_known_title),
                message = stringResource(R.string.device_cooling_system_status_last_known_message)
            )
        }
    }
    if (telemetry == null) {
        item(key = "unavailable") {
            CoolingStateMessageCard(
                title = stringResource(R.string.device_cooling_system_status_unavailable),
                message = stringResource(
                    R.string.device_cooling_system_status_unavailable_description
                )
            )
        }
        return
    }
    item(key = "alarms") {
        CoolingSystemAlarmsCard(telemetry = telemetry, visuals = visuals)
    }
    item(key = "fan") {
        CoolingSystemFanCard(telemetry = telemetry, visuals = visuals)
    }
    item(key = "sensors") {
        CoolingSystemSensorsCard(telemetry = telemetry, visuals = visuals)
    }
    item(key = "operation") {
        CoolingSystemOperationCard(snapshot = snapshot, visuals = visuals)
    }
    item(key = "power") {
        CoolingSystemPowerCard(telemetry = telemetry, visuals = visuals)
    }
}
