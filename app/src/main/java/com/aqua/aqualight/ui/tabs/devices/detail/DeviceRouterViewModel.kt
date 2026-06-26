package com.aqua.aqualight.ui.tabs.devices.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceRouterViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DeviceRouterUiState())
    val uiState: StateFlow<DeviceRouterUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceRouterEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DeviceRouterEvent> = _events.receiveAsFlow()

    private var hasResolvedRoute = false

    fun resolveRoute(deviceId: Long, deviceTitle: String) {
        if (hasResolvedRoute) return
        hasResolvedRoute = true
        _uiState.update { state -> state.copy(isRouting = true) }
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isRouting = false) }
            _events.send(
                DeviceRouterEvent.OpenDestination(
                    DeviceRouterDestination(
                        title = deviceTitle.ifBlank { "Device" },
                        message = "Device detail controllers were removed. New BLE/QR + UDP/WebSocket device detail architecture will be connected here."
                    )
                )
            )
        }
    }
}

data class DeviceRouterUiState(val isRouting: Boolean = false)

sealed class DeviceRouterEvent {
    data class OpenDestination(val destination: DeviceRouterDestination) : DeviceRouterEvent()
}
