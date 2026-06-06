package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeRepository
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceLightSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val devicesDataStoreManager =
        DevicesDataStoreManager.create(appContext)

    private val lightDeviceTimeRepository =
        LightDeviceTimeRepository(
            context = appContext
        )

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

        DevicePresenceMonitor.start(appContext)

        refreshPhoneTime()
        refreshDeviceProfile()
        refreshDeviceTime(
            showError = false
        )

        if (initialized) {
            return
        }

        initialized = true
    }

    fun refreshPhoneTime() {
        _uiState.update { state ->
            state.copy(
                phoneTime = currentPhoneTimeText()
            )
        }
    }

    private fun refreshDeviceProfile() {
        viewModelScope.launch {
            val device = devicesDataStoreManager.devicesFlow
                .first()
                .firstOrNull { savedDevice ->
                    savedDevice.id == deviceId
                }

            if (device == null) {
                _uiState.update { state ->
                    state.copy(
                        deviceName = "—",
                        deviceType = "—",
                        deviceModel = "—",
                        firmwareVersion = "—",
                        connectionState = "Not found"
                    )
                }
                return@launch
            }

            val status = DevicePresenceMonitor.statuses.value[deviceId]

            _uiState.update { state ->
                state.copy(
                    deviceName = device.name
                        .ifBlank { device.aquaName }
                        .ifBlank { device.productModel }
                        .ifBlank { "Light Device" },
                    deviceType = formatEnumName(
                        device.deviceType.name
                    ),
                    deviceModel = device.productModel
                        .ifBlank { device.productId }
                        .ifBlank { "—" },
                    firmwareVersion = device.firmwareVersion
                        .ifBlank { device.firmwareBuild }
                        .ifBlank { "—" },
                    connectionState = when {
                        status?.isOnline == true -> "Online"
                        status != null -> formatEnumName(status.status.name)
                        else -> "Unknown"
                    }
                )
            }
        }
    }

    private fun refreshDeviceTime(
        showError: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                lightDeviceTimeRepository.readDeviceTime(
                    deviceId = deviceId,
                    fallbackToPhone = false
                )
            }.onSuccess { timeState ->
                _uiState.update { state ->
                    state.copy(
                        deviceTime = timeState.timeText
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        deviceTime = "--:--"
                    )
                }

                if (showError) {
                    eventsChannel.send(
                        DeviceLightSettingsEvent.ShowError(
                            error.message ?: "Device time could not be read"
                        )
                    )
                }
            }
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

            _uiState.update { state ->
                state.copy(
                    phoneTime = phoneTime
                )
            }

            val result = lightDeviceTimeRepository.syncDeviceTimeWithPhone(
                deviceId = deviceId
            )

            if (!result.isSuccess) {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        result.message ?: "Device time could not be synced"
                    )
                )
                return@launch
            }

            val syncedTime = runCatching {
                lightDeviceTimeRepository.readDeviceTime(
                    deviceId = deviceId,
                    fallbackToPhone = true
                )
            }.getOrElse {
                null
            }

            _uiState.update { state ->
                state.copy(
                    deviceTime = syncedTime?.timeText ?: phoneTime,
                    phoneTime = phoneTime,
                    lastSyncTime = currentLastSyncText()
                )
            }

            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    "Device time synced with phone"
                )
            )
        }
    }

    fun updateFirmware() {
        viewModelScope.launch {
            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    "Firmware update coming soon"
                )
            )
        }
    }

    private fun currentPhoneTimeText(): String {
        return SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date())
    }
	
	fun refreshTimes() {
          refreshPhoneTime()
          refreshDeviceTime(
        showError = false
        )
    }

    private fun currentLastSyncText(): String {
        return SimpleDateFormat(
            "'Today' HH:mm",
            Locale.getDefault()
        ).format(Date())
    }

    private fun formatEnumName(
        value: String
    ): String {
        return value
            .lowercase(Locale.getDefault())
            .split("_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    char.uppercase(Locale.getDefault())
                }
            }
    }
}