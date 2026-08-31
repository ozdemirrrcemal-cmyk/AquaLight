package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingInteractionStyle
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography

@Composable
internal fun DeviceCoolingDashboardScreen(
    state: DeviceCoolingRootUiState,
    onModeSelected: (CoolingControlMode) -> Unit,
    onManualFanPercentChanged: (Int) -> Unit,
    onTemperatureHistoryClick: () -> Unit,
    onAutomaticSettingsClick: () -> Unit,
    onProgramSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    val contentAlpha = if (state.contentEnabled) {
        AquaCoolingInteractionStyle.enabledContentAlpha
    } else {
        AquaCoolingInteractionStyle.disabledContentAlpha
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .semantics {
                if (!state.contentEnabled) disabled()
            },
        contentPadding = PaddingValues(
            start = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            top = AquaCoolingDashboardGeometry.screenTopPadding,
            end = AquaCoolingDashboardGeometry.screenHorizontalPadding,
            bottom = AquaCoolingDashboardGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingDashboardGeometry.cardGap)
    ) {
        item(key = "temperature") {
            CoolingTemperatureCard(
                state = state,
                colors = colors,
                typography = typography,
                enabled = state.contentEnabled,
                onClick = onTemperatureHistoryClick
            )
        }
        item(key = "fan") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    AquaCoolingDashboardGeometry.splitCardGap
                )
            ) {
                CoolingFanSpeedCard(
                    state = state,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                CoolingModeCard(
                    selectedMode = state.selectedMode,
                    enabled = state.contentEnabled,
                    colors = colors,
                    typography = typography,
                    onModeSelected = onModeSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item(key = "power-status") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    AquaCoolingDashboardGeometry.splitCardGap
                )
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
        item(key = "mode-control") {
            CoolingModeControlCard(
                state = state,
                enabled = state.contentEnabled,
                colors = colors,
                typography = typography,
                onManualFanPercentChanged = onManualFanPercentChanged,
                onAutomaticSettingsClick = onAutomaticSettingsClick,
                onProgramSettingsClick = onProgramSettingsClick
            )
        }
    }
}
