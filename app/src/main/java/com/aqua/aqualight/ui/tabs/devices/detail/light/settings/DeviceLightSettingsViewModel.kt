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

    private var deviceId: Long = 0L
    private var initialized = false

    init {
        refreshPhoneTime()
    }

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        if (initialized) {
            refreshPhoneTime()
            return
        }

        initialized = true

        refreshPhoneTime()

        // TODO: ESP32 / DataStore bağlantısı yapılınca:
        // - Bu deviceId ile cihaz bilgileri okunacak.
        // - Firmware, model, connection state alınacak.
        // - Temperature protection ayarları okunacak.
        // - Fan / cooling / time bilgileri okunacak.
        // - UI state bu cihaza göre doldurulacak.
    }

    fun refreshPhoneTime() {
        _uiState.update { state ->
            state.copy(
                phoneTime = currentPhoneTimeText()
            )
        }
    }

    fun setTemperatureProtectionEnabled(
        enabled: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                temperatureProtectionEnabled = enabled
            )
        }

        viewModelScope.launch {
            // TODO: ESP32 cihaz bazlı gönderim:
            // deviceId = this@DeviceLightSettingsViewModel.deviceId
            // command = temperatureProtectionEnabled
            // value = enabled

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

        _uiState.update { state ->
            state.copy(
                limitTemperatureCelsius = safeValue
            )
        }

        viewModelScope.launch {
            // TODO: ESP32 cihaz bazlı gönderim:
            // deviceId = this@DeviceLightSettingsViewModel.deviceId
            // ESP32 variable = TempLightErr
            // value = safeValue

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

        _uiState.update { state ->
            state.copy(
                lightReductionPercent = safeValue
            )
        }

        viewModelScope.launch {
            // TODO: ESP32 cihaz bazlı gönderim:
            // deviceId = this@DeviceLightSettingsViewModel.deviceId
            // ESP32 variable = LightDownErr
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

        _uiState.update { state ->
            state.copy(
                recoveryIntervalSeconds = safeValue
            )
        }

        viewModelScope.launch {
            // TODO: ESP32 cihaz bazlı gönderim:
            // deviceId = this@DeviceLightSettingsViewModel.deviceId
            // ESP32 variable = TimeDownErr
            // Example: 60s -> 60000ms

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

            // TODO: ESP32 cihaz bazlı gönderim:
            // deviceId = this@DeviceLightSettingsViewModel.deviceId
            // phone time -> ESP32 RTC/NTP time update
            // ESP32 success response geldiğinde deviceTime da phoneTime ile güncellenecek.

            _uiState.update { state ->
                state.copy(
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
            // TODO: ESP32 cihaz bazlı firmware flow:
            // deviceId = this@DeviceLightSettingsViewModel.deviceId
            // Firmware update screen / OTA flow açılacak.

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