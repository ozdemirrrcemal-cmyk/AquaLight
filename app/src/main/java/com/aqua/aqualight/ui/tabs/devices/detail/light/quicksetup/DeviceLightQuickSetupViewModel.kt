package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.DeviceLightQuickSetupEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Temporary UI shell for Smart Quick Setup.
 *
 * Recommendation generation can be reviewed later. Saving and device loading
 * remain disabled until the new Light contract is connected.
 */
class DeviceLightQuickSetupViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        QuickSetupUiState(
            errorMessage = LIGHT_DATA_LAYER_NOT_CONNECTED
        )
    )
    val uiState: StateFlow<QuickSetupUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightQuickSetupEvent>()
    val events: SharedFlow<DeviceLightQuickSetupEvent> =
        _events.asSharedFlow()

    fun initialize(
        deviceId: Long
    ) {
        _uiState.value = QuickSetupUiState(
            errorMessage = if (deviceId <= 0L) {
                LIGHT_DEVICE_INFORMATION_MISSING
            } else {
                LIGHT_DATA_LAYER_NOT_CONNECTED
            }
        )
    }

    fun saveProgram() {
        emitUnavailable()
    }

    fun loadToDevice() {
        emitUnavailable()
    }

    private fun emitUnavailable() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightQuickSetupEvent.ShowError(
                    LIGHT_DATA_LAYER_NOT_CONNECTED
                )
            )
        }
    }
}
