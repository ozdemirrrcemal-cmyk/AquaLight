package com.aqua.aqualight.ui.tabs.settings.device

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceStatusOperations
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactVisualKind
import com.aqua.aqualight.ui.common.text.AquaUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DeviceStatusViewModel(
    private val operations: DeviceStatusOperations,
    private val clock: DeviceStatusClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = _uiState.asStateFlow()

    init {
        operations.start(viewModelScope)
        observeDeviceStatus()
    }

    private fun observeDeviceStatus() {
        viewModelScope.launch {
            combine(
                operations.statuses,
                clock.ticks
            ) { statuses, nowMillis ->
                val items = DeviceStatusSnapshotMapper.items(
                    statuses = statuses,
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
}

data class DeviceStatusUiState(
    val devices: List<DeviceStatusItem> = emptyList(),
    val isEmpty: Boolean = true
)

data class DeviceStatusItem(
    val displayName: String = "",
    @DrawableRes val iconRes: Int,
    val visualKind: DeviceCompactVisualKind = DeviceCompactVisualKind.ICON,
    val ip: String = "",
    val serialText: String = "",
    val lastSeenText: AquaUiText = AquaUiText.Resource(R.string.common_not_available_symbol),
    val isOnline: Boolean = false
)