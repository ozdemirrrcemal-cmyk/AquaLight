package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSession
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSnapshot
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeState
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val consumerKey = "light_settings_${System.identityHashCode(this)}"

    private val runtimeRepository = LightRuntimeRepository.get(
        context = application.applicationContext
    )

    private val _uiState = MutableStateFlow(
        unavailableState(
            reason = "Waiting for live settings data"
        )
    )
    val uiState: StateFlow<DeviceLightSettingsUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightSettingsEvent>()
    val events: SharedFlow<DeviceLightSettingsEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L
    private var runtimeSession: LightRuntimeSession? = null
    private var runtimeCollectorJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        if (this.deviceId == deviceId && runtimeSession != null) {
            return
        }

        runtimeCollectorJob?.cancel()
        runtimeSession?.release(consumerKey)
        runtimeSession = null
        this.deviceId = deviceId

        if (deviceId <= 0L) {
            _uiState.value = unavailableState(
                reason = LIGHT_DEVICE_INFORMATION_MISSING
            )
            return
        }

        val session = runtimeRepository.session(deviceId)
        runtimeSession = session
        runtimeCollectorJob = viewModelScope.launch {
            session.state.collectLatest { runtimeState ->
                renderRuntimeState(runtimeState)
            }
        }
    }

    fun onSettingsVisible() {
        runtimeSession?.acquire(consumerKey)
    }

    fun onSettingsHidden() {
        runtimeSession?.release(consumerKey)
    }

    fun refreshAll(
        showMessage: Boolean = true
    ) {
        refreshTimes()
        val session = runtimeSession ?: return

        viewModelScope.launch {
            if (showMessage) {
                _events.emit(
                    DeviceLightSettingsEvent.SetLoading(true)
                )
            }

            when (val result = session.refreshNow()) {
                is ApiResult.Success -> {
                    if (showMessage) {
                        _events.emit(
                            DeviceLightSettingsEvent.ShowMessage(
                                "Live settings synced"
                            )
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (showMessage) {
                        _events.emit(
                            DeviceLightSettingsEvent.ShowError(
                                result.error.message
                            )
                        )
                    }
                }
            }

            if (showMessage) {
                _events.emit(
                    DeviceLightSettingsEvent.SetLoading(false)
                )
            }
        }
    }

    fun refreshTimes() {
        _uiState.update { state ->
            state.copy(
                phoneTime = currentClockText()
            )
        }
    }

    fun syncTimeWithPhone() {
        emitReadOnlyWarning()
    }

    fun updateFirmware() {
        emitReadOnlyWarning()
    }

    fun updateLimitTemperature(
        value: Int
    ) {
        emitReadOnlyWarning()
    }

    fun updateLightReduction(
        value: Int
    ) {
        emitReadOnlyWarning()
    }

    fun updateRecoveryInterval(
        value: Int
    ) {
        emitReadOnlyWarning()
    }

    fun updateCoolingMode(
        enabled: Boolean
    ) {
        emitReadOnlyWarning()
    }

    fun updateFanStartTemperature(
        value: Int
    ) {
        emitReadOnlyWarning()
    }

    fun updateFanFullSpeedTemperature(
        value: Int
    ) {
        emitReadOnlyWarning()
    }

    private fun renderRuntimeState(
        runtimeState: LightRuntimeState
    ) {
        val snapshot = runtimeState.snapshot
        val device = runtimeState.device

        if (snapshot == null) {
            _uiState.value = unavailableState(
                reason = runtimeState.errorMessage ?: "Syncing live settings data",
                device = device
            )
            return
        }

        _uiState.value = mapRuntimeState(
            device = device,
            snapshot = snapshot,
            runtimeState = runtimeState
        )
    }

    private fun mapRuntimeState(
        device: DevicesDataStoreManager.DeviceInfo?,
        snapshot: LightRuntimeSnapshot,
        runtimeState: LightRuntimeState
    ): DeviceLightSettingsUiState {
        val primaryTemperature = snapshot.temperatureSensors
            .mapNotNull { sensor -> sensor.temperatureCelsius }
            .maxOrNull()
        val thermal = snapshot.thermalProtection
        val coolingController = snapshot.coolingControllers.firstOrNull()
        val fanPercent = snapshot.fanOutputPercent
            ?: coolingController?.linkedFanChannel?.currentPercent
        val lastSynced = runtimeState.lastSyncedAtMillis
        val connectionStatus = when {
            runtimeState.errorMessage != null -> runtimeState.errorMessage
            runtimeState.isRefreshing -> "Syncing live settings data"
            else -> "Live settings data synced"
        }

        return DeviceLightSettingsUiState(
            deviceName = device?.resolvedTitle ?: "AquaLight",
            deviceType = device?.productModel.nonBlank()
                ?: device?.displayName.nonBlank()
                ?: "Light Controller",
            firmwareVersion = device?.firmwareVersion.nonBlank()
                ?: device?.firmwareBuild.nonBlank()
                ?: "—",
            lastKnownIpText = device?.ip.nonBlank() ?: "—",
            serialNumber = device?.serialNumber.nonBlank()
                ?: device?.serial.nonBlank()
                ?: device?.firmwareSerial.nonBlank()
                ?: "—",
            productId = device?.productId.orEmpty(),
            productKey = device?.productKey?.name.orEmpty().takeUnless { it == "UNKNOWN" }.orEmpty(),
            skuCode = device?.skuCode.orEmpty(),
            setupCode = device?.setupCode.orEmpty(),
            deviceUid = device?.deviceUid.orEmpty(),
            macAddress = device?.macAddress.orEmpty(),
            hardwareRevision = device?.hardwareRevision.orEmpty(),
            protocolVersion = device?.protocolVersion?.toString()
                ?: device?.apiVersion?.toString()
                ?: "",
            deviceTime = snapshot.deviceTime.currentText.ifBlank {
                "--:--"
            },
            phoneTime = currentClockText(),
            lastSyncTime = lastSynced?.let(::formatSyncTime) ?: "Never",
            thermalProtectionStatusText = thermalStatusText(
                limitCelsius = thermal.limitCelsius,
                reductionPercent = thermal.reductionPercent
                    ?: thermal.lightDownErrPercent
            ),
            currentTemperatureText = formatTemperature(primaryTemperature),
            temperatureSensorCount = snapshot.temperatureSensors.size,
            limitTemperatureCelsius = thermal.limitCelsius?.roundToInt() ?: DEFAULT_LIMIT_CELSIUS,
            lightReductionPercent = thermal.reductionPercent
                ?: thermal.lightDownErrPercent
                ?: DEFAULT_LIGHT_REDUCTION_PERCENT,
            recoveryIntervalSeconds = thermal.recoveryIntervalSeconds
                ?: DEFAULT_RECOVERY_INTERVAL_SECONDS,
            coolingStatusText = coolingStatusText(
                enabled = coolingController?.enabled,
                fanPercent = fanPercent,
                currentTemperature = coolingController?.currentTemperatureCelsius
                    ?: primaryTemperature
            ),
            coolingFansText = fanPercent?.let { percent ->
                "$percent%"
            } ?: if (snapshot.fanPwmChannels.isEmpty()) {
                "No fan channel"
            } else {
                "Standby"
            },
            coolingMode = if (coolingController?.enabled == true) {
                "Auto cooling"
            } else {
                "Cooling disabled"
            },
            coolingModeEnabled = coolingController?.enabled == true,
            coolingFanCount = snapshot.fanPwmChannels.size,
            fanStartTemperatureCelsius = coolingController?.startCelsius?.roundToInt()
                ?: DEFAULT_FAN_START_CELSIUS,
            fanFullSpeedTemperatureCelsius = coolingController?.fullSpeedCelsius?.roundToInt()
                ?: DEFAULT_FAN_FULL_SPEED_CELSIUS,
            isDeviceOnline = runtimeState.isDeviceOnline,
            controlsEnabled = false,
            connectionStatusText = connectionStatus
        )
    }

    private fun unavailableState(
        reason: String,
        device: DevicesDataStoreManager.DeviceInfo? = null
    ): DeviceLightSettingsUiState {
        return DeviceLightSettingsUiState(
            deviceName = device?.resolvedTitle ?: "—",
            deviceType = device?.productModel.nonBlank() ?: "Light Controller",
            firmwareVersion = device?.firmwareVersion.nonBlank()
                ?: device?.firmwareBuild.nonBlank()
                ?: "—",
            lastKnownIpText = device?.ip.nonBlank() ?: "—",
            serialNumber = device?.serialNumber.nonBlank()
                ?: device?.serial.nonBlank()
                ?: "—",
            productId = device?.productId.orEmpty(),
            productKey = device?.productKey?.name.orEmpty().takeUnless { it == "UNKNOWN" }.orEmpty(),
            skuCode = device?.skuCode.orEmpty(),
            setupCode = device?.setupCode.orEmpty(),
            deviceUid = device?.deviceUid.orEmpty(),
            macAddress = device?.macAddress.orEmpty(),
            hardwareRevision = device?.hardwareRevision.orEmpty(),
            protocolVersion = device?.protocolVersion?.toString()
                ?: device?.apiVersion?.toString()
                ?: "",
            deviceTime = "--:--",
            phoneTime = currentClockText(),
            lastSyncTime = "Never",
            thermalProtectionStatusText = reason,
            coolingStatusText = reason,
            coolingMode = "Unavailable",
            isDeviceOnline = false,
            controlsEnabled = false,
            connectionStatusText = reason
        )
    }

    private fun thermalStatusText(
        limitCelsius: Double?,
        reductionPercent: Int?
    ): String {
        return when {
            reductionPercent != null && reductionPercent > 0 -> {
                "Thermal reduction active · $reductionPercent%"
            }
            limitCelsius != null -> {
                "Protected · limit ${limitCelsius.roundToInt()} °C"
            }
            else -> {
                "Protected"
            }
        }
    }

    private fun coolingStatusText(
        enabled: Boolean?,
        fanPercent: Int?,
        currentTemperature: Double?
    ): String {
        val temperature = currentTemperature?.let(::formatTemperature) ?: "-- °C"
        return when {
            enabled == false -> "Cooling disabled · $temperature"
            fanPercent != null && fanPercent > 0 -> "Cooling active · $fanPercent% · $temperature"
            enabled == true -> "Cooling standby · $temperature"
            else -> "Cooling data unavailable"
        }
    }

    private fun formatTemperature(
        value: Double?
    ): String {
        val temperature = value ?: return "-- °C"
        val rounded = (temperature * 10.0).roundToInt() / 10.0
        val roundedInt = rounded.roundToInt()

        return if (kotlin.math.abs(rounded - roundedInt) < 0.05) {
            "$roundedInt °C"
        } else {
            String.format(
                Locale.US,
                "%.1f °C",
                rounded
            )
        }
    }

    private fun String?.nonBlank(): String? {
        return this?.trim()?.takeIf { value ->
            value.isNotEmpty()
        }
    }

    private fun currentClockText(): String {
        return SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date())
    }

    private fun formatSyncTime(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date(millis))
    }

    private fun emitReadOnlyWarning() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightSettingsEvent.ShowWarning(
                    "Live settings are read-only on the current controller firmware."
                )
            )
        }
    }

    override fun onCleared() {
        runtimeCollectorJob?.cancel()
        runtimeSession?.release(consumerKey)
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_LIMIT_CELSIUS = 50
        private const val DEFAULT_LIGHT_REDUCTION_PERCENT = 70
        private const val DEFAULT_RECOVERY_INTERVAL_SECONDS = 60
        private const val DEFAULT_FAN_START_CELSIUS = 30
        private const val DEFAULT_FAN_FULL_SPEED_CELSIUS = 50
    }
}
