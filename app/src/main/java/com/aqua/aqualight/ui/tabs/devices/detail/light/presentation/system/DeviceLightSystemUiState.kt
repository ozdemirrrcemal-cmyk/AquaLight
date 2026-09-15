package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal data class DeviceLightSystemUiState(
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val snapshot: DeviceLightSystemSnapshot? = null,
    val selectedMode: DeviceLightFanMode = DeviceLightFanMode.AUTOMATIC,
    val selectedStartTemperatureCelsius: Int = DEFAULT_START_TEMPERATURE,
    val selectedFullSpeedTemperatureCelsius: Int = DEFAULT_FULL_SPEED_TEMPERATURE,
    val selectedProtectionThresholdCelsius: Int = DEFAULT_PROTECTION_THRESHOLD,
    val contentEnabled: Boolean = false,
    val initialLoading: Boolean = false,
    val operationInProgress: Boolean = false
) {
    val controlsEnabled: Boolean
        get() = contentEnabled && !operationInProgress

    val canSave: Boolean
        get() = controlsEnabled && snapshot != null
}

internal data class DeviceLightSystemActions(
    val onModeChanged: (DeviceLightFanMode) -> Unit,
    val onStartTemperatureChanged: (Int) -> Unit,
    val onFullSpeedTemperatureChanged: (Int) -> Unit,
    val onProtectionThresholdChanged: (Int) -> Unit,
    val onSaveClick: () -> Unit
)

private const val DEFAULT_START_TEMPERATURE = 30
private const val DEFAULT_FULL_SPEED_TEMPERATURE = 50
private const val DEFAULT_PROTECTION_THRESHOLD = 60
