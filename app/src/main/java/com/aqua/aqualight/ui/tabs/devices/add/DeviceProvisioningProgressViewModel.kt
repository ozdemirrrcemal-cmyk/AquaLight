package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningAddressResolver
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceProvisioningProgressViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val addressResolver = AqlBleProvisioningAddressResolver(application)
    private val gattClient = AqlBleProvisioningGattClient(application)
    private val handoffSaver = AqlProvisioningHandoffSaver(application)

    private val _uiState = MutableStateFlow(DeviceProvisioningProgressUiState())
    val uiState: StateFlow<DeviceProvisioningProgressUiState> = _uiState.asStateFlow()

    private var boundSessionId: String? = null
    private var activeDraft: AqlProvisioningDraft? = null
    private var gattEventsJob: Job? = null
    private var handoffSaved = false
    private var startJob: Job? = null

    fun bind(sessionId: String) {
        if (sessionId.isBlank() || boundSessionId == sessionId) {
            return
        }

        boundSessionId = sessionId

        val draft = AqlProvisioningDraftStore.get(sessionId)
        activeDraft = draft

        if (draft == null) {
            _uiState.value = DeviceProvisioningProgressUiState(
                title = "Provisioning session expired",
                message = "Go back and select the device again.",
                deviceName = "Unknown device",
                deviceSerial = "Unknown",
                bleAddress = "Unknown",
                wifiSsid = "Unknown",
                canStart = false,
                buttonText = "Unavailable",
                showProgress = false
            )
            return
        }

        _uiState.value = DeviceProvisioningProgressUiState(
            title = "Ready for BLE provisioning",
            message = "The selected device and Wi-Fi credentials are prepared. Start provisioning to transfer them over BLE.",
            deviceName = draft.deviceTitle.ifBlank { "AquaLight Device" },
            deviceSerial = draft.deviceSerial.ifBlank { draft.candidateId },
            bleAddress = draft.bleAddress.ifBlank { draft.bleName.ifBlank { "Resolve from QR" } },
            wifiSsid = draft.wifiCredentials.ssid,
            canStart = true,
            buttonText = "Start provisioning",
            showProgress = false
        )
    }

    fun onBlePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            title = "Bluetooth permission required",
            message = "BLE permission is required to connect to the selected AquaLight device.",
            stepThree = "3. Permission required",
            canStart = true,
            buttonText = "Try again",
            showProgress = false
        )
    }

    fun startProvisioning() {
        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = "Provisioning session expired",
                message = "Go back and select the device again.",
                canStart = false,
                buttonText = "Unavailable",
                showProgress = false
            )
            return
        }

        startJob?.cancel()
        handoffSaved = false
        observeGattEvents()

        startJob = viewModelScope.launch {
            val readyDraft = resolveBleAddressIfNeeded(draft) ?: return@launch
            activeDraft = readyDraft

            _uiState.value = _uiState.value.copy(
                title = "Connecting over BLE",
                message = "Opening a secure provisioning connection to the selected AquaLight device.",
                bleAddress = readyDraft.bleAddress,
                stepThree = "3. Connecting to BLE device",
                canStart = false,
                buttonText = "Provisioning...",
                showProgress = true
            )

            gattClient.start(readyDraft)
        }
    }

    private suspend fun resolveBleAddressIfNeeded(
        draft: AqlProvisioningDraft
    ): AqlProvisioningDraft? {
        if (draft.bleAddress.isNotBlank()) {
            return draft
        }

        val bleName = draft.bleName.trim()
        if (bleName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                title = "BLE device name missing",
                message = "QR payload does not include a BLE name. Scan with BLE discovery instead.",
                stepThree = "3. BLE resolve failed",
                canStart = true,
                buttonText = "Try again",
                showProgress = false
            )
            return null
        }

        _uiState.value = _uiState.value.copy(
            title = "Finding QR device",
            message = "Searching nearby Bluetooth devices for $bleName.",
            bleAddress = bleName,
            stepThree = "3. Resolving BLE address from QR",
            canStart = false,
            buttonText = "Finding...",
            showProgress = true
        )

        val resolvedAddress = addressResolver.resolveAddress(bleName)
            .getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    title = "QR device not found",
                    message = error.message ?: "The QR device could not be found over BLE.",
                    stepThree = "3. BLE resolve failed",
                    canStart = true,
                    buttonText = "Try again",
                    showProgress = false
                )
                return null
            }

        return draft.copy(
            bleAddress = resolvedAddress
        )
    }

    private fun observeGattEvents() {
        if (gattEventsJob != null) {
            return
        }

        gattEventsJob = viewModelScope.launch {
            gattClient.events.collect { event ->
                handleGattEvent(event)
            }
        }
    }

    private fun handleGattEvent(event: AqlBleProvisioningGattEvent) {
        when (event) {
            is AqlBleProvisioningGattEvent.RuntimeHandoffReceived -> {
                renderRuntimeHandoffReceived(event.handoff)
                saveRuntimeHandoff(event.handoff)
            }

            AqlBleProvisioningGattEvent.Completed -> {
                if (!handoffSaved) {
                    _uiState.value = _uiState.value.copy(
                        title = "Provisioning completed",
                        message = "Runtime handoff was received. Saving device is still in progress.",
                        stepThree = "3. Saving device",
                        canStart = false,
                        buttonText = "Saving...",
                        showProgress = true
                    )
                }
            }

            else -> {
                _uiState.value = reduceGattEvent(event)
            }
        }
    }

    private fun reduceGattEvent(
        event: AqlBleProvisioningGattEvent
    ): DeviceProvisioningProgressUiState {
        return when (event) {
            is AqlBleProvisioningGattEvent.Connecting -> {
                _uiState.value.copy(
                    title = "Connecting over BLE",
                    message = "Connecting to ${event.address}.",
                    stepThree = "3. Connecting to BLE device",
                    canStart = false,
                    buttonText = "Provisioning...",
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.Connected -> {
                _uiState.value.copy(
                    title = "BLE connected",
                    message = "Discovering AquaLight provisioning service.",
                    stepThree = "3. BLE connected",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.ServicesDiscovered -> {
                _uiState.value.copy(
                    title = "Provisioning service ready",
                    message = "Sending secure provisioning session request.",
                    stepThree = "3. Provisioning service discovered",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.StartSessionWritten -> {
                _uiState.value.copy(
                    title = "Session started",
                    message = "Sending Wi-Fi credentials over BLE.",
                    stepThree = "3. Session started",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.WifiCredentialsWritten -> {
                _uiState.value.copy(
                    title = "Wi-Fi sent",
                    message = "Waiting for device provisioning status.",
                    stepThree = "3. Wi-Fi credentials sent",
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.StatusReceived -> {
                _uiState.value.copy(
                    title = "Device status: ${event.statusMessage.status.wireValue}",
                    message = event.statusMessage.message.ifBlank {
                        "Waiting for runtime endpoint and token."
                    },
                    stepThree = "3. ${event.statusMessage.status.wireValue}",
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.RuntimeHandoffReceived -> {
                _uiState.value
            }

            AqlBleProvisioningGattEvent.Completed -> {
                _uiState.value
            }

            is AqlBleProvisioningGattEvent.Failed -> {
                _uiState.value.copy(
                    title = "Provisioning failed",
                    message = event.message,
                    stepThree = "3. Failed",
                    canStart = true,
                    buttonText = "Try again",
                    showProgress = false
                )
            }

            AqlBleProvisioningGattEvent.Disconnected -> {
                if (handoffSaved) {
                    _uiState.value
                } else {
                    _uiState.value.copy(
                        title = "BLE disconnected",
                        message = "The provisioning connection was closed.",
                        stepThree = "3. BLE disconnected",
                        canStart = true,
                        buttonText = "Try again",
                        showProgress = false
                    )
                }
            }
        }
    }

    private fun renderRuntimeHandoffReceived(
        handoff: AqlProvisioningRuntimeHandoff
    ) {
        _uiState.value = _uiState.value.copy(
            title = "Runtime handoff received",
            message = handoff.endpoint.toWebSocketUrl()
                ?: "Runtime endpoint was received. Saving device and token.",
            stepThree = "3. Saving runtime token and endpoint",
            canStart = false,
            buttonText = "Saving...",
            showProgress = true
        )
    }

    private fun saveRuntimeHandoff(
        handoff: AqlProvisioningRuntimeHandoff
    ) {
        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = "Provisioning session expired",
                message = "Runtime handoff arrived but the local provisioning session is missing.",
                stepThree = "3. Save failed",
                canStart = true,
                buttonText = "Try again",
                showProgress = false
            )
            return
        }

        viewModelScope.launch {
            val result = handoffSaver.saveAndConnect(
                draft = draft,
                handoff = handoff
            )

            result.onSuccess { snapshot ->
                handoffSaved = true
                boundSessionId?.let { sessionId ->
                    AqlProvisioningDraftStore.remove(sessionId)
                }

                _uiState.value = _uiState.value.copy(
                    title = "Device added",
                    message = "${snapshot.title} was saved. WebSocket runtime connection is starting.",
                    stepThree = "3. Device saved and runtime connection started",
                    canStart = false,
                    buttonText = "Completed",
                    showProgress = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    title = "Device save failed",
                    message = error.message ?: "Runtime handoff could not be saved.",
                    stepThree = "3. Save failed",
                    canStart = true,
                    buttonText = "Try again",
                    showProgress = false
                )
            }
        }
    }

    override fun onCleared() {
        startJob?.cancel()
        gattEventsJob?.cancel()
        gattClient.close()
        super.onCleared()
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
    val canStart: Boolean = false,
    val buttonText: String = "Start provisioning",
    val showProgress: Boolean = false
)
