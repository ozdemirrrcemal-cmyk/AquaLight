package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightCoolingManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightThermalProtectionManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceAddressResolver
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeRepository
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val addressResolver =
        LightDeviceAddressResolver(appContext)

    private val thermalProtectionManager =
        Esp32LightThermalProtectionManager()

    private val coolingManager =
        Esp32LightCoolingManager()

    private var deviceId: Long = 0L
    private var profileJob: Job? = null
    private var liveStateJob: Job? = null

    private val liveRefreshOwnerKey =
        "DeviceLightSettingsViewModel_${System.identityHashCode(this)}"

    init {
        refreshPhoneTime()
    }

    fun initialize(
        deviceId: Long
    ) {
        val previousDeviceId = this.deviceId

        if (
            previousDeviceId > 0L &&
            previousDeviceId != deviceId
        ) {
            LightDeviceLiveRefreshManager.stop(
                deviceId = previousDeviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        this.deviceId = deviceId

        DevicePresenceMonitor.start(appContext)

        LightDeviceLiveRefreshManager.start(
            context = appContext,
            deviceId = deviceId,
            ownerKey = liveRefreshOwnerKey
        )

        refreshPhoneTime()
        observeDeviceProfile()
        observeLiveState()

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )
    }

    fun refreshPhoneTime() {
        _uiState.update { state ->
            state.copy(
                phoneTime = currentPhoneTimeText()
            )
        }
    }

    fun refreshTimes() {
        refreshPhoneTime()

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )
    }

    fun refreshAll(
        showMessage: Boolean
    ) {
        refreshPhoneTime()

        DevicePresenceMonitor.start(appContext)

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )

        if (showMessage) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowMessage(
                        "Device info refreshed"
                    )
                )
            }
        }
    }

    private fun observeLiveState() {
        liveStateJob?.cancel()

        liveStateJob = viewModelScope.launch {
            LightDeviceLiveRefreshManager.observe(
                deviceId = deviceId
            ).collect { liveState ->
                val thermal = liveState.thermalProtection
                val cooling = liveState.cooling

                _uiState.update { state ->
                    state.copy(
                        deviceTime = if (liveState.hasDeviceTime) {
                            liveState.deviceTimeText
                        } else {
                            "--:--"
                        },

                        thermalProtectionStatusText = thermal.statusText,
                        currentTemperatureText = thermal.currentTemperatureText,
                        temperatureSensorCount = thermal.sensorCount,
                        limitTemperatureCelsius = thermal.limitTemperatureCelsius,
                        lightReductionPercent = thermal.lightReductionPercent,
                        recoveryIntervalSeconds = thermal.recoveryIntervalSeconds,

                        coolingStatusText = cooling.statusText,
                        coolingFansText = cooling.fansText,
                        coolingMode = cooling.coolingModeText,
                        coolingModeEnabled = cooling.enabledFanCount > 0,
                        coolingFanCount = cooling.fanCount,
                        fanStartTemperatureCelsius = cooling.fanStartTemperatureCelsius,
                        fanFullSpeedTemperatureCelsius = cooling.fanFullSpeedTemperatureCelsius
                    )
                }
            }
        }
    }

    private fun observeDeviceProfile() {
        profileJob?.cancel()

        profileJob = viewModelScope.launch {
            combine(
                devicesDataStoreManager.devicesFlow,
                DevicePresenceMonitor.statuses
            ) { devices, statuses ->
                devices to statuses
            }.collect { (devices, statuses) ->
                val device = devices.firstOrNull { savedDevice ->
                    savedDevice.id == deviceId
                }

                if (device == null) {
                    _uiState.update { state ->
                        state.copy(
                            deviceName = "—",
                            deviceType = "—",
                            firmwareVersion = "—",
                            deviceIp = "—",
                            serialNumber = "—"
                        )
                    }
                    return@collect
                }

                val status = statuses[deviceId]
                val definition = AquaDeviceCatalog.findByType(device.deviceType)

                val resolvedIp = status?.ip
                    ?.ifBlank {
                        device.ip
                    }
                    ?: device.ip

                _uiState.update { state ->
                    state.copy(
                        deviceName = device.name
                            .ifBlank {
                                device.aquaName
                            }
                            .ifBlank {
                                device.productModel
                            }
                            .ifBlank {
                                "Light Device"
                            },

                        deviceType = definition?.displayName
                            ?.ifBlank {
                                formatEnumName(device.deviceType.name)
                            }
                            ?: formatEnumName(device.deviceType.name),

                        firmwareVersion = device.firmwareVersion
                            .ifBlank {
                                device.firmwareBuild
                            }
                            .ifBlank {
                                "—"
                            },

                        deviceIp = resolvedIp.ifBlank {
                            "—"
                        },

                        serialNumber = device.serial
                            .ifBlank {
                                "—"
                            }
                    )
                }
            }
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
                    fallbackToPhone = false
                )
            }.getOrElse {
                null
            }

            _uiState.update { state ->
                state.copy(
                    deviceTime = syncedTime?.timeText ?: state.deviceTime,
                    phoneTime = phoneTime,
                    lastSyncTime = currentLastSyncText()
                )
            }

            LightDeviceLiveRefreshManager.refreshNow(
                context = appContext,
                deviceId = deviceId
            )

            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    "Device time synced with phone"
                )
            )
        }
    }

    fun updateLimitTemperature(
        temperatureCelsius: Int
    ) {
        val safeValue = temperatureCelsius.coerceIn(40, 75)
        val currentState = _uiState.value

        applyThermalSettings(
            limitTemperatureCelsius = safeValue,
            lightReductionPercent = currentState.lightReductionPercent,
            recoveryIntervalSeconds = currentState.recoveryIntervalSeconds,
            successMessage = "Limit temperature set to ${safeValue}°C"
        )
    }

    fun updateLightReduction(
        percent: Int
    ) {
        val safeValue = percent.coerceIn(40, 90)
        val currentState = _uiState.value

        applyThermalSettings(
            limitTemperatureCelsius = currentState.limitTemperatureCelsius,
            lightReductionPercent = safeValue,
            recoveryIntervalSeconds = currentState.recoveryIntervalSeconds,
            successMessage = "Light reduction set to $safeValue%"
        )
    }

    fun updateRecoveryInterval(
        seconds: Int
    ) {
        val safeValue = seconds.coerceIn(15, 300)
        val currentState = _uiState.value

        applyThermalSettings(
            limitTemperatureCelsius = currentState.limitTemperatureCelsius,
            lightReductionPercent = currentState.lightReductionPercent,
            recoveryIntervalSeconds = safeValue,
            successMessage = "Recovery interval set to ${safeValue}s"
        )
    }

    fun updateCoolingMode(
        enabled: Boolean
    ) {
        val currentState = _uiState.value

        applyCoolingSettings(
            enabled = enabled,
            fanStartTemperatureCelsius = currentState.fanStartTemperatureCelsius,
            fanFullSpeedTemperatureCelsius = currentState.fanFullSpeedTemperatureCelsius,
            successMessage = if (enabled) {
                "Cooling mode set to Auto"
            } else {
                "Cooling disabled"
            }
        )
    }

    fun updateFanStartTemperature(
        temperatureCelsius: Int
    ) {
        val currentState = _uiState.value

        val safeStart = temperatureCelsius.coerceIn(25, 45)
        val safeFull = currentState.fanFullSpeedTemperatureCelsius
            .coerceAtLeast(safeStart + 5)
            .coerceAtMost(70)

        applyCoolingSettings(
            enabled = currentState.coolingModeEnabled,
            fanStartTemperatureCelsius = safeStart,
            fanFullSpeedTemperatureCelsius = safeFull,
            successMessage = "Fan start set to ${safeStart}°C"
        )
    }

    fun updateFanFullSpeedTemperature(
        temperatureCelsius: Int
    ) {
        val currentState = _uiState.value

        val safeFull = temperatureCelsius
            .coerceIn(
                currentState.fanStartTemperatureCelsius + 5,
                70
            )

        applyCoolingSettings(
            enabled = currentState.coolingModeEnabled,
            fanStartTemperatureCelsius = currentState.fanStartTemperatureCelsius,
            fanFullSpeedTemperatureCelsius = safeFull,
            successMessage = "Full speed set to ${safeFull}°C"
        )
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

    private fun applyThermalSettings(
        limitTemperatureCelsius: Int,
        lightReductionPercent: Int,
        recoveryIntervalSeconds: Int,
        successMessage: String
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value

            _uiState.update { state ->
                state.copy(
                    limitTemperatureCelsius = limitTemperatureCelsius,
                    lightReductionPercent = lightReductionPercent,
                    recoveryIntervalSeconds = recoveryIntervalSeconds
                )
            }

            val address = resolveAddress()

            if (address == null) {
                restoreThermalSettings(
                    previousState = currentState
                )

                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Device address could not be resolved"
                    )
                )
                return@launch
            }

            val result = thermalProtectionManager.setSettings(
                ip = address.ip,
                limitTemperatureCelsius = limitTemperatureCelsius,
                lightReductionPercent = lightReductionPercent,
                recoveryIntervalSeconds = recoveryIntervalSeconds,
                sensorCount = currentState.temperatureSensorCount
            )

            if (!result.isSuccess) {
                restoreThermalSettings(
                    previousState = currentState
                )

                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        result.message ?: "Thermal protection could not be updated"
                    )
                )
                return@launch
            }

            LightDeviceLiveRefreshManager.refreshNow(
                context = appContext,
                deviceId = deviceId
            )

            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    successMessage
                )
            )
        }
    }

    private fun applyCoolingSettings(
        enabled: Boolean,
        fanStartTemperatureCelsius: Int,
        fanFullSpeedTemperatureCelsius: Int,
        successMessage: String
    ) {
        viewModelScope.launch {
            val previousState = _uiState.value

            if (previousState.coolingFanCount <= 0) {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Cooling fan is not configured"
                    )
                )
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    coolingMode = if (enabled) {
                        "Auto"
                    } else {
                        "Disabled"
                    },
                    coolingModeEnabled = enabled,
                    fanStartTemperatureCelsius = fanStartTemperatureCelsius,
                    fanFullSpeedTemperatureCelsius = fanFullSpeedTemperatureCelsius
                )
            }

            val address = resolveAddress()

            if (address == null) {
                restoreCoolingSettings(previousState)

                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Device address could not be resolved"
                    )
                )
                return@launch
            }

            val result = coolingManager.setSettingsForAllFans(
                ip = address.ip,
                enabled = enabled,
                fanStartTemperatureCelsius = fanStartTemperatureCelsius,
                fanFullSpeedTemperatureCelsius = fanFullSpeedTemperatureCelsius,
                fanCount = previousState.coolingFanCount
            )

            if (!result.isSuccess) {
                restoreCoolingSettings(previousState)

                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        result.message ?: "Cooling settings could not be updated"
                    )
                )
                return@launch
            }

            LightDeviceLiveRefreshManager.refreshNow(
                context = appContext,
                deviceId = deviceId
            )

            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    successMessage
                )
            )
        }
    }

    private fun restoreCoolingSettings(
        previousState: DeviceLightSettingsUiState
    ) {
        _uiState.update { state ->
            state.copy(
                coolingMode = previousState.coolingMode,
                coolingModeEnabled = previousState.coolingModeEnabled,
                fanStartTemperatureCelsius = previousState.fanStartTemperatureCelsius,
                fanFullSpeedTemperatureCelsius = previousState.fanFullSpeedTemperatureCelsius
            )
        }
    }

    private fun restoreThermalSettings(
        previousState: DeviceLightSettingsUiState
    ) {
        _uiState.update { state ->
            state.copy(
                limitTemperatureCelsius = previousState.limitTemperatureCelsius,
                lightReductionPercent = previousState.lightReductionPercent,
                recoveryIntervalSeconds = previousState.recoveryIntervalSeconds
            )
        }
    }

    private suspend fun resolveAddress(): LightDeviceAddressResolver.Result.Success? {
        return when (
            val result = addressResolver.resolve(
                deviceId = deviceId,
                requireOnline = false
            )
        ) {
            is LightDeviceAddressResolver.Result.Success -> result
            is LightDeviceAddressResolver.Result.Failure -> null
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

    override fun onCleared() {
        profileJob?.cancel()
        liveStateJob?.cancel()

        if (deviceId > 0L) {
            LightDeviceLiveRefreshManager.stop(
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        super.onCleared()
    }
}