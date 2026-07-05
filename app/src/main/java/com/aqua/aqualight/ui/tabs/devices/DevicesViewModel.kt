package com.aqua.aqualight.ui.tabs.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)
    private val connectivityObserver = DeviceConnectivityObserver(application)
    private val routeResolver = DeviceRouteResolver()
    private val localNetworkAvailable = MutableStateFlow(true)
    private val clockMillis = MutableStateFlow(System.currentTimeMillis())
    private val selectedDeviceUids = MutableStateFlow<Set<String>>(emptySet())

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<DevicesEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DevicesEvent> = _events.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        repository.start(viewModelScope)
        observeDevices()
        observeConnectivity()
        refreshForegroundPresence()
    }

    fun onScreenVisible() {
        refreshForegroundPresence()
    }

    fun onDeviceClicked(deviceUid: String) {
        if (deviceUid.isBlank()) return

        if (_uiState.value.selectionMode) {
            toggleDeviceSelection(deviceUid)
            return
        }

        viewModelScope.launch {
            val route = runCatching {
                val uid = DeviceUid(deviceUid)
                val snapshot = repository.currentDevice(uid)

                if (snapshot != null && snapshot.endpoint.hasWebSocketEndpoint) {
                    repository.connectRuntime(uid)
                }

                routeResolver.resolve(
                    snapshot = snapshot,
                    requestedDeviceUid = deviceUid
                )
            }.getOrElse {
                routeResolver.resolve(
                    snapshot = null,
                    requestedDeviceUid = deviceUid
                )
            }

            clockMillis.value = System.currentTimeMillis()
            _events.send(DevicesEvent.OpenRoute(route))
        }
    }

    fun onDeviceLongClicked(deviceUid: String) {
        if (deviceUid.isBlank()) return
        selectedDeviceUids.value = selectedDeviceUids.value + deviceUid
    }

    fun clearSelection() {
        selectedDeviceUids.value = emptySet()
    }

    fun deleteSelectedDevices() {
        val selected = selectedDeviceUids.value
        if (selected.isEmpty()) return

        viewModelScope.launch {
            selected.forEach { rawDeviceUid ->
                runCatching {
                    repository.forgetDevice(DeviceUid(rawDeviceUid))
                }
            }

            selectedDeviceUids.value = emptySet()
            clockMillis.value = System.currentTimeMillis()
        }
    }

    private fun toggleDeviceSelection(deviceUid: String) {
        val current = selectedDeviceUids.value
        selectedDeviceUids.value = if (deviceUid in current) {
            current - deviceUid
        } else {
            current + deviceUid
        }
    }

    private fun observeDevices() {
        viewModelScope.launch {
            combine(repository.devices, clockMillis, selectedDeviceUids) { snapshots, now, selectedUids ->
                val cards = snapshots.map { snapshot ->
                    val card = DeviceCardMapper.map(snapshot = snapshot, nowMillis = now)
                    card.copy(isSelected = card.deviceUid in selectedUids)
                }
                val visibleSelectedCount = cards.count { card -> card.isSelected }

                DevicesUiState(
                    devices = cards,
                    isEmpty = cards.isEmpty(),
                    isDiscovering = cards.isEmpty(),
                    selectionMode = visibleSelectedCount > 0,
                    selectedCount = visibleSelectedCount
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
                    if (available) {
                        refreshForegroundPresence()
                    } else {
                        repository.reevaluatePresence(localNetworkAvailable = false)
                        clockMillis.value = System.currentTimeMillis()
                    }
                }
        }
    }

    private fun refreshForegroundPresence() {
        if (!localNetworkAvailable.value) {
            repository.reevaluatePresence(localNetworkAvailable = false)
            clockMillis.value = System.currentTimeMillis()
            return
        }
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            repository.reevaluatePresence(localNetworkAvailable = true)
            repository.refreshForegroundRuntimeConnections()
            runCatching { repository.refreshForegroundBurst() }
            delay(FOREGROUND_REFRESH_SETTLE_MS)
            repository.reevaluatePresence(localNetworkAvailable = localNetworkAvailable.value)
            clockMillis.value = System.currentTimeMillis()
        }
    }

    data class DevicesUiState(
        val devices: List<DeviceCardUi> = emptyList(),
        val isEmpty: Boolean = true,
        val isDiscovering: Boolean = true,
        val selectionMode: Boolean = false,
        val selectedCount: Int = 0
    )

    companion object {
        private const val FOREGROUND_REFRESH_SETTLE_MS = 4_000L
    }
}
