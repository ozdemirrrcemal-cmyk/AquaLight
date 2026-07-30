package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation owner for the shared family Settings screen.
 *
 * Device-name persistence and firmware-update operations are intentionally not connected here.
 * They will be attached after their Android data contracts are completed and verified.
 */
class DeviceFamilySettingsViewModel(
    private val rootOperations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFamilySettingsUiState())
    val uiState: StateFlow<DeviceFamilySettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var observeDeviceJob: Job? = null
    private var updatePreviewJob: Job? = null
    private var localDeviceNameOverride: String? = null

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
        _uiState.value = rootOperations.current(deviceUid)
            .toInitialDeviceFamilySettingsUiState(deviceUid)
        rootOperations.connect(deviceUid)

        observeDeviceJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                applyDeviceSnapshot(deviceUid, snapshot)
            }
        }
    }

    /** Updates only the current screen preview. Persistence is connected in the later data phase. */
    fun previewDeviceName(value: String) {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized == _uiState.value.deviceName.trim()) return

        localDeviceNameOverride = normalized
        _uiState.update { state -> state.copy(deviceName = normalized) }
    }

    /**
     * Runs the approved button interaction without touching OTA repositories or firmware commands.
     * The data phase will replace this preview transition with verified availability results.
     */
    fun previewUpdateCheck() {
        if (boundDeviceUid.isBlank() || updatePreviewJob?.isActive == true) return
        if (_uiState.value.updateActionState is DeviceSettingsUpdateActionState.UpdateAvailable) return

        updatePreviewJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(updateActionState = DeviceSettingsUpdateActionState.Checking)
            }
            delay(UPDATE_CHECK_PREVIEW_DURATION_MILLIS)
            _uiState.update { state ->
                if (state.updateActionState == DeviceSettingsUpdateActionState.Checking) {
                    state.copy(updateActionState = DeviceSettingsUpdateActionState.UpToDate)
                } else {
                    state
                }
            }
            delay(UP_TO_DATE_ACTION_DURATION_MILLIS)
            _uiState.update { state ->
                if (state.updateActionState == DeviceSettingsUpdateActionState.UpToDate) {
                    state.copy(updateActionState = DeviceSettingsUpdateActionState.Idle)
                } else {
                    state
                }
            }
        }
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
        _uiState.value = DeviceFamilySettingsUiState()
    }

    private fun cancelBoundJobs() {
        observeDeviceJob?.cancel()
        updatePreviewJob?.cancel()
    }

    override fun onCleared() {
        cancelBoundJobs()
        super.onCleared()
    }

    private companion object {
        const val UPDATE_CHECK_PREVIEW_DURATION_MILLIS = 700L
        const val UP_TO_DATE_ACTION_DURATION_MILLIS = 3_000L
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
}

data class DeviceFamilySettingsUiState(
    val deviceName: String = "",
    val serialNumber: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val showLightProtectionInventory: Boolean = false,
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
