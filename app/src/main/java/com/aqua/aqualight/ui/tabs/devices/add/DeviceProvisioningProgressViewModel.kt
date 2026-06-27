package com.aqua.aqualight.ui.tabs.devices.add

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceProvisioningProgressViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceProvisioningProgressUiState())
    val uiState: StateFlow<DeviceProvisioningProgressUiState> = _uiState.asStateFlow()

    private var boundSessionId: String? = null

    fun bind(sessionId: String) {
        if (sessionId.isBlank() || boundSessionId == sessionId) {
            return
        }

        boundSessionId = sessionId

        val draft = AqlProvisioningDraftStore.get(sessionId)
        if (draft == null) {
            _uiState.value = DeviceProvisioningProgressUiState(
                title = "Provisioning session expired",
                message = "Go back and select the device again.",
                deviceName = "Unknown device",
                deviceSerial = "Unknown",
                bleAddress = "Unknown",
                wifiSsid = "Unknown",
                canStart = false
            )
            return
        }

        _uiState.value = DeviceProvisioningProgressUiState(
            title = "Ready for BLE provisioning",
            message = "The selected device and Wi-Fi credentials are prepared. The next step will connect and write them over BLE.",
            deviceName = draft.deviceTitle.ifBlank { "AquaLight Device" },
            deviceSerial = draft.deviceSerial.ifBlank { draft.candidateId },
            bleAddress = draft.bleAddress.ifBlank { "Unknown" },
            wifiSsid = draft.wifiCredentials.ssid,
            canStart = true
        )
    }

    fun startProvisioning() {
        _uiState.value = _uiState.value.copy(
            title = "BLE provisioning pending",
            message = "GATT connect/write will be enabled in the next migration step.",
            stepThree = "3. BLE provisioning engine will start next",
            canStart = false
        )
    }
}

data class DeviceProvisioningProgressUiState(
    val title: String = "Preparing provisioning",
    val message: String = "Preparing selected device and Wi-Fi credentials.",
    val deviceName: String = "",
    val deviceSerial: String = "",
    val bleAddress: String = "",
    val wifiSsid: String = "",
    val stepOne: String = "1. Device selected",
    val stepTwo: String = "2. Wi-Fi credentials prepared",
    val stepThree: String = "3. BLE provisioning connection pending",
    val canStart: Boolean = false
)
