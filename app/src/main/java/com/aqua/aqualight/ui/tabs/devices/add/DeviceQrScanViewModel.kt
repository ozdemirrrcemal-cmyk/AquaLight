package com.aqua.aqualight.ui.tabs.devices.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDiscoveryOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningQrPayload
import com.aqua.aqualight.application.devices.provisioning.ProvisioningScanStartResult
import com.aqua.aqualight.application.text.AppTextResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceQrScanViewModel(
    private val discoveryOperations: ProvisioningDiscoveryOperations,
    private val textResolver: AppTextResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceQrScanUiState())
    val uiState: StateFlow<DeviceQrScanUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceQrScanEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DeviceQrScanEvent> = _events.receiveAsFlow()

    private var pendingPayload: ProvisioningQrPayload? = null
    private var scanJob: Job? = null

    fun onQrDetected(rawValue: String, hasBlePermissions: Boolean) {
        if (scanJob?.isActive == true) return

        discardPendingPayload()
        val payload = discoveryOperations.parseQr(rawValue).getOrElse {
            showFailure(
                titleRes = R.string.device_qr_preflight_invalid_title,
                messageRes = R.string.device_qr_preflight_invalid_message
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

    fun onBlePermissionResult(granted: Boolean) {
        val payload = pendingPayload
        if (!granted || payload == null) {
            showFailure(
                titleRes = R.string.device_qr_preflight_bluetooth_permission_title,
                messageRes = R.string.device_qr_preflight_bluetooth_permission_message,
                primaryAction = DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS
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
        discoveryOperations.stopScan()
        discoveryOperations.clearCandidates()
        discardPendingPayload()
        _uiState.value = DeviceQrScanUiState()
    }

    private fun startQrBleScan(payload: ProvisioningQrPayload) {
        pendingPayload = payload
        scanJob?.cancel()
        discoveryOperations.stopScan()
        discoveryOperations.clearCandidates()

        _uiState.value = DeviceQrScanUiState(
            title = string(R.string.device_qr_preflight_checking_title),
            message = string(R.string.device_qr_preflight_checking_message),
            primaryAction = null
        )

        scanJob = viewModelScope.launch {
            when (val result = discoveryOperations.startScan()) {
                ProvisioningScanStartResult.Started -> Unit
                ProvisioningScanStartResult.MissingPermission -> {
                    showFailure(
                        titleRes = R.string.device_qr_preflight_bluetooth_permission_title,
                        messageRes = R.string.device_qr_preflight_bluetooth_permission_message,
                        primaryAction = DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION
                    )
                    return@launch
                }
                ProvisioningScanStartResult.BluetoothOff -> {
                    showFailure(
                        titleRes = R.string.device_add_bluetooth_off_title,
                        messageRes = R.string.device_add_bluetooth_off_message,
                        primaryAction = DeviceQrScanPrimaryAction.OPEN_BLUETOOTH_SETTINGS
                    )
                    return@launch
                }
                ProvisioningScanStartResult.BluetoothUnavailable -> {
                    discardPendingPayload()
                    showFailure(
                        titleRes = R.string.device_add_bluetooth_unavailable_title,
                        messageRes = R.string.device_add_bluetooth_unavailable_message
                    )
                    return@launch
                }
                is ProvisioningScanStartResult.Failed -> {
                    discardPendingPayload()
                    showFailure(
                        titleRes = R.string.device_add_scan_failed_title,
                        messageRes = R.string.device_add_scan_failed_message
                    )
                    return@launch
                }
            }

            val candidate = discoveryOperations.awaitQrCandidate(
                payload = payload,
                timeoutMillis = QR_SCAN_TIMEOUT_MS
            )
            discoveryOperations.stopScan()

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
                            deviceUid = payload.deviceUid,
                            deviceTitle = payload.displayName,
                            deviceSerial = payload.serialNumber,
                            deviceModel = string(R.string.device_setup_method_secure_qr),
                            bleAddress = candidate.address,
                            bleName = payload.bleName,
                            qrSecretReference = payload.secretReference
                        )
                    )
                )
                return@launch
            }

            val hasNearbyCandidates = discoveryOperations.hasCandidates()
            val isAlreadyRegistered = discoveryOperations.isRegistered(payload.deviceUid)
            discardPendingPayload()
            when {
                hasNearbyCandidates -> showFailure(
                    titleRes = R.string.device_qr_preflight_mismatch_title,
                    messageRes = R.string.device_qr_preflight_mismatch_message
                )
                isAlreadyRegistered -> showFailure(
                    titleRes = R.string.device_qr_preflight_already_added_title,
                    messageRes = R.string.device_qr_preflight_already_added_message
                )
                else -> showFailure(
                    titleRes = R.string.device_qr_preflight_not_found_title,
                    messageRes = R.string.device_qr_preflight_not_found_message
                )
            }
        }
    }

    private fun discardPendingPayload() {
        pendingPayload?.let(discoveryOperations::discardQrPayload)
        pendingPayload = null
    }

    private fun showFailure(
        titleRes: Int,
        messageRes: Int,
        primaryAction: DeviceQrScanPrimaryAction = DeviceQrScanPrimaryAction.SCAN_AGAIN
    ) {
        discoveryOperations.stopScan()
        _uiState.value = DeviceQrScanUiState(
            title = string(titleRes),
            message = string(messageRes),
            primaryAction = primaryAction
        )
    }

    private fun string(resId: Int): String = textResolver.get(resId)

    override fun onCleared() {
        scanJob?.cancel()
        discoveryOperations.stopScan()
        discardPendingPayload()
        super.onCleared()
    }

    private companion object {
        const val QR_SCAN_TIMEOUT_MS = 10_000L
    }
}

data class DeviceQrScanUiState(
    val title: String = "",
    val message: String = "",
    val primaryAction: DeviceQrScanPrimaryAction? = null
)

enum class DeviceQrScanPrimaryAction {
    SCAN_AGAIN,
    REQUEST_CAMERA_PERMISSION,
    OPEN_CAMERA_SETTINGS,
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
    val qrSecretReference: String
)
