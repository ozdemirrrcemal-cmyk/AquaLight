package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingAutomaticSummaryPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingControlPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingDashboardOverviewPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingHealthState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingHistoryOverviewPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootUiState

@Preview(
    name = "Cooling dashboard",
    widthDp = 360,
    heightDp = 650,
    showBackground = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
internal fun CoolingDashboardPreview() {
    DeviceCoolingDashboardScreen(
        state = DeviceCoolingRootUiState(
            contentEnabled = true,
            connectionVisualState = DeviceConnectionVisualState.ONLINE,
            controlState = CoolingDataState.Content(
                value = CoolingControlPresentation(
                    selectedMode = CoolingControlMode.MANUAL,
                    supportedModes = CoolingControlMode.entries.toSet(),
                    modeSelectionWritable = true,
                    manualFanCapabilities = DeviceCoolingManualFanCapabilities(
                        minimumPercent = 0,
                        maximumPercent = 100,
                        stepPercent = 1,
                        writable = true
                    ),
                    manualFanPercent = 65,
                    actualFanPercent = 65,
                    tankTemperatureC = 26.2,
                    operatingState = DeviceCoolingOperatingState.MANUAL
                )
            ),
            automaticSummaryState = CoolingDataState.Content(
                value = CoolingAutomaticSummaryPresentation(
                    startTemperatureC = 25.5,
                    maximumSpeedTemperatureC = 27.0
                )
            ),
            historyState = CoolingDataState.Content(
                value = CoolingHistoryOverviewPresentation(
                    temperaturesC = PREVIEW_TANK_TEMPERATURES
                )
            ),
            dashboardOverviewState = CoolingDataState.Content(
                value = CoolingDashboardOverviewPresentation(
                    roomTemperatureC = 24.1,
                    humidityPercent = 56.0,
                    powerWatts = 0.325,
                    estimatedKwhPerDay = 0.0078,
                    programSlotCount = 3,
                    fanHealth = CoolingHealthState.READY,
                    sensorHealth = CoolingHealthState.READY,
                    activeAlarmCount = 0
                )
            )
        ),
        actions = DeviceCoolingDashboardActions(
            onModeSelected = {},
            onTemperatureHistoryClick = {},
            onAutomaticSettingsClick = {},
            onManualSettingsClick = {},
            onProgramSettingsClick = {}
        ),
        modifier = Modifier.background(colorResource(R.color.background_color))
    )
}

private val PREVIEW_TANK_TEMPERATURES = listOf(
    25.8,
    26.4,
    26.7,
    27.1,
    27.0,
    27.3,
    26.9,
    27.1,
    26.6,
    26.2,
    26.0,
    26.4,
    26.7,
    26.5,
    26.3,
    26.6
)
