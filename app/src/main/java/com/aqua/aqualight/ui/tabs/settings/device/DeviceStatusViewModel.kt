package com.aqua.aqualight.ui.tabs.settings.device

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceStatusViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = _uiState.asStateFlow()
}

data class DeviceStatusUiState(
    val devices: List<DeviceStatusItem> = emptyList(),
    val isEmpty: Boolean = true
)

data class DeviceStatusItem(
    val displayName: String = "",
    val supportingText: String = "",
    val ip: String = "",
    val deviceCode: String = "",
    val productName: String = "",
    val lastSeenText: String = "",
    val isOnline: Boolean = false
)
