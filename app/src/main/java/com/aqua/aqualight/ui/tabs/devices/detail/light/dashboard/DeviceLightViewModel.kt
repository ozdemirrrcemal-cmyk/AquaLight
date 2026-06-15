package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSession
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeState
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.LightDashboardMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline.LightDashboardTimelineMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceLightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val consumerKey = "light_dashboard_${System.identityHashCode(this)}"

    private val runtimeRepository = LightRuntimeRepository.get(
        context = application.applicationContext
    )

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        unavailableState(
            reason = getApplication<Application>().getString(
                R.string.light_dashboard_timeline_not_connected
            )
        )
    )

    val uiState: kotlinx.coroutines.flow.StateFlow<DeviceLightDashboardUiState> =
        _uiState

    private var deviceId: Long = 0L
    private var runtimeSession: LightRuntimeSession? = null
    private var runtimeCollectorJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        if (this.deviceId == deviceId && runtimeSession != null) {
            return
        }

        runtimeCollectorJob?.cancel()
        runtimeSession?.release(consumerKey)
        runtimeSession = null
        this.deviceId = deviceId

        if (deviceId <= 0L) {
            _uiState.value = unavailableState(
                reason = LIGHT_DEVICE_INFORMATION_MISSING
            )
            return
        }

        val session = runtimeRepository.session(deviceId)
        runtimeSession = session
        runtimeCollectorJob = viewModelScope.launch {
            session.state.collectLatest { runtimeState ->
                renderRuntimeState(runtimeState)
            }
        }
    }

    fun onDashboardVisible() {
        if (deviceId <= 0L) {
            _uiState.value = unavailableState(
                reason = LIGHT_DEVICE_INFORMATION_MISSING
            )
            return
        }

        runtimeSession?.acquire(consumerKey)
    }

    fun onDashboardHidden() {
        runtimeSession?.release(consumerKey)
    }

    fun refreshNow() {
        runtimeSession?.refreshAsync()
    }

    private fun renderRuntimeState(
        runtimeState: LightRuntimeState
    ) {
        val snapshot = runtimeState.snapshot
        if (snapshot == null) {
            _uiState.value = unavailableState(
                reason = runtimeState.errorMessage
                    ?: getApplication<Application>().getString(
                        R.string.light_dashboard_timeline_not_connected
                    )
            )
            return
        }

        val connectionText = when {
            runtimeState.errorMessage != null -> runtimeState.errorMessage
            runtimeState.isRefreshing -> "Syncing light controller"
            else -> "Live data synced"
        }

        _uiState.value = LightDashboardRuntimeUiMapper.map(
            context = getApplication<Application>(),
            snapshot = snapshot
        ).copy(
            isDeviceOnline = runtimeState.isDeviceOnline,
            controlsEnabled = runtimeState.isDeviceOnline,
            connectionStatusText = connectionText
        )
    }

    private fun unavailableState(
        reason: String
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
            liveMode = LightDashboardMode.SYNC,
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

    override fun onCleared() {
        runtimeCollectorJob?.cancel()
        runtimeSession?.release(consumerKey)
        super.onCleared()
    }

}
