package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
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

    private val _uiState = MutableStateFlow(readyState())
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
            heroTitle = string(R.string.device_add_bluetooth_permission_title),
            heroSubtitle = string(R.string.device_add_bluetooth_permission_message),
            scanBadge = string(R.string.device_add_scan_badge_permission),
            emptyTitle = string(R.string.device_add_bluetooth_permission_empty_title),
            emptyMessage = string(R.string.device_add_bluetooth_permission_empty_message)
        )
    }

    fun startBleScan() {
        scanCollectJob?.cancel()
        scanTimeoutJob?.cancel()

        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.SCANNING,
            heroTitle = string(R.string.device_add_scanning_title),
            heroSubtitle = string(R.string.device_add_scanning_message),
            scanBadge = string(R.string.device_add_scan_badge_scanning),
            candidates = emptyList(),
            emptyTitle = string(R.string.device_add_scanning_empty_title),
            emptyMessage = string(R.string.device_add_scanning_empty_message)
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
                        emptyTitle = string(R.string.device_add_scanning_empty_title),
                        emptyMessage = string(R.string.device_add_scanning_empty_message)
                    )
                } else {
                    _uiState.value = DeviceAddUiState(
                        mode = DeviceAddScanMode.RESULTS,
                        heroTitle = if (uiCandidates.size == 1) {
                            string(R.string.device_add_result_title_single, uiCandidates.size)
                        } else {
                            string(R.string.device_add_result_title_multi, uiCandidates.size)
                        },
                        heroSubtitle = string(R.string.device_add_result_message),
                        scanBadge = string(R.string.device_add_scan_badge_nearby),
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
                    heroTitle = string(R.string.device_add_no_device_title),
                    heroSubtitle = string(R.string.device_add_no_device_message),
                    scanBadge = string(R.string.device_add_scan_badge_ready),
                    candidates = emptyList(),
                    emptyTitle = string(R.string.device_add_no_nearby_title),
                    emptyMessage = string(R.string.device_add_no_nearby_message)
                )
            }
        }
    }

    private fun showBluetoothOff() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.BLUETOOTH_OFF,
            heroTitle = string(R.string.device_add_bluetooth_off_title),
            heroSubtitle = string(R.string.device_add_bluetooth_off_message),
            scanBadge = string(R.string.device_add_scan_badge_bluetooth),
            emptyTitle = string(R.string.device_add_bluetooth_off_title),
            emptyMessage = string(R.string.device_add_bluetooth_off_empty_message)
        )
    }

    private fun showBluetoothUnavailable() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = string(R.string.device_add_bluetooth_unavailable_title),
            heroSubtitle = string(R.string.device_add_bluetooth_unavailable_message),
            scanBadge = string(R.string.device_add_scan_badge_unavailable),
            emptyTitle = string(R.string.device_add_bluetooth_unavailable_title),
            emptyMessage = string(R.string.device_add_bluetooth_unavailable_empty_message)
        )
    }

    private fun showBleError(message: String) {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = string(R.string.device_add_scan_failed_title),
            heroSubtitle = string(R.string.device_add_scan_failed_message),
            scanBadge = string(R.string.device_add_scan_badge_error),
            emptyTitle = string(R.string.device_add_scan_failed_empty_title),
            emptyMessage = message.ifBlank { string(R.string.device_add_scan_failed_fallback) }
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
            add(string(R.string.device_add_setup_mode_label))
            add(string(R.string.device_add_rssi_format, rssi))
        }.joinToString(separator = " • ")

        return DeviceAddCandidateUi(
            id = deviceUid.ifBlank { address },
            title = displayTitle,
            serial = displaySerial,
            model = modelLabel,
            status = displayStatus.ifBlank { string(R.string.device_add_status_ready) },
            rssiLabel = "$rssi dBm",
            bleAddress = address,
            bleName = name
        )
    }

    private fun readyState(): DeviceAddUiState {
        return DeviceAddUiState(
            mode = DeviceAddScanMode.READY,
            heroTitle = string(R.string.device_add_scan_title_ready),
            heroSubtitle = string(R.string.device_add_scan_subtitle_ready),
            scanBadge = string(R.string.device_add_scan_badge_ready),
            candidates = emptyList(),
            emptyTitle = string(R.string.device_add_scan_badge_ready),
            emptyMessage = string(R.string.device_add_scanning_empty_message)
        )
    }

    private fun string(
        @StringRes resId: Int,
        vararg args: Any
    ): String {
        return getApplication<Application>().getString(resId, *args)
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
    val heroTitle: String = "",
    val heroSubtitle: String = "",
    val scanBadge: String = "",
    val candidates: List<DeviceAddCandidateUi> = emptyList(),
    val emptyTitle: String = "",
    val emptyMessage: String = ""
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
