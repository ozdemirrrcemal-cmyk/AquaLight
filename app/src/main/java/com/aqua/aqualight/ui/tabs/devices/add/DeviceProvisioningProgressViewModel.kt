package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningAddressResolver
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningStatusMessage
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
    private val ownerUid = UserDataScope.requireCurrentUid()

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<DeviceProvisioningProgressUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceProvisioningProgressEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var boundSessionId: String? = null
    private var activeDraft: AqlProvisioningDraft? = null
    private var gattEventsJob: Job? = null
    private var handoffSaved = false
    private var handoffReceived = false
    private var provisioningStopped = false
    private var setupCompleted = false
    private var startJob: Job? = null
    private var handoffSaveJob: Job? = null
    private var commitJob: Job? = null
    private var rollbackJob: Job? = null
    private var exitJob: Job? = null
    private var exitRequested = false
    private var registrationCommitted = false
    private var pendingAddedRoute: DeviceRoute? = null
    private var pendingPreparedSnapshot: DeviceSnapshot? = null
    private var pendingSavedDeviceUid: DeviceUid? = null

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
            deviceSerial = draft.deviceSerial.ifBlank {
                string(R.string.device_provisioning_unknown)
            },
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
            showProgress = false,
            wifiCredentialFailure = null
        )
    }

    fun requestExit() {
        if (exitJob?.isActive == true || registrationCommitted) {
            return
        }

        exitRequested = true

        if (commitJob?.isActive == true) {
            return
        }

        provisioningStopped = true
        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_cancelling_title),
            message = string(R.string.device_provisioning_cancelling_message),
            stepThree = string(R.string.device_provisioning_cancelling_step),
            canStart = false,
            showProgress = true,
            isCancelling = true,
            wifiCredentialFailure = null
        )

        exitJob = viewModelScope.launch {
            startJob?.cancelAndJoin()
            handoffSaveJob?.cancelAndJoin()
            rollbackJob?.join()
            gattEventsJob?.cancelAndJoin()
            runCatching(gattClient::close)
                .exceptionOrNull()
                ?.printStackTrace()

            val pendingDeviceUid = pendingSavedDeviceUid
            val rollbackResult = if (pendingDeviceUid == null) {
                Result.success(Unit)
            } else {
                handoffSaver.rollbackProvisioningRegistrationForOwner(
                    ownerUid = ownerUid,
                    deviceUid = pendingDeviceUid
                )
            }

            rollbackResult
                .onSuccess {
                    clearPendingRegistrationState()
                    boundSessionId?.let(AqlProvisioningDraftStore::remove)
                    _events.send(DeviceProvisioningProgressEvent.ExitProvisioning)
                }
                .onFailure {
                    exitRequested = false
                    renderRollbackFailure()
                }
        }
    }

    fun startProvisioning() {
        if (_uiState.value.requiresFreshDeviceSelection) {
            requestExit()
            return
        }

        if (
            exitJob?.isActive == true ||
            rollbackJob?.isActive == true ||
            commitJob?.isActive == true
        ) {
            return
        }

        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_session_expired_title),
                message = string(R.string.device_provisioning_session_expired_message),
                canStart = false,
                buttonText = string(R.string.device_provisioning_unavailable),
                showProgress = false,
                wifiCredentialFailure = null
            )
            return
        }

        startJob?.cancel()
        handoffSaved = false
        handoffReceived = false
        provisioningStopped = false
        setupCompleted = false
        pendingAddedRoute = null
        pendingPreparedSnapshot = null
        pendingSavedDeviceUid = null
        exitRequested = false
        registrationCommitted = false
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
                showProgress = true,
                wifiCredentialFailure = null
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
                showProgress = true,
                wifiCredentialFailure = null
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
                            message = string(R.string.device_provisioning_qr_not_found_message),
                            stepThree = string(R.string.device_provisioning_device_not_found_title),
                            canStart = true,
                            buttonText = string(R.string.device_provisioning_try_again),
                            showProgress = false,
                            wifiCredentialFailure = null
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
            showProgress = false,
            wifiCredentialFailure = null
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
            AqlBleProvisioningGattEvent.FinalizeSetupWritten -> {
                _uiState.value = _uiState.value.copy(
                    title = string(R.string.device_provisioning_setup_complete_title),
                    message = string(R.string.device_provisioning_details_received_message),
                    stepThree = string(R.string.device_provisioning_step_setup_complete),
                    canStart = false,
                    buttonText = string(R.string.device_provisioning_opening),
                    showProgress = true,
                    wifiCredentialFailure = null
                )
            }

            is AqlBleProvisioningGattEvent.RuntimeHandoffReceived -> {
                handoffReceived = true
                renderRuntimeHandoffReceived(event.handoff)
                saveRuntimeHandoff(event.handoff)
            }

            is AqlBleProvisioningGattEvent.DeviceInfoVerified -> {
                renderDeviceInfoVerified(event)
            }

            is AqlBleProvisioningGattEvent.Failed -> {
                markProvisioningStopped()
                _uiState.value = reduceGattEvent(event)
            }

            AqlBleProvisioningGattEvent.Disconnected -> {
                if (!setupCompleted) markProvisioningStopped()
                _uiState.value = reduceGattEvent(event)
            }

            AqlBleProvisioningGattEvent.Completed -> {
                if (
                    commitJob?.isActive == true ||
                    rollbackJob?.isActive == true ||
                    registrationCommitted
                ) {
                    return
                }

                setupCompleted = true
                provisioningStopped = false
                val route = pendingAddedRoute
                val preparedSnapshot = pendingPreparedSnapshot
                if (handoffSaved && route != null && preparedSnapshot != null) {
                    _uiState.value = _uiState.value.copy(
                        title = string(R.string.device_provisioning_added_title),
                        message = string(R.string.device_provisioning_added_message_format, _uiState.value.deviceName),
                        stepThree = string(R.string.device_provisioning_opening_menu_step),
                        canStart = false,
                        buttonText = string(R.string.device_provisioning_opening),
                        showProgress = true,
                        wifiCredentialFailure = null
                    )

                    commitJob = viewModelScope.launch {
                        handoffSaver.commitPreparedRegistration(preparedSnapshot)
                            .onSuccess {
                                registrationCommitted = true
                                boundSessionId?.let { sessionId ->
                                    AqlProvisioningDraftStore.remove(sessionId)
                                }
                                pendingSavedDeviceUid = null
                                pendingPreparedSnapshot = null
                                pendingAddedRoute = null
                                if (exitRequested) {
                                    _events.send(DeviceProvisioningProgressEvent.ExitProvisioning)
                                } else {
                                    _events.send(
                                        DeviceProvisioningProgressEvent.OpenAddedDevice(route)
                                    )
                                }
                            }
                            .onFailure { error ->
                                setupCompleted = false
                                exitRequested = false
                                rollbackPendingProvisioningRegistration()
                                _uiState.value = _uiState.value.copy(
                                    title = string(R.string.device_provisioning_save_failed_title),
                                    message = error.message.toFriendlySaveError(),
                                    stepThree = string(R.string.device_provisioning_save_failed_step),
                                    canStart = true,
                                    buttonText = string(R.string.device_provisioning_start_again),
                                    showProgress = false,
                                    requiresFreshDeviceSelection = true,
                                    wifiCredentialFailure = null
                                )
                            }
                    }
                } else if (!handoffSaved) {
                    _uiState.value = _uiState.value.copy(
                        title = string(R.string.device_provisioning_setup_complete_title),
                        message = string(R.string.device_provisioning_details_received_message),
                        stepThree = string(R.string.device_provisioning_preparing_menu_step),
                        canStart = false,
                        buttonText = string(R.string.device_provisioning_saving),
                        showProgress = true,
                        wifiCredentialFailure = null
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
                    showProgress = true,
                    wifiCredentialFailure = null
                )
            }

            is AqlBleProvisioningGattEvent.Connected -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_device_found_title),
                    message = string(R.string.device_provisioning_prepare_service_message),
                    stepThree = string(R.string.device_provisioning_bluetooth_connected_step),
                    showProgress = true,
                    wifiCredentialFailure = null
                )
            }

            AqlBleProvisioningGattEvent.ServicesDiscovered -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_secure_session_title),
                    message = string(R.string.device_provisioning_secure_session_message),
                    stepThree = string(R.string.device_provisioning_secure_session_step),
                    showProgress = true,
                    wifiCredentialFailure = null
                )
            }

            AqlBleProvisioningGattEvent.StartSessionWritten -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_session_started_title),
                    message = string(R.string.device_provisioning_sending_wifi_message),
                    stepThree = string(R.string.device_provisioning_sending_wifi_step),
                    showProgress = true,
                    wifiCredentialFailure = null
                )
            }

            AqlBleProvisioningGattEvent.WifiCredentialsWritten -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_wifi_sent_title),
                    message = string(R.string.device_provisioning_wifi_connecting_message),
                    stepThree = string(R.string.device_provisioning_wifi_connecting_step),
                    showProgress = true,
                    wifiCredentialFailure = null
                )
            }

            is AqlBleProvisioningGattEvent.StatusReceived -> {
                _uiState.value.copy(
                    title = event.statusMessage.status.toProgressTitle(),
                    message = event.statusMessage.toProgressMessage(),
                    stepThree = event.statusMessage.status.toProgressStep(),
                    showProgress = event.statusMessage.status !in terminalStatuses,
                    canStart = event.statusMessage.status in retryStatuses,
                    buttonText = string(R.string.device_provisioning_try_again),
                    wifiCredentialFailure = event.statusMessage.toWifiCredentialFailure()
                )
            }

            is AqlBleProvisioningGattEvent.DeviceInfoVerified -> _uiState.value
            is AqlBleProvisioningGattEvent.RuntimeHandoffReceived -> _uiState.value
            AqlBleProvisioningGattEvent.Completed -> _uiState.value
            AqlBleProvisioningGattEvent.FinalizeSetupWritten -> _uiState.value

            is AqlBleProvisioningGattEvent.Failed -> {
                _uiState.value.copy(
                    title = string(R.string.device_provisioning_failed_title),
                    message = event.message.toFriendlyError(),
                    stepThree = string(R.string.device_provisioning_setup_stopped),
                    canStart = true,
                    buttonText = string(R.string.device_provisioning_start_again),
                    showProgress = false,
                    requiresFreshDeviceSelection = true,
                    wifiCredentialFailure = null
                )
            }

            AqlBleProvisioningGattEvent.Disconnected -> {
                if (setupCompleted) {
                    _uiState.value.copy(wifiCredentialFailure = null)
                } else {
                    _uiState.value.copy(
                        title = string(R.string.device_provisioning_connection_closed_title),
                        message = string(R.string.device_provisioning_connection_closed_message),
                        stepThree = string(R.string.device_provisioning_connection_closed_step),
                        canStart = true,
                        buttonText = string(R.string.device_provisioning_start_again),
                        showProgress = false,
                        requiresFreshDeviceSelection = true,
                        wifiCredentialFailure = null
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
            showProgress = true,
            wifiCredentialFailure = null
        )
    }

    private fun saveRuntimeHandoff(
        handoff: AqlProvisioningRuntimeHandoff
    ) {
        if (handoffSaveJob?.isActive == true || handoffSaved) {
            return
        }

        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_session_expired_title),
                message = string(R.string.device_provisioning_save_missing_session_message),
                stepThree = string(R.string.device_provisioning_save_failed_step),
                canStart = true,
                buttonText = string(R.string.device_provisioning_try_again),
                showProgress = false,
                wifiCredentialFailure = null
            )
            return
        }

        handoffSaveJob = viewModelScope.launch {
            pendingSavedDeviceUid = handoff.deviceUid

            val result = handoffSaver.prepareAndConnect(
                draft = draft,
                handoff = handoff
            )

            result.onSuccess { snapshot ->
                if (provisioningStopped || setupCompleted) {
                    handoffSaver.rollbackProvisioningRegistration(snapshot.deviceUid)
                        .onSuccess {
                            clearPendingRegistrationState()
                        }
                        .onFailure {
                            renderRollbackFailure()
                        }
                    return@onSuccess
                }

                handoffSaved = true
                pendingPreparedSnapshot = snapshot
                pendingSavedDeviceUid = snapshot.deviceUid

                val route = routeResolver.resolve(
                    snapshot = snapshot,
                    requestedDeviceUid = snapshot.deviceUid.value
                )

                pendingAddedRoute = route

                _uiState.value = _uiState.value.copy(
                    title = string(R.string.device_provisioning_setup_complete_title),
                    message = string(R.string.device_provisioning_details_received_message),
                    stepThree = string(R.string.device_provisioning_preparing_menu_step),
                    canStart = false,
                    buttonText = string(R.string.device_provisioning_saving),
                    showProgress = true,
                    wifiCredentialFailure = null
                )

                gattClient.finalizeSetup(handoff)
            }.onFailure { error ->
                val cleanupResult = handoffSaver.rollbackProvisioningRegistrationForOwner(
                    ownerUid = ownerUid,
                    deviceUid = handoff.deviceUid
                )

                if (cleanupResult.isFailure) {
                    renderRollbackFailure()
                    return@launch
                }

                cleanupResult.onSuccess {
                    clearPendingRegistrationState()
                }

                _uiState.value = _uiState.value.copy(
                    title = string(R.string.device_provisioning_save_failed_title),
                    message = error.message.toFriendlySaveError(),
                    stepThree = string(R.string.device_provisioning_save_failed_step),
                    canStart = true,
                    buttonText = string(R.string.device_provisioning_start_again),
                    showProgress = false,
                    requiresFreshDeviceSelection = true,
                    wifiCredentialFailure = null
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
            AqlProvisioningStatus.FINALIZING -> string(R.string.device_provisioning_setup_complete_title)
            AqlProvisioningStatus.COMPLETED -> string(R.string.device_provisioning_setup_complete_title)
            AqlProvisioningStatus.WIFI_FAILED -> string(R.string.device_provisioning_status_wifi_failed_title)
            AqlProvisioningStatus.CLAIM_REJECTED -> string(R.string.device_provisioning_status_claim_rejected_title)
            AqlProvisioningStatus.TIMEOUT -> string(R.string.device_provisioning_status_timeout_title)
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.UNKNOWN -> string(R.string.device_provisioning_status_waiting_title)
        }
    }

    private fun AqlBleProvisioningStatusMessage.toProgressMessage(): String {
        if (status == AqlProvisioningStatus.WIFI_FAILED) {
            return when (errorCode) {
                AqlBleProvisioningContract.ErrorCode.WIFI_AUTH_FAILED ->
                    string(R.string.device_provisioning_status_wifi_auth_failed_message)
                AqlBleProvisioningContract.ErrorCode.WIFI_NETWORK_NOT_FOUND ->
                    string(R.string.device_provisioning_status_wifi_network_not_found_message)
                AqlBleProvisioningContract.ErrorCode.WIFI_HANDSHAKE_FAILED,
                AqlBleProvisioningContract.ErrorCode.WIFI_ASSOCIATION_FAILED ->
                    string(R.string.device_provisioning_status_wifi_router_rejected_message)
                AqlBleProvisioningContract.ErrorCode.WIFI_TIMEOUT ->
                    string(R.string.device_provisioning_status_wifi_timeout_message)
                AqlBleProvisioningContract.ErrorCode.NETWORK_SAVE_FAILED ->
                    string(R.string.device_provisioning_status_wifi_save_failed_message)
                else -> message.ifBlank {
                    string(R.string.device_provisioning_status_wifi_failed_message)
                }
            }
        }
        return status.toProgressMessage(fallback = message)
    }

    private fun AqlBleProvisioningStatusMessage.toWifiCredentialFailure(): DeviceProvisioningWifiCredentialFailure? {
        if (status != AqlProvisioningStatus.WIFI_FAILED) {
            return null
        }

        return when (errorCode) {
            AqlBleProvisioningContract.ErrorCode.WIFI_AUTH_FAILED -> {
                DeviceProvisioningWifiCredentialFailure(
                    message = string(R.string.device_wifi_password_incorrect_error),
                    field = DeviceProvisioningWifiCredentialField.PASSWORD
                )
            }
            AqlBleProvisioningContract.ErrorCode.WIFI_NETWORK_NOT_FOUND -> {
                DeviceProvisioningWifiCredentialFailure(
                    message = string(R.string.device_wifi_network_not_found_error),
                    field = DeviceProvisioningWifiCredentialField.SSID
                )
            }
            AqlBleProvisioningContract.ErrorCode.WIFI_HANDSHAKE_FAILED,
            AqlBleProvisioningContract.ErrorCode.WIFI_ASSOCIATION_FAILED -> {
                DeviceProvisioningWifiCredentialFailure(
                    message = string(R.string.device_wifi_router_rejected_error),
                    field = DeviceProvisioningWifiCredentialField.PASSWORD
                )
            }
            AqlBleProvisioningContract.ErrorCode.WIFI_TIMEOUT -> {
                DeviceProvisioningWifiCredentialFailure(
                    message = string(R.string.device_wifi_connection_timeout_error),
                    field = DeviceProvisioningWifiCredentialField.PASSWORD
                )
            }
            AqlBleProvisioningContract.ErrorCode.NETWORK_SAVE_FAILED -> null
            else -> {
                DeviceProvisioningWifiCredentialFailure(
                    message = string(R.string.device_wifi_provisioning_failed_error),
                    field = DeviceProvisioningWifiCredentialField.PASSWORD
                )
            }
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
            AqlProvisioningStatus.FINALIZING -> string(R.string.device_provisioning_details_received_message)
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
            AqlProvisioningStatus.FINALIZING -> string(R.string.device_provisioning_preparing_menu_step)
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
            ProvisioningFailurePolicy.isSecureSessionFailure(normalized) ->
                string(R.string.device_provisioning_error_secure_session_ended)
            normalized.contains("StartSession is required", ignoreCase = true) ->
                string(R.string.device_provisioning_error_start_session)
            normalized.contains("WiFi", ignoreCase = true) || normalized.contains("wifi", ignoreCase = true) ->
                string(R.string.device_provisioning_error_wifi_failed)
            else -> string(R.string.device_provisioning_error_unexpected)
        }
    }

    private fun String?.toFriendlySaveError(): String {
        val normalized = orEmpty().trim()
        return when {
            ProvisioningFailurePolicy.isSecureSessionFailure(normalized) ->
                string(R.string.device_provisioning_error_secure_session_ended)
            ProvisioningFailurePolicy.isRuntimeConfirmationFailure(normalized) ->
                string(R.string.device_provisioning_error_runtime_confirmation)
            else -> string(R.string.device_provisioning_save_failed_message)
        }
    }

    private fun markProvisioningStopped() {
        provisioningStopped = true
        rollbackPendingProvisioningRegistration()
    }

    private fun rollbackPendingProvisioningRegistration() {
        val deviceUid = pendingSavedDeviceUid ?: return
        if (rollbackJob?.isActive == true) return

        rollbackJob = viewModelScope.launch {
            handoffSaver.rollbackProvisioningRegistration(deviceUid)
                .onSuccess {
                    clearPendingRegistrationState()
                }
                .onFailure {
                    if (!exitRequested) {
                        renderRollbackFailure()
                    }
                }
        }
    }

    private suspend fun renderRollbackFailure() {
        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_cancel_failed_title),
            message = string(R.string.device_provisioning_cancel_failed_message),
            stepThree = string(R.string.device_provisioning_save_failed_step),
            canStart = false,
            showProgress = false,
            isCancelling = false,
            wifiCredentialFailure = null
        )
        _events.send(
            DeviceProvisioningProgressEvent.ShowCancellationFailed
        )
    }

    private fun clearPendingRegistrationState() {
        pendingSavedDeviceUid = null
        pendingAddedRoute = null
        pendingPreparedSnapshot = null
        handoffSaved = false
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
        handoffSaveJob?.cancel()
        runCatching(gattClient::close)
            .exceptionOrNull()
            ?.printStackTrace()

        val pendingHandoffJob = handoffSaveJob
        val pendingCommitJob = commitJob
        val pendingRollbackJob = rollbackJob
        val pendingExitJob = exitJob
        val cleanupOwnerUid = ownerUid
        val cleanupSaver = handoffSaver

        PROCESS_CLEANUP_SCOPE.launch {
            pendingHandoffJob?.join()
            pendingCommitJob?.join()
            pendingRollbackJob?.join()
            pendingExitJob?.join()
            pendingSavedDeviceUid?.let { deviceUid ->
                cleanupSaver.rollbackProvisioningRegistrationForOwner(
                    ownerUid = cleanupOwnerUid,
                    deviceUid = deviceUid
                )
            }
        }

        super.onCleared()
    }

    private companion object {
        val PROCESS_CLEANUP_SCOPE = CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

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

internal object ProvisioningFailurePolicy {

    fun isSecureSessionFailure(message: String): Boolean {
        return message.contains("status 147", ignoreCase = true) ||
            message.contains("ECDH", ignoreCase = true) ||
            message.contains("BAD_DECRYPT", ignoreCase = true) ||
            message.contains("decrypt", ignoreCase = true) ||
            message.contains(
                "secure BLE provisioning session is not active",
                ignoreCase = true
            ) ||
            message.contains("GATT connection is not active", ignoreCase = true)
    }

    fun isRuntimeConfirmationFailure(message: String): Boolean {
        return message.contains("identity and capabilities", ignoreCase = true) ||
            message.contains("supported product family", ignoreCase = true)
    }
}
