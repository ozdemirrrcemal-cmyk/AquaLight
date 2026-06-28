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
            _events.send(DeviceAddEvent.OpenQrScanner)
        }
    }

    fun onBlePermissionDenied() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.PERMISSION_REQUIRED,
            heroTitle = "Bluetooth permission required",
            heroSubtitle = "Allow Bluetooth access to find nearby AquaLight devices in setup mode.",
            scanBadge = "Permission",
            emptyTitle = "Permission required",
            emptyMessage = "After Bluetooth permission is granted, press Scan to search for nearby devices."
        )
    }

    fun startBleScan() {
        scanCollectJob?.cancel()
        scanTimeoutJob?.cancel()

        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.SCANNING,
            heroTitle = "Searching nearby devices",
            heroSubtitle = "Keep the device close to your phone. AquaLight devices in setup mode will appear here.",
            scanBadge = "Scanning",
            candidates = emptyList(),
            emptyTitle = "Scanning...",
            emptyMessage = "Searching for AquaLight devices in setup mode."
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
                DeviceAddEvent.OpenWifiProvisioning(
                    candidate = candidate
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
                        emptyMessage = "Searching for AquaLight devices in setup mode."
                    )
                } else {
                    _uiState.value = DeviceAddUiState(
                        mode = DeviceAddScanMode.RESULTS,
                        heroTitle = "${uiCandidates.size} device found",
                        heroSubtitle = "Select the AquaLight device you want to set up.",
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
                    heroTitle = "No device found",
                    heroSubtitle = "Make sure the device is powered on. For a previously paired device, hold the setup button for 5 seconds and try again.",
                    scanBadge = "Ready",
                    candidates = emptyList(),
                    emptyTitle = "No nearby devices",
                    emptyMessage = "Put the device in setup mode, keep it close to your phone, then press Scan again. QR setup is available from the top-right icon."
                )
            }
        }
    }

    private fun showBluetoothOff() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.BLUETOOTH_OFF,
            heroTitle = "Bluetooth is off",
            heroSubtitle = "Turn on Bluetooth to find nearby AquaLight devices.",
            scanBadge = "Bluetooth",
            emptyTitle = "Bluetooth is off",
            emptyMessage = "Turn on Bluetooth in your phone settings, then scan again."
        )
    }

    private fun showBluetoothUnavailable() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = "Bluetooth unavailable",
            heroSubtitle = "BLE setup scanning could not be started on this phone.",
            scanBadge = "Unavailable",
            emptyTitle = "Bluetooth unavailable",
            emptyMessage = "This phone is not ready for BLE device setup right now."
        )
    }

    private fun showBleError(message: String) {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = "Scan could not start",
            heroSubtitle = "A problem occurred while starting Bluetooth scanning.",
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
            add("Setup mode")
            add("RSSI $rssi dBm")
        }.joinToString(separator = " • ")

        return DeviceAddCandidateUi(
            id = deviceUid.ifBlank { address },
            title = displayTitle,
            serial = displaySerial,
            model = modelLabel,
            status = displayStatus.ifBlank { "Ready" },
            rssiLabel = "$rssi dBm",
            bleAddress = address,
            bleName = name
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
    val heroTitle: String = "Find nearby AquaLight devices",
    val heroSubtitle: String = "Press Scan when your device is in setup mode. Use the QR icon in the top-right for QR setup.",
    val scanBadge: String = "Ready",
    val candidates: List<DeviceAddCandidateUi> = emptyList(),
    val emptyTitle: String = "Ready to scan",
    val emptyMessage: String = "AquaLight devices in setup mode will appear here after scanning."
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

    object OpenQrScanner : DeviceAddEvent

    data class OpenWifiProvisioning(
        val candidate: DeviceAddCandidateUi
    ) : DeviceAddEvent
}
