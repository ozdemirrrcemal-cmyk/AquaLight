package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightCoolingManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightThermalProtectionManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceAddressResolver
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceDataCenter
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeRepository
import com.aqua.aqualight.data.devices.presence.DeviceConnectionStatus
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    init {
        LightDeviceDataCenter.configure(appContext)
    }

    private val devicesDataStoreManager =
        DevicesDataStoreManager.create(appContext)

    private val lightDeviceTimeRepository =
        LightDeviceTimeRepository(
            context = appContext
        )

    private val addressResolver =
        LightDeviceAddressResolver(appContext)

    private val thermalProtectionManager =
        Esp32LightThermalProtectionManager()

    private val coolingManager =
        Esp32LightCoolingManager()

    private val _uiState =
        MutableStateFlow(DeviceLightSettingsUiState())

    val uiState: StateFlow<DeviceLightSettingsUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<DeviceLightSettingsEvent>(Channel.BUFFERED)

    val events =
        eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var profileJob: Job? = null
    private var liveStateJob: Job? = null
    private var isApplyingSettings: Boolean = false

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
            LightDeviceDataCenter.stop(
                deviceId = previousDeviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        this.deviceId = deviceId

        if (deviceId <= 0L) {
            _uiState.value = DeviceLightSettingsUiState()

            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Device information is missing"
                    )
                )
            }
            return
        }

        DevicePresenceMonitor.start(appContext)

        LightDeviceDataCenter.start(
            context = appContext,
            deviceId = deviceId,
            ownerKey = liveRefreshOwnerKey
        )

        refreshPhoneTime()
        observeDeviceProfile()
        observeLiveState()
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

        if (deviceId > 0L) {
            LightDeviceDataCenter.refreshNow(
                context = appContext,
                deviceId = deviceId
            )
        }
    }

    fun refreshAll(
        showMessage: Boolean
    ) {
        refreshPhoneTime()

        if (deviceId <= 0L) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Device information is missing"
                    )
                )
            }
            return
        }

        DevicePresenceMonitor.start(appContext)

        LightDeviceDataCenter.refreshNow(
            context = appContext,
            deviceId = deviceId
        )

        // Bilerek snackbar göstermiyoruz.
        // Refresh tuşu sadece sessiz canlı veri yenileme tetikler.
    }

    private fun observeLiveState() {
        liveStateJob?.cancel()

        liveStateJob = viewModelScope.launch {
            LightDeviceDataCenter.observeLiveState(
                deviceId = deviceId
            ).collect { liveState ->
                val thermal = liveState.thermalProtection
                val cooling = liveState.cooling

                _uiState.update { state ->
                    val preserveEditableSettings =
                        isApplyingSettings

                    val hasLiveContact =
                        liveState.hasAuthoritativeContact

                    if (!state.isDeviceOnline && !hasLiveContact && !liveState.hasCachedDisplayData) {
                        return@update state.copy(
                            deviceTime = "--:--",
                            thermalProtectionStatusText = "Unavailable",
                            currentTemperatureText = "-- °C",
                            temperatureSensorCount = 0,
                            coolingStatusText = "Unavailable",
                            coolingFansText = "—",
                            coolingMode = "Unavailable",
                            coolingModeEnabled = false,
                            coolingFanCount = 0
                        )
                    }

                    state.copy(
                        isDeviceOnline = state.isDeviceOnline || hasLiveContact,
                        controlsEnabled = state.controlsEnabled || hasLiveContact,
                        connectionStatusText = if (hasLiveContact) {
                            connectionStatusTextFor(DeviceConnectionStatus.ONLINE)
                        } else if (liveState.hasCachedDisplayData) {
                            "Syncing live data"
                        } else {
                            state.connectionStatusText
                        },
                        deviceTime = if (liveState.hasDeviceTime) {
                            liveState.deviceTimeText
                        } else {
                            "--:--"
                        },

                        thermalProtectionStatusText = thermal.statusText,
                        currentTemperatureText = thermal.currentTemperatureText,
                        temperatureSensorCount = thermal.sensorCount,

                        limitTemperatureCelsius = if (preserveEditableSettings) {
                            state.limitTemperatureCelsius
                        } else {
                            thermal.limitTemperatureCelsius
                        },

                        lightReductionPercent = if (preserveEditableSettings) {
                            state.lightReductionPercent
                        } else {
                            thermal.lightReductionPercent
                        },

                        recoveryIntervalSeconds = if (preserveEditableSettings) {
                            state.recoveryIntervalSeconds
                        } else {
                            thermal.recoveryIntervalSeconds
                        },

                        coolingStatusText = cooling.statusText,
                        coolingFansText = cooling.fansText,
                        coolingFanCount = cooling.fanCount,

                        coolingMode = if (preserveEditableSettings) {
                            state.coolingMode
                        } else {
                            cooling.coolingModeText
                        },

                        coolingModeEnabled = if (preserveEditableSettings) {
                            state.coolingModeEnabled
                        } else {
                            cooling.enabledFanCount > 0
                        },

                        fanStartTemperatureCelsius = if (preserveEditableSettings) {
                            state.fanStartTemperatureCelsius
                        } else {
                            cooling.fanStartTemperatureCelsius
                        },

                        fanFullSpeedTemperatureCelsius = if (preserveEditableSettings) {
                            state.fanFullSpeedTemperatureCelsius
                        } else {
                            cooling.fanFullSpeedTemperatureCelsius
                        }
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
                DevicePresenceMonitor.statuses,
                LightDeviceDataCenter.observeLiveState(deviceId)
            ) { devices, statuses, liveState ->
                DeviceProfileInputs(
                    devices = devices,
                    statuses = statuses,
                    liveState = liveState
                )
            }.collect { inputs ->
                val devices = inputs.devices
                val statuses = inputs.statuses
                val liveState = inputs.liveState
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
                            serialNumber = "—",
                            isDeviceOnline = false,
                            controlsEnabled = false,
                            connectionStatusText = "Device profile not found"
                        )
                    }
                    return@collect
                }

                val status = statuses[deviceId]
                val definition = AquaDeviceCatalog.findDefinition(
                    productId = device.productId,
                    productKey = device.productKey,
                    category = device.category
                )

                val resolvedIp = status?.ip
                    ?.ifBlank {
                        device.ip
                    }
                    ?: device.ip

                val catalogName = definition?.displayName.orEmpty()

                val resolvedDeviceName = device.aquaName
                    .ifBlank {
                        device.name
                    }
                    .ifBlank {
                        "—"
                    }

                val resolvedDeviceType = catalogName
                    .ifBlank {
                        device.productModel
                    }
                    .ifBlank {
                        formatEnumName(device.category.name)
                    }

                val hasLiveContact = liveState.hasAuthoritativeContact
                val isOnline = status?.isOnline == true || hasLiveContact
                val effectiveStatus = when {
                    status?.isOnline == true -> {
                        status.status
                    }

                    hasLiveContact -> {
                        DeviceConnectionStatus.ONLINE
                    }

                    else -> {
                        status?.status ?: DeviceConnectionStatus.UNKNOWN
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        deviceName = resolvedDeviceName,

                        deviceType = resolvedDeviceType,

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
                            },

                        isDeviceOnline = isOnline,
                        controlsEnabled = isOnline,
                        connectionStatusText = connectionStatusTextFor(
                            effectiveStatus
                        )
                    )
                }
            }
        }
    }

    fun syncTimeWithPhone() {
        launchSettingsOperation {
            if (deviceId <= 0L) {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Device information is missing"
                    )
                )
                return@launchSettingsOperation
            }

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
                return@launchSettingsOperation
            }

            val syncedTime = runCatching {
                lightDeviceTimeRepository.readDeviceTime(
                    deviceId = deviceId,
                    fallbackToPhone = false
                )
            }.getOrNull()

            _uiState.update { state ->
                state.copy(
                    deviceTime = syncedTime?.timeText ?: state.deviceTime,
                    phoneTime = phoneTime,
                    lastSyncTime = currentLastSyncText()
                )
            }

            LightDeviceDataCenter.refreshNow(
                context = appContext,
                deviceId = deviceId
            )

            eventsChannel.send(
                DeviceLightSettingsEvent.ShowMessage(
                    "Device clock synced"
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
                "Cooling set to Auto"
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
                DeviceLightSettingsEvent.ShowWarning(
                    "Firmware update is not available yet"
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
        launchSettingsOperation {
            val previousState = _uiState.value

            if (previousState.temperatureSensorCount <= 0) {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Temperature sensor is not configured"
                    )
                )
                return@launchSettingsOperation
            }

            _uiState.update { state ->
                state.copy(
                    limitTemperatureCelsius = limitTemperatureCelsius,
                    lightReductionPercent = lightReductionPercent,
                    recoveryIntervalSeconds = recoveryIntervalSeconds
                )
            }

            val address = when (
                val result = resolveAddress(
                    requireOnline = true
                )
            ) {
                is LightDeviceAddressResolver.Result.Success -> {
                    result
                }

                is LightDeviceAddressResolver.Result.Failure -> {
                    restoreThermalSettings(
                        previousState = previousState
                    )

                    eventsChannel.send(
                        DeviceLightSettingsEvent.ShowError(
                            result.message
                        )
                    )
                    return@launchSettingsOperation
                }
            }

            val result = thermalProtectionManager.setSettings(
                ip = address.ip,
                limitTemperatureCelsius = limitTemperatureCelsius,
                lightReductionPercent = lightReductionPercent,
                recoveryIntervalSeconds = recoveryIntervalSeconds,
                sensorCount = previousState.temperatureSensorCount
            )

            if (!result.isSuccess) {
                restoreThermalSettings(
                    previousState = previousState
                )

                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        result.message ?: "Thermal protection could not be updated"
                    )
                )
                return@launchSettingsOperation
            }

            LightDeviceDataCenter.refreshNow(
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
        launchSettingsOperation {
            val previousState = _uiState.value

            if (previousState.coolingFanCount <= 0) {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Cooling fan is not configured"
                    )
                )
                return@launchSettingsOperation
            }

            if (
                enabled &&
                previousState.temperatureSensorCount <= 0
            ) {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        "Temperature sensor is not configured"
                    )
                )
                return@launchSettingsOperation
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

            val address = when (
                val result = resolveAddress(
                    requireOnline = true
                )
            ) {
                is LightDeviceAddressResolver.Result.Success -> {
                    result
                }

                is LightDeviceAddressResolver.Result.Failure -> {
                    restoreCoolingSettings(previousState)

                    eventsChannel.send(
                        DeviceLightSettingsEvent.ShowError(
                            result.message
                        )
                    )
                    return@launchSettingsOperation
                }
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
                return@launchSettingsOperation
            }

            LightDeviceDataCenter.refreshNow(
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

    private fun launchSettingsOperation(
        block: suspend () -> Unit
    ) {
        if (!_uiState.value.controlsEnabled) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightSettingsEvent.ShowError(
                        _uiState.value.connectionStatusText
                    )
                )
            }
            return
        }

        if (isApplyingSettings) {
            return
        }

        isApplyingSettings = true

        viewModelScope.launch {
            eventsChannel.send(
                DeviceLightSettingsEvent.SetLoading(true)
            )

            try {
                block()
            } finally {
                eventsChannel.send(
                    DeviceLightSettingsEvent.SetLoading(false)
                )

                isApplyingSettings = false
            }
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

    private suspend fun resolveAddress(
        requireOnline: Boolean
    ): LightDeviceAddressResolver.Result {
        return addressResolver.resolve(
            deviceId = deviceId,
            requireOnline = requireOnline,
            forceLiveCheck = requireOnline
        )
    }


    private data class DeviceProfileInputs(
        val devices: List<DevicesDataStoreManager.DeviceInfo>,
        val statuses: Map<Long, DeviceStatusState>,
        val liveState: LightDeviceLiveState
    )

    private fun connectionStatusTextFor(
        status: DeviceConnectionStatus
    ): String {
        return when (status) {
            DeviceConnectionStatus.ONLINE -> "Online"
            DeviceConnectionStatus.CHECKING -> "Checking device connection"
            DeviceConnectionStatus.STALE -> "Connection is unstable · settings disabled"
            DeviceConnectionStatus.OFFLINE -> "Device offline · settings disabled"
            DeviceConnectionStatus.UNKNOWN -> "Waiting for device connection"
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
            LightDeviceDataCenter.stop(
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        super.onCleared()
    }
}