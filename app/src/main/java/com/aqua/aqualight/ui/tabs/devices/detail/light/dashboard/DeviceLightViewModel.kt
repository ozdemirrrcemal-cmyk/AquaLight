package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeDeviceAccessor
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.LightDashboardMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline.LightDashboardTimelineMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceLightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val runtimeAccessor = LightRuntimeDeviceAccessor(
        context = application.applicationContext
    )
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(
        unavailableState(
            reason = getApplication<Application>().getString(
                R.string.light_dashboard_timeline_not_connected
            )
        )
    )

    val uiState: StateFlow<DeviceLightDashboardUiState> =
        _uiState.asStateFlow()

    private var deviceId: Long = 0L
    private var runtimeJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        if (this.deviceId == deviceId && runtimeJob?.isActive == true) {
            return
        }

        this.deviceId = deviceId
        startRuntimePolling()
    }

    fun refreshNow() {
        viewModelScope.launch {
            readAndRenderSnapshot()
        }
    }

    private fun startRuntimePolling() {
        runtimeJob?.cancel()

        if (deviceId <= 0L) {
            _uiState.value = unavailableState(
                reason = LIGHT_DEVICE_INFORMATION_MISSING
            )
            return
        }

        runtimeJob = viewModelScope.launch {
            readAndRenderSnapshot()

            while (isActive) {
                delay(RUNTIME_REFRESH_INTERVAL_MILLIS)
                readAndRenderSnapshot()
            }
        }
    }

    private suspend fun readAndRenderSnapshot() {
        refreshMutex.withLock {
            if (deviceId <= 0L) {
                _uiState.value = unavailableState(
                    reason = LIGHT_DEVICE_INFORMATION_MISSING
                )
                return@withLock
            }

            _uiState.value = when (val result = runtimeAccessor.readSnapshot(deviceId)) {
                is ApiResult.Success -> LightDashboardRuntimeUiMapper.map(
                    context = getApplication<Application>(),
                    snapshot = result.value
                )

                is ApiResult.Error -> unavailableState(
                    reason = result.error.message
                )
            }
        }
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
        runtimeJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val RUNTIME_REFRESH_INTERVAL_MILLIS = 5_000L
    }
}
