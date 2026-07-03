package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
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

    private val _uiState = MutableStateFlow(initialState())
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
                title = string(R.string.device_provisioning_session_expired_title),
                message = string(R.string.device_provisioning_session_expired_message),
                deviceName = string(R.string.device_provisioning_unknown_device),
                deviceSerial = string(R.string.device_provisioning_unknown),
                bleAddress = string(R.string.device_provisioning_unknown),
                wifiSsid = string(R.string.device_provisioning_unknown),
                stepOne = string(R.string.device_provisioning_step_device_selected),
                stepTwo = string(R.string.device_provisioning_step_wifi_prepared),
                stepThree = string(R.string.device_provisioning_session_not_found),
                canStart = false,
                buttonText = string(R.string.device_provisioning_unavailable),
                showProgress = false
            )
            return
        }

        _uiState.value = DeviceProvisioningProgressUiState(
            title = string(R.string.device_provisioning_ready_title),
            message = string(R.string.device_provisioning_ready_message),
            deviceName = draft.deviceTitle.ifBlank { string(R.string.device_wifi_default_device_name) },
            deviceSerial = draft.deviceSerial.ifBlank { draft.candidateId },
            bleAddress = draft.bleAddress.ifBlank {
                draft.bleName.ifBlank { string(R.string.device_provisioning_qr_device_will_be_located) }
            },
            wifiSsid = draft.wifiCredentials.ssid,
            stepOne = string(R.string.device_provisioning_step_device_selected),
            stepTwo = string(R.string.device_provisioning_step_wifi_prepared),
            stepThree = string(R.string.device_provisioning_step_preparing_secure),
            canStart = true,
            buttonText = string(R.string.device_provisioning_try_again),
            showProgress = false
        )
    }

    fun onBlePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_bluetooth_permission_title),
            message = string(R.string.device_provisioning_bluetooth_permission_message),
            stepThree = string(R.string.device_provisioning_waiting_bluetooth_permission),
            canStart = true,
            buttonText = string(R.string.device_provisioning_try_again),
            showProgress = false
        )
    }

    fun startProvisioning() {
        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_session_expired_title),
                message = string(R.string.device_provisioning_session_expired_message),
                canStart = false,
                buttonText = string(R.string.device_provisioning_unavailable),
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
                title = string(R.string.device_provisioning_connecting_title),
                message = string(R.string.device_provisioning_connecting_message),
                bleAddress = readyDraft.bleAddress,
                stepThree = string(R.string.device_provisioning_connecting_step),
                canStart = false,
                buttonText = string(R.string.device_provisioning_running),
                showProgress = true
            )

            gattClient.start(readyDraft)
        }
    }

    private suspend fun resolveBleAddressIfNeeded(
        draft: AqlProvisioningDraft
    ): AqlProvisioningDraft? {
        val existingAddress = draft.bleAddress.trim()
        val bleName = draft.bleName.trim()

        if (bleName.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_locating_qr_title),
                message = string(R.string.device_provisioning_locating_qr_message_format, bleName),
                bleAddress = bleName,
                stepThree = string(R.string.device_provisioning_locating_qr_step),
                canStart = false,
                buttonText = string(R.string.device_provisioning_finding),
                showProgress = true
            )

            addressResolver.resolveQrAddress(draft)
                .onSuccess { resolvedAddress ->
                    if (resolvedAddress.isNotBlank()) {
                        return draft.copy(bleAddress = resolvedAddress)
                    }
                }
                .onFailure { error ->
                    if (existingAddress.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            title = string(R.string.device_provisioning_qr_not_found_title),
                            message = error.message ?: string(R.string.device_provisioning_qr_not_found_message),
                            stepThree = string(R.string.device_provisioning_device_not_found_title),
                            canStart = true,
                            buttonText = string(R.string.device_provisioning_try_again),
                            showProgress = false
                        )
                        return null
                    }
                }
        }

        if (existingAddress.isNotBlank()) {
            return draft.copy(bleAddress = existingAddress)
        }

        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_device_not_found_title),
            message = string(R.string.device_provisioning_qr_missing_ble_message),
            stepThree = string(R.string.device_provisioning_device_not_found_title),
            canStart = true,
            buttonText = string(R.string.device_provisioning_try_again),
            showProgress = false
        )
        return null
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

            is AqlBleProvisioningGattEvent.DeviceInfoVerified -> {
                renderDeviceInfoVerified(event)
            }

            AqlBleProvisioningGattEvent.Completed -> {
                if (!handoffSaved) {
                    _uiState.value = _uiState.value.copy(
                        title = string(R.string.device_provisioning_setup_complete_title),
                        message = string(R.string.device_provisioning_details_received_message),
                        stepThree = string(R.string.device_provisioning_preparing_menu_step),
                        canStart = false,
                        buttonText = string(R.string.device_provisioning_saving),
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
                    title = string(R.string.device_provisioning_connecting_title),
                    message = string(R.string.device_provisioning_connecting_message),
                    stepThree = string(R.string.device_provisioning_connecting_step),
                    canStart = false,
                    buttonText = string(R.string.device_provisioning_running),
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.Connected -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_device_found_title),
                    message = string(R.string.device_provisioning_prepare_service_message),
                    stepThree = string(R.string.device_provisioning_bluetooth_connected_step),
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.ServicesDiscovered -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_secure_session_title),
                    message = string(R.string.device_provisioning_secure_session_message),
                    stepThree = string(R.string.device_provisioning_secure_session_step),
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.StartSessionWritten -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_session_started_title),
                    message = string(R.string.device_provisioning_sending_wifi_message),
                    stepThree = string(R.string.device_provisioning_sending_wifi_step),
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.WifiCredentialsWritten -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_wifi_sent_title),
                    message = string(R.string.device_provisioning_wifi_connecting_message),
                    stepThree = string(R.string.device_provisioning_wifi_connecting_step),
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
                    buttonText = string(R.string.device_provisioning_try_again)
                )
            }

            is AqlBleProvisioningGattEvent.DeviceInfoVerified -> _uiState.value
            is AqlBleProvisioningGattEvent.RuntimeHandoffReceived -> _uiState.value
            AqlBleProvisioningGattEvent.Completed -> _uiState.value

            is AqlBleProvisioningGattEvent.Failed -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_failed_title),
                    message = event.message.toFriendlyError(),
                    stepThree = string(R.string.device_provisioning_setup_stopped),
                    canStart = true,
                    buttonText = string(R.string.device_provisioning_try_again),
                    showProgress = false
                )
            }

            AqlBleProvisioningGattEvent.Disconnected -> {
                if (handoffSaved) {
                    _uiState.value
                } else {
                    _uiState.value.copy(
                        title = string(R.string.device_provisioning_connection_closed_title),
                        message = string(R.string.device_provisioning_connection_closed_message),
                        stepThree = string(R.string.device_provisioning_connection_closed_step),
                        canStart = true,
                        buttonText = string(R.string.device_provisioning_try_again),
                        showProgress = false
                    )
                }
            }
        }
    }

    private fun renderDeviceInfoVerified(
        event: AqlBleProvisioningGattEvent.DeviceInfoVerified
    ) {
        val currentDraft = activeDraft
        if (currentDraft != null) {
            activeDraft = currentDraft.copy(
                deviceTitle = event.deviceTitle.ifBlank { currentDraft.deviceTitle },
                deviceSerial = event.deviceSerial.ifBlank { currentDraft.deviceSerial },
                deviceModel = event.deviceModel.ifBlank { currentDraft.deviceModel }
            )
        }

        _uiState.value = _uiState.value.copy(
            deviceName = event.deviceTitle.ifBlank { _uiState.value.deviceName },
            deviceSerial = event.deviceSerial.ifBlank { _uiState.value.deviceSerial }
        )
    }

    private fun renderRuntimeHandoffReceived(
        handoff: AqlProvisioningRuntimeHandoff
    ) {
        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_device_online_title),
            message = string(R.string.device_provisioning_device_online_message),
            stepThree = string(R.string.device_provisioning_preparing_menu_step),
            canStart = false,
            buttonText = string(R.string.device_provisioning_saving),
            showProgress = true
        )
    }

    private fun saveRuntimeHandoff(
        handoff: AqlProvisioningRuntimeHandoff
    ) {
        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_session_expired_title),
                message = string(R.string.device_provisioning_save_missing_session_message),
                stepThree = string(R.string.device_provisioning_save_failed_step),
                canStart = true,
                buttonText = string(R.string.device_provisioning_try_again),
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
                    title = string(R.string.device_provisioning_added_title),
                    message = string(R.string.device_provisioning_added_message_format, snapshot.title),
                    stepThree = string(R.string.device_provisioning_opening_menu_step),
                    canStart = false,
                    buttonText = string(R.string.device_provisioning_opening),
                    showProgress = true
                )

                _events.send(
                    DeviceProvisioningProgressEvent.OpenAddedDevice(route)
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    title = string(R.string.device_provisioning_save_failed_title),
                    message = error.message ?: string(R.string.device_provisioning_save_failed_message),
                    stepThree = string(R.string.device_provisioning_save_failed_step),
                    canStart = true,
                    buttonText = string(R.string.device_provisioning_try_again),
                    showProgress = false
                )
            }
        }
    }

    private fun AqlProvisioningStatus.toProgressTitle(): String {
        return when (this) {
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.PHYSICAL_RESET -> string(R.string.device_provisioning_status_setup_mode_title)
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS,
            AqlProvisioningStatus.CLAIM_VALIDATING -> string(R.string.device_provisioning_status_verifying_title)
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> string(R.string.device_provisioning_status_wifi_received_title)
            AqlProvisioningStatus.WIFI_CONNECTING -> string(R.string.device_provisioning_status_wifi_connecting_title)
            AqlProvisioningStatus.WIFI_CONNECTED -> string(R.string.device_provisioning_status_wifi_connected_title)
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> string(R.string.device_provisioning_device_online_title)
            AqlProvisioningStatus.COMPLETED -> string(R.string.device_provisioning_setup_complete_title)
            AqlProvisioningStatus.WIFI_FAILED -> string(R.string.device_provisioning_status_wifi_failed_title)
            AqlProvisioningStatus.CLAIM_REJECTED -> string(R.string.device_provisioning_status_claim_rejected_title)
            AqlProvisioningStatus.TIMEOUT -> string(R.string.device_provisioning_status_timeout_title)
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.UNKNOWN -> string(R.string.device_provisioning_status_waiting_title)
        }
    }

    private fun AqlProvisioningStatus.toProgressMessage(fallback: String): String {
        return when (this) {
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.PHYSICAL_RESET -> string(R.string.device_provisioning_status_setup_mode_message)
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS,
            AqlProvisioningStatus.CLAIM_VALIDATING -> string(R.string.device_provisioning_status_verifying_message)
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> string(R.string.device_provisioning_status_wifi_received_message)
            AqlProvisioningStatus.WIFI_CONNECTING -> string(R.string.device_provisioning_status_wifi_connecting_message)
            AqlProvisioningStatus.WIFI_CONNECTED -> string(R.string.device_provisioning_status_wifi_connected_message)
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> string(R.string.device_provisioning_status_runtime_ready_message)
            AqlProvisioningStatus.COMPLETED -> string(R.string.device_provisioning_status_setup_complete_message)
            AqlProvisioningStatus.WIFI_FAILED -> string(R.string.device_provisioning_status_wifi_failed_message)
            AqlProvisioningStatus.CLAIM_REJECTED -> string(R.string.device_provisioning_status_claim_rejected_message)
            AqlProvisioningStatus.TIMEOUT -> string(R.string.device_provisioning_status_timeout_message)
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.UNKNOWN -> fallback.ifBlank {
                string(R.string.device_provisioning_status_unknown_message)
            }
        }
    }

    private fun AqlProvisioningStatus.toProgressStep(): String {
        return when (this) {
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> string(R.string.device_provisioning_step_wifi_delivered)
            AqlProvisioningStatus.WIFI_CONNECTING -> string(R.string.device_provisioning_wifi_connecting_step)
            AqlProvisioningStatus.WIFI_CONNECTED -> string(R.string.device_provisioning_step_wifi_success)
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> string(R.string.device_provisioning_preparing_menu_step)
            AqlProvisioningStatus.COMPLETED -> string(R.string.device_provisioning_step_setup_complete)
            AqlProvisioningStatus.WIFI_FAILED -> string(R.string.device_provisioning_step_wifi_failed)
            AqlProvisioningStatus.CLAIM_REJECTED -> string(R.string.device_provisioning_step_verification_failed)
            AqlProvisioningStatus.TIMEOUT -> string(R.string.device_provisioning_step_timeout)
            AqlProvisioningStatus.ERROR -> string(R.string.device_provisioning_setup_stopped)
            else -> string(R.string.device_provisioning_step_in_progress)
        }
    }

    private fun String.toFriendlyError(): String {
        val normalized = trim()
        return when {
            normalized.contains("StartSession is required", ignoreCase = true) ->
                string(R.string.device_provisioning_error_start_session)
            normalized.contains("WiFi", ignoreCase = true) || normalized.contains("wifi", ignoreCase = true) ->
                string(R.string.device_provisioning_error_wifi_failed)
            normalized.isNotBlank() -> normalized
            else -> string(R.string.device_provisioning_error_unexpected)
        }
    }

    private fun initialState(): DeviceProvisioningProgressUiState {
        return DeviceProvisioningProgressUiState(
            title = string(R.string.device_provisioning_default_title),
            message = string(R.string.device_provisioning_default_message),
            stepOne = string(R.string.device_provisioning_step_device_selected),
            stepTwo = string(R.string.device_provisioning_step_wifi_prepared),
            stepThree = string(R.string.device_provisioning_step_preparing_secure),
            buttonText = string(R.string.device_provisioning_try_again)
        )
    }

    private fun string(
        @StringRes resId: Int,
        vararg args: Any
    ): String {
        return getApplication<Application>().getString(resId, *args)
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
