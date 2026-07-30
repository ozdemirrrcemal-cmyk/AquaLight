package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSettingsOperations
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceFamilySettingsViewModel(
    private val rootOperations: DeviceRootOperations,
    private val settingsOperations: DeviceSettingsOperations,
    private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFamilySettingsUiState())
    val uiState: StateFlow<DeviceFamilySettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceFamilySettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DeviceFamilySettingsEvent> = _events.asSharedFlow()

    private var boundDeviceUid = ""
    private var observeDeviceJob: Job? = null
    private var observeOtaJob: Job? = null
    private var nameUpdateJob: Job? = null
    private var availabilityCheckJob: Job? = null
    private var upToDateActionJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            cancelBoundJobs()
            boundDeviceUid = ""
            _uiState.value = DeviceFamilySettingsUiState()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelBoundJobs()
        _uiState.value = rootOperations.current(deviceUid)
            ?.toDeviceFamilySettingsUiState()
            ?.copy(otaState = DeviceOtaState.Idle(deviceUid))
            ?: DeviceFamilySettingsUiState(
                serialNumber = deviceUid,
                otaState = DeviceOtaState.Idle(deviceUid)
            )
        rootOperations.connect(deviceUid)

        observeDeviceJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                val previous = _uiState.value
                val deviceState = snapshot?.toDeviceFamilySettingsUiState()
                    ?: DeviceFamilySettingsUiState(serialNumber = deviceUid)
                _uiState.value = deviceState.copy(
                    isSavingDeviceName = previous.isSavingDeviceName,
                    otaState = previous.otaState,
                    showUpToDateAction = previous.showUpToDateAction
                )
            }
        }
        observeOtaJob = viewModelScope.launch {
            firmwareUpdateOperations.observe(deviceUid).collect(::applyOtaState)
        }
    }

    fun updateDeviceName(value: String) {
        val deviceUid = boundDeviceUid
        val normalized = value.trim()
        val currentName = _uiState.value.deviceName.trim()
        val canUpdate = listOf(
            deviceUid.isNotBlank(),
            normalized.isNotBlank(),
            normalized != currentName,
            nameUpdateJob?.isActive != true
        ).all { condition -> condition }
        if (!canUpdate) return

        nameUpdateJob = viewModelScope.launch {
            _uiState.update { state -> state.copy(isSavingDeviceName = true) }
            settingsOperations.updateCustomName(deviceUid, normalized).fold(
                onSuccess = {
                    _events.emit(DeviceFamilySettingsEvent.DeviceNameUpdated)
                },
                onFailure = {
                    _events.emit(DeviceFamilySettingsEvent.DeviceNameUpdateFailed)
                }
            )
            _uiState.update { state -> state.copy(isSavingDeviceName = false) }
        }
    }

    fun checkForUpdates() {
        val deviceUid = boundDeviceUid
        if (
            deviceUid.isBlank() ||
            availabilityCheckJob?.isActive == true ||
            _uiState.value.otaState.blocksAvailabilityCheck
        ) {
            return
        }

        availabilityCheckJob = viewModelScope.launch {
            firmwareUpdateOperations.checkAvailability(
                deviceUid = deviceUid,
                manifestUrl = DEVICE_FIRMWARE_MANIFEST_URL,
                applyNow = true
            )
        }
    }

    private fun applyOtaState(state: DeviceOtaState) {
        upToDateActionJob?.cancel()
        _uiState.update { current ->
            current.copy(
                otaState = state,
                showUpToDateAction = state is DeviceOtaState.UpToDate
            )
        }
        if (state is DeviceOtaState.UpToDate) {
            upToDateActionJob = viewModelScope.launch {
                delay(UP_TO_DATE_ACTION_DURATION_MILLIS)
                _uiState.update { current ->
                    if (current.otaState == state) {
                        current.copy(showUpToDateAction = false)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun cancelBoundJobs() {
        observeDeviceJob?.cancel()
        observeOtaJob?.cancel()
        nameUpdateJob?.cancel()
        availabilityCheckJob?.cancel()
        upToDateActionJob?.cancel()
    }

    override fun onCleared() {
        cancelBoundJobs()
        firmwareUpdateOperations.close()
        super.onCleared()
    }

    private companion object {
        const val UP_TO_DATE_ACTION_DURATION_MILLIS = 2_500L
    }
}

sealed interface DeviceFamilySettingsEvent {
    data object DeviceNameUpdated : DeviceFamilySettingsEvent
    data object DeviceNameUpdateFailed : DeviceFamilySettingsEvent
}

data class DeviceFamilySettingsUiState(
    val deviceName: String = "",
    val serialNumber: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val showLightProtectionInventory: Boolean = false,
    val isSavingDeviceName: Boolean = false,
    val otaState: DeviceOtaState = DeviceOtaState.Idle(""),
    val showUpToDateAction: Boolean = false
)

internal fun DeviceRootSnapshot.toDeviceFamilySettingsUiState(): DeviceFamilySettingsUiState {
    val supportsLightProtection =
        catalogState == DeviceRootCatalogState.VALID &&
            family == OwnerDeviceFamily.LIGHT &&
            model == WRGB_PRO_ELITE_MODEL &&
            temperatureSensorCount > 0 &&
            LIGHT_TEMPERATURE_PROTECTION_FEATURE in supportedFeatures

    return DeviceFamilySettingsUiState(
        deviceName = title,
        serialNumber = serialNumber.ifBlank { deviceUid },
        hardwareRevision = hardwareRevision,
        firmwareVersion = firmwareLabel,
        family = family,
        showLightProtectionInventory = supportsLightProtection
    )
}

private val DeviceOtaState.blocksAvailabilityCheck: Boolean
    get() = this is DeviceOtaState.Checking ||
        this is DeviceOtaState.Starting ||
        this is DeviceOtaState.InProgress ||
        this is DeviceOtaState.Recovering

private const val WRGB_PRO_ELITE_MODEL = "wrgb_pro_elite_120"
private const val LIGHT_TEMPERATURE_PROTECTION_FEATURE = "LIGHT_TEMPERATURE_PROTECTION"
