package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingInteractionStyle
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingStateMessageCard
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCommercialCoolingError
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState

@Composable
internal fun DeviceCoolingDashboardScreen(
    state: DeviceCoolingRootUiState,
    actions: DeviceCoolingDashboardActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val contentAlpha = if (state.contentEnabled) {
        AquaCoolingInteractionStyle.enabledContentAlpha
    } else {
        AquaCoolingInteractionStyle.disabledContentAlpha
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .semantics {
                if (!state.contentEnabled) disabled()
            }
            .padding(
                start = AquaCoolingDashboardGeometry.screenHorizontalPadding,
                top = AquaCoolingDashboardGeometry.screenTopPadding,
                end = AquaCoolingDashboardGeometry.screenHorizontalPadding
            )
    ) {
        CoolingLiveHero(
            state = state,
            colors = colors
        )
        Spacer(modifier = Modifier.height(AquaCoolingDashboardGeometry.cardGap))
        CoolingScrollableDashboardContent(
            state = state,
            actions = actions,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CoolingScrollableDashboardContent(
    state: DeviceCoolingRootUiState,
    actions: DeviceCoolingDashboardActions,
    modifier: Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = AquaCoolingDashboardGeometry.screenBottomPadding),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.cardGap)
    ) {
        val mutation = state.controlMutationState
        if (mutation is CoolingMutationState.OperationError) {
            item(key = "control-error") {
                val copy = mutation.failure.toCommercialCoolingError()
                CoolingStateMessageCard(
                    title = stringResource(copy.titleRes),
                    message = stringResource(copy.messageRes)
                )
            }
        }
        item(key = "temperature") {
            CoolingTemperatureCard(
                state = state,
                colors = colors,
                typography = typography,
                enabled = state.contentEnabled,
                onClick = actions.onTemperatureHistoryClick
            )
        }
        item(key = "fan") {
            CoolingFanAndModeRow(state = state, actions = actions)
        }
        item(key = "power-status") {
            CoolingPowerAndStatusRow(state = state)
        }
        if (state.activeAlarmCodes.isNotEmpty()) {
            item(key = "alarm-messages") {
                CoolingAlarmMessagesCard(state)
            }
        }
        item(key = "mode-settings") {
            CoolingModeSettingsCard(
                state = state,
                enabled = state.contentEnabled && state.controlAvailable,
                colors = colors,
                typography = typography,
                actions = actions
            )
        }
    }
}

@Composable
private fun CoolingFanAndModeRow(
    state: DeviceCoolingRootUiState,
    actions: DeviceCoolingDashboardActions
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.splitCardGap)
    ) {
        CoolingFanSpeedCard(
            state = state,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        CoolingModeCard(
            state = state,
            enabled = state.contentEnabled && state.controlAvailable && state.modeSelectionWritable,
            colors = colors,
            typography = typography,
            onModeSelected = actions.onModeSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CoolingPowerAndStatusRow(state: DeviceCoolingRootUiState) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.splitCardGap)
    ) {
        CoolingPowerCard(
            state = state,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        CoolingStatusCard(
            state = state,
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}
