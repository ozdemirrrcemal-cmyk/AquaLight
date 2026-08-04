package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation owner for the shared family Settings screen.
 *
 * Device-name and thermal-threshold persistence remain separate data-stage follow-ups. OTA
 * availability and runtime progress use the owner-scoped commercial coordinator shared with the
 * full-screen update destination.
 */
class DeviceFamilySettingsViewModel(
    private val rootOperations: DeviceRootOperations,
    private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations,
    private val manifestUrl: String = DEVICE_FIRMWARE_MANIFEST_URL
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFamilySettingsUiState())
    val uiState: StateFlow<DeviceFamilySettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var observeDeviceJob: Job? = null
    private var observeFirmwareJob: Job? = null
    private var updateCheckJob: Job? = null
    private var localDeviceNameOverride: String? = null
    private var localTemperatureProtectionThresholdOverride: Int? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            reset()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelBoundJobs()
        localDeviceNameOverride = null
        localTemperatureProtectionThresholdOverride = null
        _uiState.value = rootOperations.current(deviceUid)
            .toInitialDeviceFamilySettingsUiState(deviceUid)
        rootOperations.connect(deviceUid)

        observeDeviceJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                applyDeviceSnapshot(deviceUid, snapshot)
            }
        }
        observeFirmwareJob = viewModelScope.launch {
            firmwareUpdateOperations.observe(deviceUid).collect(::applyFirmwareState)
        }
    }

    /** Updates only the current screen preview. Persistence is connected in the data phase. */
    fun previewDeviceName(value: String) {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized == _uiState.value.deviceName.trim()) return

        localDeviceNameOverride = normalized
        _uiState.update { state -> state.copy(deviceName = normalized) }
    }

    /** Updates only the bounded screen preview. Persistence is connected in the data phase. */
    fun previewTemperatureProtectionThreshold(value: Int) {
        if (!_uiState.value.showLightProtectionInventory) return
        if (!LightTemperatureProtectionUiContract.isAllowedThreshold(value)) return

        localTemperatureProtectionThresholdOverride = value
        _uiState.update { state ->
            state.copy(temperatureProtectionThresholdC = value)
        }
    }

    fun checkForUpdates() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank() || updateCheckJob?.isActive == true) return
        if (_uiState.value.updateActionState is DeviceSettingsUpdateActionState.UpdateInProgress) {
            return
        }

        updateCheckJob = viewModelScope.launch {
            val availability = firmwareUpdateOperations.checkAvailability(
                deviceUid = deviceUid,
                manifestUrl = manifestUrl,
                applyNow = true
            ).getOrNull()
            if (availability is DeviceOtaState.UpdateAvailable) {
                // The status probe can recover a transfer that continued without Android. The
                // coordinator keeps this signed availability state if that probe itself fails.
                firmwareUpdateOperations.requestStatus(deviceUid)
            }
        }
    }

    private fun applyFirmwareState(state: DeviceOtaState) {
        if (state.deviceUid != boundDeviceUid) return
        val actionState = when (state) {
            is DeviceOtaState.Idle -> DeviceSettingsUpdateActionState.Idle
            is DeviceOtaState.Checking -> DeviceSettingsUpdateActionState.Checking
            is DeviceOtaState.Unsupported -> DeviceSettingsUpdateActionState.Unsupported
            is DeviceOtaState.UpToDate,
            is DeviceOtaState.Succeeded -> DeviceSettingsUpdateActionState.UpToDate
            is DeviceOtaState.UpdateAvailable -> DeviceSettingsUpdateActionState.UpdateAvailable(
                state.plan.targetVersion
            )
            is DeviceOtaState.Starting -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.plan.targetVersion,
                progressPermille = 0
            )
            is DeviceOtaState.InProgress -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.targetVersion,
                progressPermille = state.progressPermille
            )
            is DeviceOtaState.Recovering -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.targetVersion,
                progressPermille = state.progressPermille
            )
            is DeviceOtaState.RestartRequired -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.targetVersion,
                progressPermille = COMPLETE_PROGRESS_PERMILLE
            )
            is DeviceOtaState.Failed -> DeviceSettingsUpdateActionState.Failed(state.failure)
        }
        _uiState.update { current -> current.copy(updateActionState = actionState) }
    }

    private fun applyDeviceSnapshot(deviceUid: String, snapshot: DeviceRootSnapshot?) {
        if (snapshot == null || snapshot.catalogState != DeviceRootCatalogState.VALID) {
            preserveStableDeviceInformation(deviceUid, snapshot)
            return
        }

        val previous = _uiState.value
        val deviceState = snapshot.toDeviceFamilySettingsUiState()
        _uiState.value = deviceState.copy(
            deviceName = localDeviceNameOverride ?: deviceState.deviceName,
            temperatureProtectionThresholdC =
                localTemperatureProtectionThresholdOverride
                    ?: deviceState.temperatureProtectionThresholdC,
            updateActionState = previous.updateActionState,
            informationLoadState = DeviceSettingsInformationLoadState.READY
        )
    }

    private fun preserveStableDeviceInformation(
        deviceUid: String,
        snapshot: DeviceRootSnapshot?
    ) {
        _uiState.update { current ->
            current.copy(
                deviceName = localDeviceNameOverride
                    ?: current.deviceName.ifBlank { snapshot?.title.orEmpty() },
                serialNumber = current.serialNumber.ifBlank {
                    snapshot?.serialNumber?.ifBlank { deviceUid } ?: deviceUid
                },
                firmwareVersion = current.firmwareVersion.ifBlank {
                    snapshot?.firmwareLabel.orEmpty()
                },
                informationLoadState = if (current.hardwareRevision.isNotBlank()) {
                    DeviceSettingsInformationLoadState.READY
                } else {
                    DeviceSettingsInformationLoadState.LOADING
                }
            )
        }
    }

    private fun reset() {
        cancelBoundJobs()
        boundDeviceUid = ""
        localDeviceNameOverride = null
        localTemperatureProtectionThresholdOverride = null
        _uiState.value = DeviceFamilySettingsUiState()
    }

    private fun cancelBoundJobs() {
        observeDeviceJob?.cancel()
        observeFirmwareJob?.cancel()
        updateCheckJob?.cancel()
    }

    override fun onCleared() {
        cancelBoundJobs()
        super.onCleared()
    }

    private companion object {
        const val COMPLETE_PROGRESS_PERMILLE = 1_000
    }
}

