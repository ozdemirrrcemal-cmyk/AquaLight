package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceLightSettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeviceLightSettingsUiState()
    )

    val uiState: StateFlow<DeviceLightSettingsUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<DeviceLightSettingsEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    init {
        refreshPhoneTime()
    }

    fun refreshPhoneTime() {
        _uiState.update {
            it.copy(
                phoneTime = currentPhoneTimeText()
            )
        }
    }

    fun setTemperatureProtectionEnabled(
        enabled: Boolean
    ) {
        _uiState.update {
            it.copy(
                temperatureProtectionEnabled = enabled
            )
        }

        viewModelScope.launch {
            // TODO: Send temperature protection enabled state to ESP32.
            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    if (enabled) {
                        "Temperature protection enabled"
                    } else {
                        "Temperature protection disabled"
                    }
                )
            )
        }
    }

    fun updateLimitTemperature(
        temperatureCelsius: Int
    ) {
        val safeValue = temperatureCelsius.coerceIn(40, 75)

        _uiState.update {
            it.copy(
                limitTemperatureCelsius = safeValue
            )
        }

        viewModelScope.launch {
            // TODO: Send TempLightErr to ESP32.
            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    "Limit temperature set to ${safeValue}°C"
                )
            )
        }
    }

    fun updateLightReduction(
        percent: Int
    ) {
        val safeValue = percent.coerceIn(40, 90)

        _uiState.update {
            it.copy(
                lightReductionPercent = safeValue
            )
        }

        viewModelScope.launch {
            // TODO: Send LightDownErr to ESP32 as ratio.
            // Example: 70% -> 0.7f
            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    "Light reduction set to $safeValue%"
                )
            )
        }
    }

    fun updateRecoveryInterval(
        seconds: Int
    ) {
        val safeValue = seconds.coerceIn(15, 300)

        _uiState.update {
            it.copy(
                recoveryIntervalSeconds = safeValue
            )
        }

        viewModelScope.launch {
            // TODO: Send TimeDownErr to ESP32 as milliseconds.
            // Example: 60s -> 60000
            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    "Recovery interval set to ${safeValue}s"
                )
            )
        }
    }

    fun syncTimeWithPhone() {
        viewModelScope.launch {
            val phoneTime = currentPhoneTimeText()

            // TODO: Send phone time to ESP32.
            // ESP32 success response geldiğinde deviceTime da phoneTime ile güncellenecek.

            _uiState.update {
                it.copy(
                    phoneTime = phoneTime,
                    lastSyncTime = currentLastSyncText()
                )
            }

            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage("Phone time refreshed")
            )
        }
    }

    fun updateFirmware() {
        viewModelScope.launch {
            // TODO: Open firmware update flow.
            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage("Firmware update coming soon")
            )
        }
    }

    private fun currentPhoneTimeText(): String {
        return SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date())
    }

    private fun currentLastSyncText(): String {
        return SimpleDateFormat(
            "'Today' HH:mm",
            Locale.getDefault()
        ).format(Date())
    }
}