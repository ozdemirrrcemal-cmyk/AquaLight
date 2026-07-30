package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceFamilySettingsViewModel(
    private val rootOperations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFamilySettingsUiState())
    val uiState: StateFlow<DeviceFamilySettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var observeJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = DeviceFamilySettingsUiState()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = rootOperations.current(deviceUid)
            ?.toDeviceFamilySettingsUiState()
            ?: DeviceFamilySettingsUiState(serialNumber = deviceUid)
        rootOperations.connect(deviceUid)
        observeJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toDeviceFamilySettingsUiState()
                    ?: DeviceFamilySettingsUiState(serialNumber = deviceUid)
            }
        }
    }
}

data class DeviceFamilySettingsUiState(
    val deviceName: String = "",
    val serialNumber: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val showLightProtectionInventory: Boolean = false
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

private const val WRGB_PRO_ELITE_MODEL = "wrgb_pro_elite_120"
private const val LIGHT_TEMPERATURE_PROTECTION_FEATURE = "LIGHT_TEMPERATURE_PROTECTION"
