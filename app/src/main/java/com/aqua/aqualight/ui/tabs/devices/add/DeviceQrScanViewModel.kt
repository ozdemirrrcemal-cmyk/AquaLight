package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleDeviceInfoPreflightClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningAddressResolver
import com.aqua.aqualight.data.devices.provisioning.ble.QrCandidatePreflightResult
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrParser
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrPayload
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceQrScanViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val qrParser = AqlProvisioningQrParser()
    private val addressResolver = AqlBleProvisioningAddressResolver(application)
    private val preflightClient = AqlBleDeviceInfoPreflightClient(application)
    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceQrScanUiState())
    val uiState: StateFlow<DeviceQrScanUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceQrScanEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DeviceQrScanEvent> = _events.receiveAsFlow()

    private var pendingPayload: AqlProvisioningQrPayload? = null
    private var preflightJob: Job? = null

    fun onQrDetected(
        rawValue: String,
        hasBlePermissions: Boolean
    ) {
        if (preflightJob?.isActive == true) return

        val payload = qrParser.parse(rawValue)
            .getOrElse {
                showFailure(
                    titleRes = R.string.device_qr_preflight_invalid_title,
                    messageRes = R.string.device_qr_preflight_invalid_message
                )
                return
            }

        if (repository.currentDevice(payload.deviceUid) != null) {
            showFailure(
                titleRes = R.string.device_qr_preflight_already_added_title,
                messageRes = R.string.device_qr_preflight_already_added_message
            )
            return
        }

        if (!hasBlePermissions) {
            pendingPayload = payload
            _uiState.value = DeviceQrScanUiState(
                title = string(R.string.device_qr_preflight_bluetooth_permission_title),
                message = string(R.string.device_qr_preflight_bluetooth_permission_message),
                showScanAgain = false
            )
            viewModelScope.launch {
                _events.send(DeviceQrScanEvent.RequestBlePermission)
            }
            return
        }

        startPreflight(payload)
    }

    fun onBlePermissionResult(granted: Boolean) {
        val payload = pendingPayload
        pendingPayload = null

        if (!granted || payload == null) {
            showFailure(
                titleRes = R.string.device_qr_preflight_bluetooth_permission_title,
                messageRes = R.string.device_qr_preflight_bluetooth_permission_message
            )
            return
        }

        startPreflight(payload)
    }

    fun onScanAgain() {
        preflightJob?.cancel()
        preflightJob = null
        pendingPayload = null
        _uiState.value = DeviceQrScanUiState()
    }

    private fun startPreflight(payload: AqlProvisioningQrPayload) {
        preflightJob?.cancel()
        _uiState.value = DeviceQrScanUiState(
            title = string(R.string.device_qr_preflight_checking_title),
            message = string(R.string.device_qr_preflight_checking_message),
            showScanAgain = false
        )

        preflightJob = viewModelScope.launch {
            val draft = payload.toPreflightDraft()

            val resolvedAddress = addressResolver.resolveQrAddress(draft)
                .getOrElse { error ->
                    showMappedPreflightFailure(error.message.orEmpty())
                    return@launch
                }

            val verifiedDraft = draft.copy(bleAddress = resolvedAddress)
            when (val result = preflightClient.verifyQrCandidate(resolvedAddress, verifiedDraft)) {
                is QrCandidatePreflightResult.Allowed -> {
                    _uiState.value = DeviceQrScanUiState(
                        title = string(R.string.device_qr_verified_title),
                        message = string(R.string.device_qr_opening_wifi),
                        showScanAgain = false
                    )
                    _events.send(
                        DeviceQrScanEvent.OpenWifiProvisioning(
                            result = DeviceQrPreflightSuccess(
                                deviceUid = payload.deviceUid.value,
                                deviceTitle = payload.displayName,
                                deviceSerial = payload.serialNumber,
                                deviceModel = SECURE_QR_SETUP_LABEL,
                                bleAddress = result.bleAddress,
                                bleName = payload.bleName,
                                claimCode = payload.claimCode,
                                rawQrPayload = payload.raw
                            )
                        )
                    )
                }

                is QrCandidatePreflightResult.Rejected -> {
                    showMappedPreflightFailure(result.message)
                }

                is QrCandidatePreflightResult.Failed -> {
                    showMappedPreflightFailure(result.message)
                }
            }
        }
    }

    private fun AqlProvisioningQrPayload.toPreflightDraft(): AqlProvisioningDraft {
        return AqlProvisioningDraft(
            sessionId = UUID.randomUUID().toString(),
            candidateId = deviceUid.value,
            bleAddress = "",
            bleName = bleName,
            claimCode = claimCode,
            rawQrPayload = raw,
            deviceTitle = displayName,
            deviceSerial = serialNumber,
            deviceModel = SECURE_QR_SETUP_LABEL,
            wifiCredentials = AqlWifiCredentials(
                ssid = PREFLIGHT_WIFI_SSID,
                password = ""
            ),
            createdAtMillis = System.currentTimeMillis()
        )
    }

    private fun showMappedPreflightFailure(message: String) {
        val normalized = message.lowercase()
        when {
            normalized.contains("permission") -> showFailure(
                titleRes = R.string.device_qr_preflight_bluetooth_permission_title,
                messageRes = R.string.device_qr_preflight_bluetooth_permission_message
            )

            normalized.contains("bluetooth is disabled") -> showFailure(
                titleRes = R.string.device_add_bluetooth_off_title,
                messageRes = R.string.device_add_bluetooth_off_message
            )

            normalized.contains("does not match") ||
                normalized.contains("none matched") ||
                normalized.contains("not match") -> showFailure(
                    titleRes = R.string.device_qr_preflight_mismatch_title,
                    messageRes = R.string.device_qr_preflight_mismatch_message
                )

            normalized.contains("not in provisioning mode") ||
                normalized.contains("physical reset") ||
                normalized.contains("setup mode is not ready") -> showFailure(
                    titleRes = R.string.device_qr_preflight_not_setup_title,
                    messageRes = R.string.device_qr_preflight_not_setup_message
                )

            normalized.contains("not found") ||
                normalized.contains("timed out") ||
                normalized.contains("timeout") ||
                normalized.contains("could not be found") -> showFailure(
                    titleRes = R.string.device_qr_preflight_not_found_title,
                    messageRes = R.string.device_qr_preflight_not_found_message
                )

            else -> showFailure(
                titleRes = R.string.device_qr_preflight_mismatch_title,
                messageRes = R.string.device_qr_preflight_mismatch_message
            )
        }
    }

    private fun showFailure(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int
    ) {
        _uiState.value = DeviceQrScanUiState(
            title = string(titleRes),
            message = string(messageRes),
            showScanAgain = true
        )
    }

    private fun string(
        @StringRes resId: Int
    ): String = getApplication<Application>().getString(resId)

    override fun onCleared() {
        preflightJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val PREFLIGHT_WIFI_SSID = "qr-preflight"
        const val SECURE_QR_SETUP_LABEL = "Secure QR Setup"
    }
}

data class DeviceQrScanUiState(
    val title: String = "",
    val message: String = "",
    val showScanAgain: Boolean = false
)

sealed interface DeviceQrScanEvent {
    object RequestBlePermission : DeviceQrScanEvent

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
