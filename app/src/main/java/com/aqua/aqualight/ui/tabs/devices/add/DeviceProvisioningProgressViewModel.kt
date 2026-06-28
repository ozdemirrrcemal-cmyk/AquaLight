package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningAddressResolver
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceProvisioningProgressViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val addressResolver = AqlBleProvisioningAddressResolver(application)
    private val gattClient = AqlBleProvisioningGattClient(application)
    private val handoffSaver = AqlProvisioningHandoffSaver(application)
    private val routeResolver = DeviceRouteResolver()

    private val _uiState = MutableStateFlow(DeviceProvisioningProgressUiState())
    val uiState: StateFlow<DeviceProvisioningProgressUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceProvisioningProgressEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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
                title = "Setup session expired",
                message = "Go back and select the device again.",
                deviceName = "Unknown device",
                deviceSerial = "Unknown",
                bleAddress = "Unknown",
                wifiSsid = "Unknown",
                stepOne = "Device selected",
                stepTwo = "Wi-Fi details prepared",
                stepThree = "Setup session not found",
                canStart = false,
                buttonText = "Unavailable",
                showProgress = false
            )
            return
        }

        _uiState.value = DeviceProvisioningProgressUiState(
            title = "Device ready for setup",
            message = "Wi-Fi details are ready. A secure connection to your AquaLight device is being prepared.",
            deviceName = draft.deviceTitle.ifBlank { "AquaLight Device" },
            deviceSerial = draft.deviceSerial.ifBlank { draft.candidateId },
            bleAddress = draft.bleAddress.ifBlank { draft.bleName.ifBlank { "QR device will be located" } },
            wifiSsid = draft.wifiCredentials.ssid,
            stepOne = "Device selected",
            stepTwo = "Wi-Fi details prepared",
            stepThree = "Preparing secure connection",
            canStart = true,
            buttonText = "Try again",
            showProgress = false
        )
    }

    fun onBlePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            title = "Bluetooth permission required",
            message = "Allow Bluetooth access to complete secure device setup.",
            stepThree = "Waiting for Bluetooth permission",
            canStart = true,
            buttonText = "Try again",
            showProgress = false
        )
    }

    fun startProvisioning() {
        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = "Setup session expired",
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
                title = "Connecting to device",
                message = "A secure Bluetooth connection is being established with your AquaLight device.",
                bleAddress = readyDraft.bleAddress,
                stepThree = "Connecting over Bluetooth",
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
                title = "Device not found",
                message = "The QR code does not include a Bluetooth name. Search for the device with Scan and try again.",
                stepThree = "Device not found",
                canStart = true,
                buttonText = "Try again",
                showProgress = false
            )
            return null
        }

        _uiState.value = _uiState.value.copy(
            title = "Locating QR device",
            message = "Searching for $bleName over Bluetooth.",
            bleAddress = bleName,
            stepThree = "Locating QR device over Bluetooth",
            canStart = false,
            buttonText = "Finding...",
            showProgress = true
        )

        val resolvedAddress = addressResolver.resolveAddress(bleName)
            .getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    title = "QR device not found",
                    message = error.message ?: "The device selected by QR could not be found over Bluetooth.",
                    stepThree = "Device not found",
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
                        title = "Setup complete",
                        message = "Device details received. Preparing the device menu.",
                        stepThree = "Preparing device menu",
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
                    title = "Connecting to device",
                    message = "A secure Bluetooth connection is being established with your AquaLight device.",
                    stepThree = "Connecting over Bluetooth",
                    canStart = false,
                    buttonText = "Provisioning...",
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.Connected -> {
                _uiState.value.copy(
                    title = "Device found",
                    message = "Preparing the setup service.",
                    stepThree = "Bluetooth connection established",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.ServicesDiscovered -> {
                _uiState.value.copy(
                    title = "Preparing secure session",
                    message = "Starting the device setup session.",
                    stepThree = "Starting secure session",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.StartSessionWritten -> {
                _uiState.value.copy(
                    title = "Secure session started",
                    message = "Sending Wi-Fi details to your device.",
                    stepThree = "Sending Wi-Fi details",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.WifiCredentialsWritten -> {
                _uiState.value.copy(
                    title = "Wi-Fi details sent",
                    message = "Your device is connecting to the Wi-Fi network.",
                    stepThree = "Connecting device to Wi-Fi",
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.StatusReceived -> {
                _uiState.value.copy(
                    title = event.statusMessage.status.toProgressTitle(),
                    message = event.statusMessage.status.toProgressMessage(
                        fallback = event.statusMessage.message
                    ),
                    stepThree = event.statusMessage.status.toProgressStep(),
                    showProgress = event.statusMessage.status !in terminalStatuses,
                    canStart = event.statusMessage.status in retryStatuses,
                    buttonText = "Try again"
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
                    title = "Setup could not be completed",
                    message = event.message.toFriendlyError(),
                    stepThree = "Setup stopped",
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
                        title = "Connection closed",
                        message = "The device setup connection was closed. If the device is still in setup mode, try again.",
                        stepThree = "Connection closed",
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
            title = "Device online",
            message = "Your AquaLight device is connected to Wi-Fi. Preparing the device menu.",
            stepThree = "Preparing device menu",
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
                title = "Setup session expired",
                message = "Device details arrived, but the local setup session could not be found.",
                stepThree = "Device could not be saved",
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

                val route = routeResolver.resolve(
                    snapshot = snapshot,
                    requestedDeviceUid = snapshot.deviceUid.value
                )

                _uiState.value = _uiState.value.copy(
                    title = "Device added",
                    message = "${snapshot.title} is ready. Opening the device menu.",
                    stepThree = "Opening device menu",
                    canStart = false,
                    buttonText = "Opening...",
                    showProgress = true
                )

                _events.send(
                    DeviceProvisioningProgressEvent.OpenAddedDevice(route)
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    title = "Device could not be saved",
                    message = error.message ?: "Device details could not be saved.",
                    stepThree = "Device could not be saved",
                    canStart = true,
                    buttonText = "Try again",
                    showProgress = false
                )
            }
        }
    }

    private fun AqlProvisioningStatus.toProgressTitle(): String {
        return when (this) {
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.PHYSICAL_RESET -> "Device in setup mode"
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS,
            AqlProvisioningStatus.CLAIM_VALIDATING -> "Verifying setup"
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> "Wi-Fi details received"
            AqlProvisioningStatus.WIFI_CONNECTING -> "Connecting to Wi-Fi"
            AqlProvisioningStatus.WIFI_CONNECTED -> "Wi-Fi connected"
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> "Device online"
            AqlProvisioningStatus.COMPLETED -> "Setup complete"
            AqlProvisioningStatus.WIFI_FAILED -> "Wi-Fi connection failed"
            AqlProvisioningStatus.CLAIM_REJECTED -> "Device verification failed"
            AqlProvisioningStatus.TIMEOUT -> "Setup timed out"
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.UNKNOWN -> "Waiting for device status"
        }
    }

    private fun AqlProvisioningStatus.toProgressMessage(fallback: String): String {
        return when (this) {
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.PHYSICAL_RESET -> "The device is in setup mode. Preparing a secure session."
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS,
            AqlProvisioningStatus.CLAIM_VALIDATING -> "The AquaLight device is verifying the setup request."
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> "The device received the Wi-Fi details and is starting the connection."
            AqlProvisioningStatus.WIFI_CONNECTING -> "The device is connecting to the selected 2.4 GHz Wi-Fi network."
            AqlProvisioningStatus.WIFI_CONNECTED -> "Wi-Fi connection successful. Preparing the device menu."
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> "The device runtime connection is ready. Opening the menu."
            AqlProvisioningStatus.COMPLETED -> "Setup is complete. Opening the device menu."
            AqlProvisioningStatus.WIFI_FAILED -> "The password may be incorrect or the device may not see the 2.4 GHz network. Check the network and password."
            AqlProvisioningStatus.CLAIM_REJECTED -> "The device rejected this setup request. Use QR setup or hold the setup button for 5 seconds and try again."
            AqlProvisioningStatus.TIMEOUT -> "The device setup window expired. Hold the setup button for 5 seconds and try again."
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.UNKNOWN -> fallback.ifBlank { "Waiting for device setup status." }
        }
    }

    private fun AqlProvisioningStatus.toProgressStep(): String {
        return when (this) {
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> "Wi-Fi details delivered to device"
            AqlProvisioningStatus.WIFI_CONNECTING -> "Connecting device to Wi-Fi"
            AqlProvisioningStatus.WIFI_CONNECTED -> "Wi-Fi connection successful"
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> "Preparing device menu"
            AqlProvisioningStatus.COMPLETED -> "Setup complete"
            AqlProvisioningStatus.WIFI_FAILED -> "Wi-Fi connection failed"
            AqlProvisioningStatus.CLAIM_REJECTED -> "Device verification failed"
            AqlProvisioningStatus.TIMEOUT -> "Setup timed out"
            AqlProvisioningStatus.ERROR -> "Setup stopped"
            else -> "Setup in progress"
        }
    }

    private fun String.toFriendlyError(): String {
        val normalized = trim()
        return when {
            normalized.contains("StartSession is required", ignoreCase = true) ->
                "The device rejected Wi-Fi details before the secure setup session was ready. Put the device in setup mode and try again."
            normalized.contains("WiFi", ignoreCase = true) || normalized.contains("wifi", ignoreCase = true) ->
                "Wi-Fi connection failed. Make sure the network is 2.4 GHz and the password is correct."
            normalized.isNotBlank() -> normalized
            else -> "An unexpected setup problem occurred. Try again."
        }
    }

    override fun onCleared() {
        startJob?.cancel()
        gattEventsJob?.cancel()
        gattClient.close()
        super.onCleared()
    }

    private companion object {
        val terminalStatuses = setOf(
            AqlProvisioningStatus.COMPLETED,
            AqlProvisioningStatus.WIFI_FAILED,
            AqlProvisioningStatus.CLAIM_REJECTED,
            AqlProvisioningStatus.TIMEOUT,
            AqlProvisioningStatus.ERROR
        )

        val retryStatuses = setOf(
            AqlProvisioningStatus.WIFI_FAILED,
            AqlProvisioningStatus.CLAIM_REJECTED,
            AqlProvisioningStatus.TIMEOUT,
            AqlProvisioningStatus.ERROR
        )
    }
}

sealed interface DeviceProvisioningProgressEvent {
    data class OpenAddedDevice(
        val route: DeviceRoute
    ) : DeviceProvisioningProgressEvent
}

data class DeviceProvisioningProgressUiState(
    val title: String = "Setting up device",
    val message: String = "Your AquaLight device is being prepared securely.",
    val deviceName: String = "",
    val deviceSerial: String = "",
    val bleAddress: String = "",
    val wifiSsid: String = "",
    val stepOne: String = "Device selected",
    val stepTwo: String = "Wi-Fi details prepared",
    val stepThree: String = "Preparing secure connection",
    val canStart: Boolean = false,
    val buttonText: String = "Try again",
    val showProgress: Boolean = false
)
