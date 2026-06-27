package com.aqua.aqualight.ui.tabs.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get()
    private val connectivityObserver = DeviceConnectivityObserver(application)
    private val localNetworkAvailable = MutableStateFlow(true)
    private val clockMillis = MutableStateFlow(System.currentTimeMillis())

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        repository.start(viewModelScope)
        observeDevices()
        observeConnectivity()
        startPresenceTicker()
        refreshDiscoveryBurst()
    }

    fun onScreenVisible() {
        refreshDiscoveryBurst()
    }

    fun onDeviceClicked(deviceUid: String) {
        // RouteResolver + direct NavController navigation lands in the next migration step.
        // The click is intentionally captured here so UI already has a stable deviceUid boundary.
        if (deviceUid.isNotBlank()) {
            clockMillis.value = System.currentTimeMillis()
        }
    }

    private fun observeDevices() {
        viewModelScope.launch {
            combine(repository.devices, clockMillis) { snapshots, now ->
                val cards = snapshots.map { snapshot ->
                    DeviceCardMapper.map(snapshot = snapshot, nowMillis = now)
                }

                DevicesUiState(
                    devices = cards,
                    isEmpty = cards.isEmpty(),
                    isDiscovering = cards.isEmpty()
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.observeLocalNetworkAvailable()
                .distinctUntilChanged()
                .collect { available ->
                    localNetworkAvailable.value = available
                    repository.reevaluatePresence(localNetworkAvailable = available)
                    clockMillis.value = System.currentTimeMillis()
                    if (available) refreshDiscoveryBurst()
                }
        }
    }

    private fun startPresenceTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(PRESENCE_REEVALUATE_INTERVAL_MS)
                repository.reevaluatePresence(localNetworkAvailable = localNetworkAvailable.value)
                clockMillis.value = System.currentTimeMillis()
            }
        }
    }

    private fun refreshDiscoveryBurst() {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            runCatching { repository.refreshForegroundBurst() }
        }
    }

    data class DevicesUiState(
        val devices: List<DeviceCardUi> = emptyList(),
        val isEmpty: Boolean = true,
        val isDiscovering: Boolean = true
    )

    companion object {
        private const val PRESENCE_REEVALUATE_INTERVAL_MS = 15_000L
    }
}
