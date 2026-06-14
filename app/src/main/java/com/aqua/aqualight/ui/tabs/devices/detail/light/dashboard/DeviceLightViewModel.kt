package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.LightDashboardMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline.LightDashboardTimelineMapper
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
        val app = getApplication<Application>()
        val timeline = LightDashboardTimelineMapper.noData(
            statusText = app.getString(R.string.light_dashboard_timeline_not_connected),
            nextEventText = app.getString(R.string.light_dashboard_no_runtime_source),
            emptyMessage = app.getString(R.string.light_dashboard_timeline_not_connected)
        )

        return DeviceLightDashboardUiState(
            activeProgramName = app.getString(R.string.light_dashboard_status_title),
            runStatus = app.getString(R.string.light_dashboard_status_subtitle),
            liveMode = LightDashboardMode.IDLE,
            currentWattText = app.getString(R.string.light_dashboard_power_empty),
            outputPercentText = app.getString(R.string.light_dashboard_output_empty),
            redChannelText = app.getString(R.string.light_dashboard_channel_red_empty),
            greenChannelText = app.getString(R.string.light_dashboard_channel_green_empty),
            blueChannelText = app.getString(R.string.light_dashboard_channel_blue_empty),
            whiteChannelText = app.getString(R.string.light_dashboard_channel_white_empty),
            deviceTimeText = app.getString(R.string.light_dashboard_time_empty),
            nextEventText = timeline.nextEventText,
            healthTemperatureText = app.getString(R.string.light_dashboard_temperature_empty),
            healthTemperatureStatusText = app.getString(R.string.light_dashboard_unavailable),
            healthFanText = app.getString(R.string.light_dashboard_unavailable),
            healthFanStatusText = app.getString(R.string.light_dashboard_unavailable),
            timelineStatusText = timeline.statusText,
            todayPlanGraphState = timeline.graphState,
            isDeviceOnline = false,
            controlsEnabled = false,
            connectionStatusText = reason
        )
    }
}
