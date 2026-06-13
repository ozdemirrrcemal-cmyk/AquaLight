package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.LightDashboardMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary UI shell for the Light dashboard.
 *
 * The previous firmware/DataStore based light data layer has been removed.
 * This ViewModel intentionally does not read firmware, local light programs,
 * automation settings or cached runtime data. It keeps the screen compile-safe
 * until the new Device API layer and the new Light contract are designed and
 * explicitly connected.
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
                "Device information is missing"
            } else {
                LIGHT_DATA_LAYER_NOT_CONNECTED
            }
        )
    }

    fun refreshNow() {
        _uiState.value = unavailableState(
            reason = if (deviceId <= 0L) {
                "Device information is missing"
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

    companion object {
        const val LIGHT_DATA_LAYER_NOT_CONNECTED =
            "Light data layer is not connected yet"
    }
}
