package com.aqua.aqualight.ui.tabs.devices.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceAddViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceAddUiState())
    val uiState: StateFlow<DeviceAddUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceAddEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DeviceAddEvent> = _events.receiveAsFlow()

    private var scanJob: Job? = null

    fun onQrClicked() {
        viewModelScope.launch {
            _events.send(
                DeviceAddEvent.ShowMessage(
                    message = "QR scanner ekranı bir sonraki adımda bağlanacak."
                )
            )
        }
    }

    fun onBleScanClicked() {
        startBleScanShell()
    }

    fun onScanAgainClicked() {
        startBleScanShell()
    }

    fun onCandidateClicked(candidate: DeviceAddCandidateUi) {
        viewModelScope.launch {
            _events.send(
                DeviceAddEvent.ShowMessage(
                    message = "${candidate.title} seçildi. Wi-Fi provisioning akışı sonraki adımda bağlanacak."
                )
            )
        }
    }

    private fun startBleScanShell() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = DeviceAddUiState(
                mode = DeviceAddScanMode.SCANNING,
                heroTitle = "Scanning nearby AquaLight devices",
                heroSubtitle = "Keep the device powered on and close to your phone.",
                scanBadge = "BLE scan",
                candidates = emptyList(),
                emptyTitle = "",
                emptyMessage = ""
            )

            delay(SCAN_SHELL_DURATION_MS)

            _uiState.value = DeviceAddUiState(
                mode = DeviceAddScanMode.EMPTY,
                heroTitle = "Ready for secure provisioning",
                heroSubtitle = "No Bluetooth provisioning devices were listed yet. Real BLE scan will plug into this screen next.",
                scanBadge = "Ready",
                candidates = emptyList(),
                emptyTitle = "No devices found yet",
                emptyMessage = "Use QR setup or run BLE scan again after powering on the AquaLight device."
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val SCAN_SHELL_DURATION_MS = 1_200L
    }
}

data class DeviceAddUiState(
    val mode: DeviceAddScanMode = DeviceAddScanMode.READY,
    val heroTitle: String = "Add your AquaLight device",
    val heroSubtitle: String = "Scan a QR code or discover a nearby device over Bluetooth provisioning.",
    val scanBadge: String = "Secure setup",
    val candidates: List<DeviceAddCandidateUi> = emptyList(),
    val emptyTitle: String = "No devices found yet",
    val emptyMessage: String = "Start BLE scan or use the QR code printed on the device."
)

enum class DeviceAddScanMode {
    READY,
    SCANNING,
    RESULTS,
    EMPTY,
    ERROR
}

sealed interface DeviceAddEvent {
    data class ShowMessage(val message: String) : DeviceAddEvent
}