enum class DeviceSettingsInformationLoadState {
    LOADING,
    READY
}

sealed interface DeviceSettingsUpdateActionState {
    data object Idle : DeviceSettingsUpdateActionState
    data object Checking : DeviceSettingsUpdateActionState
    data object UpToDate : DeviceSettingsUpdateActionState

    data class UpdateAvailable(
        val version: String
    ) : DeviceSettingsUpdateActionState

    data class UpdateInProgress(
        val version: String,
        val progressPermille: Int
    ) : DeviceSettingsUpdateActionState

    data class Failed(
        val failure: DeviceOtaFailure
    ) : DeviceSettingsUpdateActionState

    data object Unsupported : DeviceSettingsUpdateActionState
}

data class DeviceFamilySettingsUiState(
    val deviceName: String = "",
    val serialNumber: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val showLightProtectionInventory: Boolean = false,
    val temperatureProtectionThresholdC: Int =
        LightTemperatureProtectionUiContract.DEFAULT_THRESHOLD_C,
    val informationLoadState: DeviceSettingsInformationLoadState =
        DeviceSettingsInformationLoadState.LOADING,
    val updateActionState: DeviceSettingsUpdateActionState =
        DeviceSettingsUpdateActionState.Idle
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
        showLightProtectionInventory = supportsLightProtection,
        informationLoadState = if (
            catalogState == DeviceRootCatalogState.VALID && hardwareRevision.isNotBlank()
        ) {
            DeviceSettingsInformationLoadState.READY
        } else {
            DeviceSettingsInformationLoadState.LOADING
        }
    )
}

private fun DeviceRootSnapshot?.toInitialDeviceFamilySettingsUiState(
    deviceUid: String
): DeviceFamilySettingsUiState {
    return this?.toDeviceFamilySettingsUiState()
        ?: DeviceFamilySettingsUiState(serialNumber = deviceUid)
}

private const val WRGB_PRO_ELITE_MODEL = "wrgb_pro_elite_120"
private const val LIGHT_TEMPERATURE_PROTECTION_FEATURE = "LIGHT_TEMPERATURE_PROTECTION"
