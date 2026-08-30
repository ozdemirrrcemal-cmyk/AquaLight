package com.aqua.aqualight.ui.tabs.devices.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.provisioning.PreparedProvisioningRegistration
import com.aqua.aqualight.application.devices.provisioning.ProvisionedDevice
import com.aqua.aqualight.application.devices.provisioning.ProvisioningProgressOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningRuntimeHandoff
import com.aqua.aqualight.application.devices.provisioning.ProvisioningSessionSnapshot
import com.aqua.aqualight.application.devices.provisioning.ProvisioningTransportEvent
import com.aqua.aqualight.application.devices.provisioning.ProvisioningVerifiedDeviceInfo
import com.aqua.aqualight.application.text.AppTextResolver
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
    private val operations: ProvisioningProgressOperations,
    menuOpenUseCase: DeviceMenuOpenUseCase,
    private val textResolver: AppTextResolver
) : ViewModel() {

    private val ownerUid = operations.ownerUid
    private val presenter = ProvisioningProgressPresenter(textResolver)
    private val reducer = DeviceProvisioningProgressReducer(presenter, textResolver)

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<DeviceProvisioningProgressUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceProvisioningProgressEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    internal val preparedNavigation = DeviceProvisioningPreparedNavigation(
        menuOpenUseCase = menuOpenUseCase,
        textResolver = textResolver,
        uiState = _uiState,
        events = _events
    )

    private var boundSessionId: String? = null
    private var activeSession: ProvisioningSessionSnapshot? = null
    private var verifiedDeviceInfo: ProvisioningVerifiedDeviceInfo? = null
    private var transportEventsJob: Job? = null
    private var handoffSaved = false
    private var provisioningStopped = false
    private var setupCompleted = false
    private var startJob: Job? = null
    private var handoffSaveJob: Job? = null
    private var commitJob: Job? = null
    private var rollbackJob: Job? = null
    private var exitJob: Job? = null
    private var exitRequested = false
    private var registrationCommitted = false
    private var pendingAddedDevice: ProvisionedDevice? = null
    private var pendingPreparedRegistration: PreparedProvisioningRegistration? = null
    private var pendingSavedDeviceUid: String? = null

    fun bind(sessionId: String) {
        if (sessionId.isBlank() || boundSessionId == sessionId) return

        boundSessionId = sessionId
        val session = operations.getSession(sessionId)
        activeSession = session

        if (session == null) {
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
            deviceName = session.deviceTitle.ifBlank {
                string(R.string.device_wifi_default_device_name)
            },
            deviceSerial = session.deviceSerial.ifBlank {
                string(R.string.device_provisioning_unknown)
            },
            bleAddress = session.bleAddress.ifBlank {
                session.bleName.ifBlank {
                    string(R.string.device_provisioning_qr_device_will_be_located)
                }
            },
            wifiSsid = session.wifiSsid,
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
        if (exitJob?.isActive == true || registrationCommitted) return

        exitRequested = true
        if (commitJob?.isActive == true) return

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
            transportEventsJob?.cancelAndJoin()
            runCatching(operations::closeTransport)
                .exceptionOrNull()
                ?.printStackTrace()

            val deviceUid = pendingSavedDeviceUid
            val rollbackResult = if (deviceUid == null) {
                Result.success(Unit)
            } else {
                operations.rollbackProvisioningRegistrationForOwner(ownerUid, deviceUid)
            }

            rollbackResult
                .onSuccess {
                    clearPendingRegistrationState()
                    boundSessionId?.let(operations::removeSession)
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
        ) return

        val session = activeSession ?: run {
            renderExpiredSession()
            return
        }

        startJob?.cancel()
        handoffSaved = false
        provisioningStopped = false
        setupCompleted = false
        verifiedDeviceInfo = null
        pendingAddedDevice = null
        pendingPreparedRegistration = null
        pendingSavedDeviceUid = null
        exitRequested = false
        registrationCommitted = false
        observeTransportEvents()

        startJob = viewModelScope.launch {
            val bleAddress = resolveBleAddressIfNeeded(session) ?: return@launch
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_connecting_title),
                message = string(R.string.device_provisioning_connecting_message),
                bleAddress = bleAddress,
                stepThree = string(R.string.device_provisioning_connecting_step),
                canStart = false,
                buttonText = string(R.string.device_provisioning_running),
                showProgress = true,
                wifiCredentialFailure = null
            )
            operations.startTransport(session.sessionId, bleAddress)
                .onFailure(::renderTransportStartFailure)
        }
    }

    private suspend fun resolveBleAddressIfNeeded(
        session: ProvisioningSessionSnapshot
    ): String? {
        val existingAddress = session.bleAddress.trim()
        val bleName = session.bleName.trim()

        if (bleName.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_locating_qr_title),
                message = string(
                    R.string.device_provisioning_locating_qr_message_format,
                    bleName
                ),
                bleAddress = bleName,
                stepThree = string(R.string.device_provisioning_locating_qr_step),
                canStart = false,
                buttonText = string(R.string.device_provisioning_finding),
                showProgress = true,
                wifiCredentialFailure = null
            )

            operations.resolveBleAddress(session.sessionId)
                .onSuccess { address ->
                    if (address.isNotBlank()) return address
                }
                .onFailure {
                    if (existingAddress.isBlank()) {
                        renderQrDeviceNotFound()
                        return null
                    }
                }
        }

        if (existingAddress.isNotBlank()) return existingAddress

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

    private fun observeTransportEvents() {
        if (transportEventsJob != null) return
        transportEventsJob = viewModelScope.launch {
            operations.events.collect(::handleTransportEvent)
        }
    }

    private fun handleTransportEvent(event: ProvisioningTransportEvent) {
        when (event) {
            ProvisioningTransportEvent.FinalizeSetupWritten -> {
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
            is ProvisioningTransportEvent.RuntimeHandoffReceived -> {
                renderRuntimeHandoffReceived()
                saveRuntimeHandoff(event.handoff)
            }
            is ProvisioningTransportEvent.DeviceInfoVerified -> {
                verifiedDeviceInfo = event.info
                _uiState.value = _uiState.value.copy(
                    deviceName = event.info.title.ifBlank { _uiState.value.deviceName },
                    deviceSerial = event.info.serial.ifBlank { _uiState.value.deviceSerial }
                )
            }
            is ProvisioningTransportEvent.Failed -> {
                markProvisioningStopped()
                _uiState.value = reduceTransportEvent(event)
            }
            ProvisioningTransportEvent.Disconnected -> {
                if (!setupCompleted) markProvisioningStopped()
                _uiState.value = reduceTransportEvent(event)
            }
            ProvisioningTransportEvent.Completed -> handleCompletedEvent()
            else -> _uiState.value = reduceTransportEvent(event)
        }
    }

    private fun handleCompletedEvent() {
        if (
            commitJob?.isActive == true ||
            rollbackJob?.isActive == true ||
            registrationCommitted
        ) return

        setupCompleted = true
        provisioningStopped = false
        val device = pendingAddedDevice
        val registration = pendingPreparedRegistration

        if (handoffSaved && device != null && registration != null) {
            _uiState.value = _uiState.value.copy(
                title = string(R.string.device_provisioning_added_title),
                message = string(
                    R.string.device_provisioning_added_message_format,
                    _uiState.value.deviceName
                ),
                stepThree = string(R.string.device_provisioning_opening_menu_step),
                canStart = false,
                buttonText = string(R.string.device_provisioning_opening),
                showProgress = true,
                wifiCredentialFailure = null
            )

            commitJob = viewModelScope.launch {
                operations.commitPreparedRegistration(registration)
                    .onSuccess {
                        registrationCommitted = true
                        boundSessionId?.let(operations::removeSession)
                        pendingSavedDeviceUid = null
                        pendingPreparedRegistration = null
                        pendingAddedDevice = null
                        if (exitRequested) {
                            _events.send(DeviceProvisioningProgressEvent.ExitProvisioning)
                        } else {
                            preparedNavigation.open(device)
                        }
                    }
                    .onFailure { error ->
                        setupCompleted = false
                        exitRequested = false
                        rollbackPendingProvisioningRegistration()
                        _uiState.value = _uiState.value.copy(
                            title = string(R.string.device_provisioning_save_failed_title),
                            message = presenter.friendlySaveError(error.message),
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

    private fun reduceTransportEvent(
        event: ProvisioningTransportEvent
    ): DeviceProvisioningProgressUiState = reducer.reduce(
        current = _uiState.value,
        event = event,
        setupCompleted = setupCompleted
    )

    private fun renderRuntimeHandoffReceived() {
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

    private fun saveRuntimeHandoff(handoff: ProvisioningRuntimeHandoff) {
        if (handoffSaveJob?.isActive == true || handoffSaved) return

        val session = activeSession ?: run {
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
            operations.prepareRegistration(
                sessionId = session.sessionId,
                verifiedDeviceInfo = verifiedDeviceInfo,
                handoff = handoff
            ).onSuccess { registration ->
                if (provisioningStopped || setupCompleted) {
                    operations.rollbackProvisioningRegistration(
                        registration.device.deviceUid
                    ).onSuccess {
                        clearPendingRegistrationState()
                    }.onFailure {
                        renderRollbackFailure()
                    }
                    return@onSuccess
                }

                handoffSaved = true
                pendingPreparedRegistration = registration
                pendingSavedDeviceUid = registration.device.deviceUid
                pendingAddedDevice = registration.device
                _uiState.value = _uiState.value.copy(
                    title = string(R.string.device_provisioning_setup_complete_title),
                    message = string(R.string.device_provisioning_details_received_message),
                    stepThree = string(R.string.device_provisioning_preparing_menu_step),
                    canStart = false,
                    buttonText = string(R.string.device_provisioning_saving),
                    showProgress = true,
                    wifiCredentialFailure = null
                )
                operations.finalizeSetup(handoff)
                    .onFailure { error ->
                        markProvisioningStopped()
                        _uiState.value = _uiState.value.copy(
                            title = string(R.string.device_provisioning_failed_title),
                            message = presenter.friendlyTransportError(error.message.orEmpty()),
                            stepThree = string(R.string.device_provisioning_setup_stopped),
                            canStart = true,
                            buttonText = string(R.string.device_provisioning_start_again),
                            showProgress = false,
                            requiresFreshDeviceSelection = true,
                            wifiCredentialFailure = null
                        )
                    }
            }.onFailure { error ->
                val cleanupResult = operations.rollbackProvisioningRegistrationForOwner(
                    ownerUid = ownerUid,
                    deviceUid = handoff.deviceUid
                )
                if (cleanupResult.isFailure) {
                    renderRollbackFailure()
                    return@launch
                }
                clearPendingRegistrationState()
                _uiState.value = _uiState.value.copy(
                    title = string(R.string.device_provisioning_save_failed_title),
                    message = presenter.friendlySaveError(error.message),
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

    private fun renderExpiredSession() {
        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_session_expired_title),
            message = string(R.string.device_provisioning_session_expired_message),
            canStart = false,
            buttonText = string(R.string.device_provisioning_unavailable),
            showProgress = false,
            wifiCredentialFailure = null
        )
    }

    private fun renderQrDeviceNotFound() {
        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_qr_not_found_title),
            message = string(R.string.device_provisioning_qr_not_found_message),
            stepThree = string(R.string.device_provisioning_device_not_found_title),
            canStart = true,
            buttonText = string(R.string.device_provisioning_try_again),
            showProgress = false,
            wifiCredentialFailure = null
        )
    }

    private fun renderTransportStartFailure(error: Throwable) {
        markProvisioningStopped()
        _uiState.value = _uiState.value.copy(
            title = string(R.string.device_provisioning_failed_title),
            message = presenter.friendlyTransportError(error.message.orEmpty()),
            stepThree = string(R.string.device_provisioning_setup_stopped),
            canStart = true,
            buttonText = string(R.string.device_provisioning_start_again),
            showProgress = false,
            requiresFreshDeviceSelection = true,
            wifiCredentialFailure = null
        )
    }

    private fun markProvisioningStopped() {
        provisioningStopped = true
        rollbackPendingProvisioningRegistration()
    }

    private fun rollbackPendingProvisioningRegistration() {
        val deviceUid = pendingSavedDeviceUid ?: return
        if (rollbackJob?.isActive == true) return

        rollbackJob = viewModelScope.launch {
            operations.rollbackProvisioningRegistration(deviceUid)
                .onSuccess { clearPendingRegistrationState() }
                .onFailure {
                    if (!exitRequested) renderRollbackFailure()
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
        _events.send(DeviceProvisioningProgressEvent.ShowCancellationFailed)
    }

    private fun clearPendingRegistrationState() {
        pendingSavedDeviceUid = null
        pendingAddedDevice = null
        pendingPreparedRegistration = null
        handoffSaved = false
    }

    private fun initialState(): DeviceProvisioningProgressUiState =
        DeviceProvisioningProgressUiState(
            title = string(R.string.device_provisioning_default_title),
            message = string(R.string.device_provisioning_default_message),
            stepOne = string(R.string.device_provisioning_step_device_selected),
            stepTwo = string(R.string.device_provisioning_step_wifi_prepared),
            stepThree = string(R.string.device_provisioning_step_preparing_secure),
            buttonText = string(R.string.device_provisioning_try_again)
        )

    private fun string(resId: Int, vararg args: Any): String =
        textResolver.get(resId, *args)

    override fun onCleared() {
        startJob?.cancel()
        transportEventsJob?.cancel()
        handoffSaveJob?.cancel()
        runCatching(operations::closeTransport)
            .exceptionOrNull()
            ?.printStackTrace()

        val pendingHandoffJob = handoffSaveJob
        val pendingCommitJob = commitJob
        val pendingRollbackJob = rollbackJob
        val pendingExitJob = exitJob
        val cleanupDeviceUid = pendingSavedDeviceUid
        val cleanupOperations = operations
        val cleanupOwnerUid = ownerUid

        PROCESS_CLEANUP_SCOPE.launch {
            pendingHandoffJob?.join()
            pendingCommitJob?.join()
            pendingRollbackJob?.join()
            pendingExitJob?.join()
            cleanupDeviceUid?.let { deviceUid ->
                cleanupOperations.rollbackProvisioningRegistrationForOwner(
                    ownerUid = cleanupOwnerUid,
                    deviceUid = deviceUid
                )
            }
        }

        super.onCleared()
    }

    private companion object {
        val PROCESS_CLEANUP_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
