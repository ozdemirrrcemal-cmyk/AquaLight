package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningCandidate
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceAddViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val bleScanner = AqlBleProvisioningScanner(application)

    private val _uiState = MutableStateFlow(DeviceAddUiState())
    val uiState: StateFlow<DeviceAddUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceAddEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DeviceAddEvent> = _events.receiveAsFlow()

    private var scanCollectJob: Job? = null
    private var scanTimeoutJob: Job? = null

    fun onQrClicked() {
        viewModelScope.launch {
            _events.send(
                DeviceAddEvent.ShowMessage(
                    message = "QR scanner ekranı bir sonraki adımda bağlanacak."
                )
            )
        }
    }

    fun onBlePermissionDenied() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.PERMISSION_REQUIRED,
            heroTitle = "Bluetooth permission required",
            heroSubtitle = "BLE provisioning scan needs permission before nearby AquaLight devices can be discovered.",
            scanBadge = "Permission",
            emptyTitle = "Permission required",
            emptyMessage = "Allow Bluetooth permission to scan for nearby setup devices."
        )
    }

    fun startBleScan() {
        scanCollectJob?.cancel()
        scanTimeoutJob?.cancel()

        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.SCANNING,
            heroTitle = "Scanning nearby AquaLight devices",
            heroSubtitle = "Keep the device powered on and close to your phone.",
            scanBadge = "BLE scan",
            candidates = emptyList(),
            emptyTitle = "Scanning...",
            emptyMessage = "Searching for AquaLight provisioning devices."
        )

        when (val result = bleScanner.startScan()) {
            AqlBleProvisioningScanner.StartResult.Started -> {
                observeBleCandidates()
                startScanTimeout()
            }

            AqlBleProvisioningScanner.StartResult.MissingPermission -> {
                onBlePermissionDenied()
            }

            AqlBleProvisioningScanner.StartResult.BluetoothOff -> {
                showBluetoothOff()
            }

            AqlBleProvisioningScanner.StartResult.BluetoothUnavailable -> {
                showBluetoothUnavailable()
            }

            is AqlBleProvisioningScanner.StartResult.Failed -> {
                showBleError(result.message)
            }
        }
    }

    fun onScanAgainClicked() {
        startBleScan()
    }

    fun onCandidateClicked(candidate: DeviceAddCandidateUi) {
        stopBleScan()
        viewModelScope.launch {
            _events.send(
                DeviceAddEvent.ShowMessage(
                    message = "${candidate.title} seçildi. Wi-Fi provisioning akışı sonraki adımda bağlanacak."
                )
            )
        }
    }

    private fun observeBleCandidates() {
        scanCollectJob = viewModelScope.launch {
            bleScanner.candidates.collect { candidates ->
                val uiCandidates = candidates.map { candidate ->
                    candidate.toUi()
                }

                if (uiCandidates.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        mode = DeviceAddScanMode.SCANNING,
                        candidates = emptyList(),
                        emptyTitle = "Scanning...",
                        emptyMessage = "Searching for AquaLight provisioning devices."
                    )
                } else {
                    _uiState.value = DeviceAddUiState(
                        mode = DeviceAddScanMode.RESULTS,
                        heroTitle = "${uiCandidates.size} device found",
                        heroSubtitle = "Select the device you want to provision securely.",
                        scanBadge = "Nearby",
                        candidates = uiCandidates,
                        emptyTitle = "",
                        emptyMessage = ""
                    )
                }
            }
        }
    }

    private fun startScanTimeout() {
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)

            val hasCandidates = _uiState.value.candidates.isNotEmpty()
            if (!hasCandidates) {
                bleScanner.stopScan()
                _uiState.value = DeviceAddUiState(
                    mode = DeviceAddScanMode.EMPTY,
                    heroTitle = "Ready for secure provisioning",
                    heroSubtitle = "No Bluetooth provisioning devices were found yet.",
                    scanBadge = "Ready",
                    candidates = emptyList(),
                    emptyTitle = "No devices found",
                    emptyMessage = "Power on the AquaLight device, keep it nearby, then scan again. You can also use QR setup."
                )
            }
        }
    }

    private fun showBluetoothOff() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.BLUETOOTH_OFF,
            heroTitle = "Bluetooth is off",
            heroSubtitle = "Turn on Bluetooth to discover AquaLight setup devices nearby.",
            scanBadge = "Bluetooth",
            emptyTitle = "Bluetooth is disabled",
            emptyMessage = "Enable Bluetooth from your phone settings, then run the scan again."
        )
    }

    private fun showBluetoothUnavailable() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = "Bluetooth unavailable",
            heroSubtitle = "This device cannot start BLE provisioning scan.",
            scanBadge = "Unavailable",
            emptyTitle = "Bluetooth unavailable",
            emptyMessage = "BLE provisioning cannot run on this phone right now."
        )
    }

    private fun showBleError(message: String) {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = "Scan failed",
            heroSubtitle = "The BLE scan could not be started.",
            scanBadge = "Error",
            emptyTitle = "Scan failed",
            emptyMessage = message.ifBlank { "BLE scan failed. Try again." }
        )
    }

    private fun stopBleScan() {
        scanCollectJob?.cancel()
        scanCollectJob = null
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        bleScanner.stopScan()
    }

    private fun AqlBleProvisioningCandidate.toUi(): DeviceAddCandidateUi {
        val modelLabel = buildList {
            if (model.isNotBlank()) add(model)
            add("BLE provisioning")
            add("RSSI $rssi dBm")
        }.joinToString(separator = " • ")

        return DeviceAddCandidateUi(
            id = deviceUid.ifBlank { address },
            title = displayTitle,
            serial = displaySerial,
            model = modelLabel,
            status = displayStatus,
            rssiLabel = "$rssi dBm"
        )
    }

    override fun onCleared() {
        stopBleScan()
        super.onCleared()
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
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
    PERMISSION_REQUIRED,
    BLUETOOTH_OFF,
    ERROR
}

sealed interface DeviceAddEvent {
    data class ShowMessage(val message: String) : DeviceAddEvent
}
