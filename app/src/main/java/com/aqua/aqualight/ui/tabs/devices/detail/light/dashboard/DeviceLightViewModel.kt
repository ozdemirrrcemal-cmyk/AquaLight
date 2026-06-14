package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.LightDashboardMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary UI shell for the Light dashboard.
 *
 * No Light data source is connected in this layer yet. The dashboard stays
 * compile-safe and exposes only an unavailable UI state until the new Light
 * contract is designed and connected intentionally.
 */
class DeviceLightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        unavailableState()
    )

    val uiState: StateFlow<DeviceLightDashboardUiState> =
        _uiState.asStateFlow()

    private var deviceId: Long = 0L

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        _uiState.value = unavailableState(
            reason = if (deviceId <= 0L) {
                LIGHT_DEVICE_INFORMATION_MISSING
            } else {
                LIGHT_DATA_LAYER_NOT_CONNECTED
            }
        )
    }

    fun refreshNow() {
        _uiState.value = unavailableState(
            reason = if (deviceId <= 0L) {
                LIGHT_DEVICE_INFORMATION_MISSING
            } else {
                LIGHT_DATA_LAYER_NOT_CONNECTED
            }
        )
    }

    private fun unavailableState(
        reason: String = LIGHT_DATA_LAYER_NOT_CONNECTED
    ): DeviceLightDashboardUiState {
        return DeviceLightDashboardUiState(
            activeProgramName = "Light data not connected",
            runStatus = reason,
            liveMode = LightDashboardMode.IDLE,
            currentWattText = "-- W",
            outputPercentText = "--%",
            redChannelText = "R --",
            greenChannelText = "G --",
            blueChannelText = "B --",
            whiteChannelText = "W --",
            deviceTimeText = "--:--",
            nextEventText = "No runtime source",
            healthTemperatureText = "-- °C",
            healthTemperatureStatusText = "Unavailable",
            healthFanText = "Unavailable",
            healthFanStatusText = "Unavailable",
            timelineStatusText = "No light data source connected",
            isDeviceOnline = false,
            controlsEnabled = false,
            connectionStatusText = reason
        )
    }
}
