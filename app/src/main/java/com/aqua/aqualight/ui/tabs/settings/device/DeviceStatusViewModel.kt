package com.aqua.aqualight.ui.tabs.settings.device

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceStatusViewModel(
    private val devicesRepository: DevicesRepository
) : ViewModel() {

    private val clockMillis = MutableStateFlow(System.currentTimeMillis())
    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = _uiState.asStateFlow()

    init {
        devicesRepository.start(viewModelScope)
        observeDeviceStatus()
        startLastSeenTicker()
    }

    private fun observeDeviceStatus() {
        viewModelScope.launch {
            combine(
                devicesRepository.devices,
                clockMillis
            ) { snapshots, nowMillis ->
                val items = DeviceStatusSnapshotMapper.items(
                    snapshots = snapshots,
                    nowMillis = nowMillis
                )
                DeviceStatusUiState(
                    devices = items,
                    isEmpty = items.isEmpty()
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun startLastSeenTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(LAST_SEEN_TICK_MS)
                clockMillis.value = System.currentTimeMillis()
            }
        }
    }

    private companion object {
        const val LAST_SEEN_TICK_MS = 15_000L
    }
}

data class DeviceStatusUiState(
    val devices: List<DeviceStatusItem> = emptyList(),
    val isEmpty: Boolean = true
)

data class DeviceStatusItem(
    val displayName: String = "",
    @DrawableRes val iconRes: Int,
    val ip: String = "",
    val serialText: String = "",
    val lastSeenText: String = "",
    val isOnline: Boolean = false
)
