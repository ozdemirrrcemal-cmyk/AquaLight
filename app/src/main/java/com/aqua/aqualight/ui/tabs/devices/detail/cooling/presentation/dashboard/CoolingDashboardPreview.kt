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
                    temperaturesC = previewTankTemperatures
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

private val previewTankTemperatures = listOf(
    PREVIEW_TEMPERATURE_01_C,
    PREVIEW_TEMPERATURE_02_C,
    PREVIEW_TEMPERATURE_03_C,
    PREVIEW_TEMPERATURE_04_C,
    PREVIEW_TEMPERATURE_05_C,
    PREVIEW_TEMPERATURE_06_C,
    PREVIEW_TEMPERATURE_07_C,
    PREVIEW_TEMPERATURE_08_C,
    PREVIEW_TEMPERATURE_09_C,
    PREVIEW_TEMPERATURE_10_C,
    PREVIEW_TEMPERATURE_11_C,
    PREVIEW_TEMPERATURE_12_C,
    PREVIEW_TEMPERATURE_13_C,
    PREVIEW_TEMPERATURE_14_C,
    PREVIEW_TEMPERATURE_15_C,
    PREVIEW_TEMPERATURE_16_C
)

private const val PREVIEW_TEMPERATURE_01_C = 25.8
private const val PREVIEW_TEMPERATURE_02_C = 26.4
private const val PREVIEW_TEMPERATURE_03_C = 26.7
private const val PREVIEW_TEMPERATURE_04_C = 27.1
private const val PREVIEW_TEMPERATURE_05_C = 27.0
private const val PREVIEW_TEMPERATURE_06_C = 27.3
private const val PREVIEW_TEMPERATURE_07_C = 26.9
private const val PREVIEW_TEMPERATURE_08_C = 27.1
private const val PREVIEW_TEMPERATURE_09_C = 26.6
private const val PREVIEW_TEMPERATURE_10_C = 26.2
private const val PREVIEW_TEMPERATURE_11_C = 26.0
private const val PREVIEW_TEMPERATURE_12_C = 26.4
private const val PREVIEW_TEMPERATURE_13_C = 26.7
private const val PREVIEW_TEMPERATURE_14_C = 26.5
private const val PREVIEW_TEMPERATURE_15_C = 26.3
private const val PREVIEW_TEMPERATURE_16_C = 26.6
