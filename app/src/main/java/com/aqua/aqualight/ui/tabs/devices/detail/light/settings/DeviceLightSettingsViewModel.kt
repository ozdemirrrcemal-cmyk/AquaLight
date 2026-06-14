package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Temporary UI shell for light device settings.
 *
 * The settings form is intentionally UI-only until the new Light settings
 * contract is designed and connected.
 */
class DeviceLightSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        unavailableState()
    )
    val uiState: StateFlow<DeviceLightSettingsUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightSettingsEvent>()
    val events: SharedFlow<DeviceLightSettingsEvent> =
        _events.asSharedFlow()

    fun initialize(
        deviceId: Long
    ) {
        _uiState.value = unavailableState(
            reason = if (deviceId <= 0L) {
                LIGHT_DEVICE_INFORMATION_MISSING
            } else {
                LIGHT_DATA_LAYER_NOT_CONNECTED
            }
        )
    }

    fun refreshAll(
        showMessage: Boolean = true
    ) {
        refreshTimes()
        if (showMessage) {
            emitWarning()
        }
    }

    fun refreshTimes() {
        _uiState.update { state ->
            state.copy(
                phoneTime = SimpleDateFormat(
                    "HH:mm",
                    Locale.getDefault()
                ).format(Date())
            )
        }
    }

    fun syncTimeWithPhone() {
        emitWarning()
    }

    fun updateFirmware() {
        emitWarning()
    }

    fun updateLimitTemperature(
        value: Int
    ) {
        emitWarning()
    }

    fun updateLightReduction(
        value: Int
    ) {
        emitWarning()
    }

    fun updateRecoveryInterval(
        value: Int
    ) {
        emitWarning()
    }

    fun updateCoolingMode(
        enabled: Boolean
    ) {
        emitWarning()
    }

    fun updateFanStartTemperature(
        value: Int
    ) {
        emitWarning()
    }

    fun updateFanFullSpeedTemperature(
        value: Int
    ) {
        emitWarning()
    }

    private fun unavailableState(
        reason: String = LIGHT_DATA_LAYER_NOT_CONNECTED
    ): DeviceLightSettingsUiState {
        return DeviceLightSettingsUiState(
            deviceName = "—",
            deviceType = "Light Controller",
            firmwareVersion = "—",
            deviceIp = "—",
            serialNumber = "—",
            deviceTime = "--:--",
            phoneTime = SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date()),
            lastSyncTime = "Never",
            thermalProtectionStatusText = reason,
            coolingStatusText = reason,
            coolingMode = "Unavailable",
            isDeviceOnline = false,
            controlsEnabled = false,
            connectionStatusText = reason
        )
    }

    private fun emitWarning() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightSettingsEvent.ShowWarning(
                    LIGHT_DATA_LAYER_NOT_CONNECTED
                )
            )
        }
    }
}
