package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningCandidate
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningScanner
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrParser
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrPayload
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DeviceQrScanViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val qrParser = AqlProvisioningQrParser()
    private val bleScanner = AqlBleProvisioningScanner(application)
    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceQrScanUiState())
    val uiState: StateFlow<DeviceQrScanUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceQrScanEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DeviceQrScanEvent> = _events.receiveAsFlow()

    private var pendingPayload: AqlProvisioningQrPayload? = null
    private var scanJob: Job? = null

    fun onQrDetected(
        rawValue: String,
        hasBlePermissions: Boolean
    ) {
        if (scanJob?.isActive == true) return

        val payload = qrParser.parse(rawValue)
            .getOrElse {
                pendingPayload = null
                showFailure(
                    titleRes = R.string.device_qr_preflight_invalid_title,
                    messageRes = R.string.device_qr_preflight_invalid_message
                )
                return
            }

        if (repository.currentDevice(payload.deviceUid) != null) {
            pendingPayload = null
            showFailure(
                titleRes = R.string.device_qr_preflight_already_added_title,
                messageRes = R.string.device_qr_preflight_already_added_message
            )
            return
        }

        pendingPayload = payload

        if (!hasBlePermissions) {
            _uiState.value = DeviceQrScanUiState(
                title = string(R.string.device_qr_preflight_bluetooth_permission_title),
                message = string(R.string.device_qr_preflight_bluetooth_permission_message),
                primaryAction = DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION
            )
            return
        }

        startQrBleScan(payload)
    }

    fun onBlePermissionResult(
        granted: Boolean,
        permanentlyDenied: Boolean = false
    ) {
        val payload = pendingPayload

        if (!granted || payload == null) {
            showFailure(
                titleRes = R.string.device_qr_preflight_bluetooth_permission_title,
                messageRes = R.string.device_qr_preflight_bluetooth_permission_message,
                primaryAction = if (permanentlyDenied) {
                    DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS
                } else {
                    DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION
                }
            )
            return
        }

        startQrBleScan(payload)
    }

    fun retryPendingBleScan(): Boolean {
        val payload = pendingPayload ?: return false
        startQrBleScan(payload)
        return true
    }

    fun onScanAgain() {
        scanJob?.cancel()
        scanJob = null
        bleScanner.stopScan()
        bleScanner.clearCandidates()
        pendingPayload = null
        _uiState.value = DeviceQrScanUiState()
    }

    private fun startQrBleScan(payload: AqlProvisioningQrPayload) {
        pendingPayload = payload
        scanJob?.cancel()
        bleScanner.stopScan()
        bleScanner.clearCandidates()

        _uiState.value = DeviceQrScanUiState(
            title = string(R.string.device_qr_preflight_checking_title),
            message = string(R.string.device_qr_preflight_checking_message),
            primaryAction = null
        )

        scanJob = viewModelScope.launch {
            when (val result = bleScanner.startScan()) {
                AqlBleProvisioningScanner.StartResult.Started -> Unit
                AqlBleProvisioningScanner.StartResult.MissingPermission -> {
                    showFailure(
                        titleRes = R.string.device_qr_preflight_bluetooth_permission_title,
                        messageRes = R.string.device_qr_preflight_bluetooth_permission_message,
                        primaryAction = DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION
                    )
                    return@launch
                }

                AqlBleProvisioningScanner.StartResult.BluetoothOff -> {
                    showFailure(
                        titleRes = R.string.device_add_bluetooth_off_title,
                        messageRes = R.string.device_add_bluetooth_off_message,
                        primaryAction = DeviceQrScanPrimaryAction.OPEN_BLUETOOTH_SETTINGS
                    )
                    return@launch
                }

                AqlBleProvisioningScanner.StartResult.BluetoothUnavailable -> {
                    showFailure(
                        titleRes = R.string.device_add_bluetooth_unavailable_title,
                        messageRes = R.string.device_add_bluetooth_unavailable_message
                    )
                    return@launch
                }

                is AqlBleProvisioningScanner.StartResult.Failed -> {
                    showFailure(
                        titleRes = R.string.device_add_scan_failed_title,
                        messageRes = R.string.device_add_scan_failed_message
                    )
                    return@launch
                }
            }

            val candidate = awaitQrCandidate(payload)
            bleScanner.stopScan()

            if (candidate != null) {
                pendingPayload = null
                _uiState.value = DeviceQrScanUiState(
                    title = string(R.string.device_qr_verified_title),
                    message = string(R.string.device_qr_opening_wifi),
                    primaryAction = null
                )
                _events.send(
                    DeviceQrScanEvent.OpenWifiProvisioning(
                        result = DeviceQrPreflightSuccess(
                            deviceUid = payload.deviceUid.value,
                            deviceTitle = payload.displayName,
                            deviceSerial = payload.serialNumber,
                            deviceModel = SECURE_QR_SETUP_LABEL,
                            bleAddress = candidate.address,
                            bleName = payload.bleName,
                            claimCode = payload.claimCode,
                            rawQrPayload = payload.raw
                        )
                    )
                )
                return@launch
            }

            if (bleScanner.candidates.value.isEmpty()) {
                showFailure(
                    titleRes = R.string.device_qr_preflight_not_found_title,
                    messageRes = R.string.device_qr_preflight_not_found_message
                )
            } else {
                showFailure(
                    titleRes = R.string.device_qr_preflight_mismatch_title,
                    messageRes = R.string.device_qr_preflight_mismatch_message
                )
            }
        }
    }

    private suspend fun awaitQrCandidate(
        payload: AqlProvisioningQrPayload
    ): AqlBleProvisioningCandidate? {
        val targetBleName = payload.bleName.trim()
        if (targetBleName.isBlank()) return null

        return withTimeoutOrNull(QR_SCAN_TIMEOUT_MS) {
            bleScanner.candidates
                .map { candidates ->
                    candidates.firstOrNull { candidate ->
                        candidate.name.equals(targetBleName, ignoreCase = false)
                    }
                }
                .filterNotNull()
                .first()
        }
    }

    private fun showFailure(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        primaryAction: DeviceQrScanPrimaryAction = DeviceQrScanPrimaryAction.SCAN_AGAIN
    ) {
        bleScanner.stopScan()
        _uiState.value = DeviceQrScanUiState(
            title = string(titleRes),
            message = string(messageRes),
            primaryAction = primaryAction
        )
    }

    private fun string(
        @StringRes resId: Int
    ): String = getApplication<Application>().getString(resId)

    override fun onCleared() {
        scanJob?.cancel()
        bleScanner.stopScan()
        super.onCleared()
    }

    private companion object {
        const val QR_SCAN_TIMEOUT_MS = 10_000L
        const val SECURE_QR_SETUP_LABEL = "Secure QR Setup"
    }
}

data class DeviceQrScanUiState(
    val title: String = "",
    val message: String = "",
    val primaryAction: DeviceQrScanPrimaryAction? = null
)

enum class DeviceQrScanPrimaryAction {
    SCAN_AGAIN,
    REQUEST_BLE_PERMISSION,
    OPEN_BLUETOOTH_SETTINGS,
    OPEN_APP_SETTINGS
}

sealed interface DeviceQrScanEvent {
    data class OpenWifiProvisioning(
        val result: DeviceQrPreflightSuccess
    ) : DeviceQrScanEvent
}

data class DeviceQrPreflightSuccess(
    val deviceUid: String,
    val deviceTitle: String,
    val deviceSerial: String,
    val deviceModel: String,
    val bleAddress: String,
    val bleName: String,
    val claimCode: String,
    val rawQrPayload: String
)
