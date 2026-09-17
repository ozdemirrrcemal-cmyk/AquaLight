package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation owner for the shared family Settings screen.
 *
 * The ViewModel depends only on owner-scoped application contracts. Repository identities,
 * catalog identities, transport outcomes, firmware payloads and persistence details remain below
 * presentation. Runtime freshness is owned below presentation; this ViewModel only observes the
 * already-running authoritative domain state.
 */
@Suppress("TooManyFunctions")
class DeviceFamilySettingsViewModel(
    private val settingsOperations: DeviceFamilySettingsOperations,
    private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations,
    private val manifestUrl: String = DEVICE_FIRMWARE_MANIFEST_URL
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFamilySettingsUiState())
    val uiState: StateFlow<DeviceFamilySettingsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<DeviceFamilySettingsEvent>(Channel.BUFFERED)
    val events: Flow<DeviceFamilySettingsEvent> = eventChannel.receiveAsFlow()

    private var boundDeviceUid = ""
    private var observeDeviceJob: Job? = null
    private var observeFirmwareJob: Job? = null
    private var deviceNameUpdateJob: Job? = null
    private var updateCheckJob: Job? = null
    private var automaticFirmwareCheckPending = false
    private var connectionAttemptCompleted = false

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            reset()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelBoundJobs()
        automaticFirmwareCheckPending = true
        connectionAttemptCompleted = false
        val currentSnapshot = settingsOperations.current(deviceUid)
        _uiState.value = currentSnapshot.toInitialDeviceFamilySettingsUiState()

        observeDeviceJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settingsOperations.observe(deviceUid).collect { snapshot ->
                applyDeviceSnapshot(deviceUid, snapshot)
            }
        }
        val connectionResult = settingsOperations.connect(deviceUid)
        connectionAttemptCompleted = true
        applyDeviceConnectionResult(deviceUid, connectionResult)
        val firmwareStates = firmwareUpdateOperations.observe(deviceUid)
        applyFirmwareState(firmwareStates.value)
        observeFirmwareJob = viewModelScope.launch {
            firmwareStates.collect(::applyFirmwareState)
        }
        startAutomaticFirmwareAvailabilityCheckIfReady(deviceUid, currentSnapshot)
    }

    fun updateDeviceName(value: String) {
        val deviceUid = boundDeviceUid
        val normalized = value.trim()
        val current = _uiState.value
        val canStart = deviceUid.isNotBlank() &&
            normalized.isNotBlank() &&
            normalized != current.deviceName.trim() &&
            deviceNameUpdateJob?.isActive != true
        if (!canStart) return

        startDeviceNameUpdate(
            deviceUid = deviceUid,
            customName = normalized,
            displayNameOnSuccess = normalized,
            hasCustomNameOnSuccess = true
        )
    }

    fun resetDeviceNameToDefault() {
        val deviceUid = boundDeviceUid
        val current = _uiState.value
        val canStart = deviceUid.isNotBlank() &&
            current.hasCustomDeviceName &&
            current.productDisplayName.isNotBlank() &&
            deviceNameUpdateJob?.isActive != true
        if (!canStart) return

        startDeviceNameUpdate(
            deviceUid = deviceUid,
            customName = "",
            displayNameOnSuccess = current.productDisplayName,
            hasCustomNameOnSuccess = false
        )
    }

    private fun startDeviceNameUpdate(
        deviceUid: String,
        customName: String,
        displayNameOnSuccess: String,
        hasCustomNameOnSuccess: Boolean
    ) {
        _uiState.update { state -> state.copy(deviceNameSaving = true) }
        deviceNameUpdateJob = viewModelScope.launch {
            val result = settingsOperations.updateCustomName(deviceUid, customName)
            if (boundDeviceUid != deviceUid) return@launch

            _uiState.update { state ->
                state.copy(
                    deviceName = if (result.isSuccess) {
                        displayNameOnSuccess
                    } else {
                        state.deviceName
                    },
                    hasCustomDeviceName = if (result.isSuccess) {
                        hasCustomNameOnSuccess
                    } else {
                        state.hasCustomDeviceName
                    },
                    deviceNameSaving = false
                )
            }
            if (result.isFailure) {
                eventChannel.trySend(DeviceFamilySettingsEvent.DeviceNameUpdateFailed)
            }
        }
    }

    fun checkForUpdates() {
        if (_uiState.value.firmwareLoadState != DeviceSettingsFirmwareLoadState.READY) return
        startFirmwareAvailabilityCheck(deviceUid = boundDeviceUid, automatic = false)
    }

    fun onFirmwareUpdateAction() {
        val current = _uiState.value
        when (val state = current.updateActionState) {
            DeviceSettingsUpdateActionState.Idle,
            DeviceSettingsUpdateActionState.UpToDate -> when (current.firmwareLoadState) {
                DeviceSettingsFirmwareLoadState.LOADING -> Unit
                DeviceSettingsFirmwareLoadState.READY -> checkForUpdates()
                DeviceSettingsFirmwareLoadState.CONNECTION_FAILED -> retryDeviceConnection()
            }
            DeviceSettingsUpdateActionState.Checking,
            DeviceSettingsUpdateActionState.Unsupported -> Unit
            is DeviceSettingsUpdateActionState.UpdateAvailable,
            is DeviceSettingsUpdateActionState.UpdateInProgress,
            is DeviceSettingsUpdateActionState.PostUpdateAttention ->
                eventChannel.trySend(DeviceFamilySettingsEvent.OpenFirmwareUpdate)
            is DeviceSettingsUpdateActionState.Failed -> {
                if (state.failure.canRetryAvailabilityCheck) {
                    checkForUpdates()
                } else {
                    eventChannel.trySend(DeviceFamilySettingsEvent.OpenFirmwareUpdate)
                }
            }
        }
    }

    private fun startAutomaticFirmwareAvailabilityCheckIfReady(
        deviceUid: String,
        snapshot: DeviceRootSnapshot?
    ) {
        val canStart = automaticFirmwareCheckPending &&
            connectionAttemptCompleted &&
            boundDeviceUid == deviceUid &&
            snapshot?.catalogState == DeviceRootCatalogState.VALID &&
            _uiState.value.firmwareLoadState == DeviceSettingsFirmwareLoadState.READY
        if (!canStart) return

        automaticFirmwareCheckPending = false
        startFirmwareAvailabilityCheck(deviceUid = deviceUid, automatic = true)
    }

    private fun startFirmwareAvailabilityCheck(
        deviceUid: String,
        automatic: Boolean
    ) {
        if (deviceUid.isBlank() || updateCheckJob?.isActive == true) return
        val previousActionState = _uiState.value.updateActionState
        if (!previousActionState.allowsAvailabilityCheck(automatic)) return

        updateCheckJob = viewModelScope.launch {
            val result = if (automatic) {
                firmwareUpdateOperations.refreshAvailabilityIfStale(
                    deviceUid = deviceUid,
                    manifestUrl = manifestUrl,
                    applyNow = true
                )
            } else {
                firmwareUpdateOperations.checkAvailability(
                    deviceUid = deviceUid,
                    manifestUrl = manifestUrl,
                    applyNow = true
                )
            }
            if (boundDeviceUid != deviceUid) return@launch

            val availability = result.getOrNull()
            if (
                availability is DeviceOtaState.UpdateAvailable &&
                previousActionState !is DeviceSettingsUpdateActionState.UpdateAvailable
            ) {
                firmwareUpdateOperations.requestStatus(deviceUid)
            }
        }
    }

    private fun applyFirmwareState(state: DeviceOtaState) {
        if (state.deviceUid != boundDeviceUid) return
        _uiState.update { current ->
            current.copy(updateActionState = state.toSettingsActionState())
        }
    }

    private fun retryDeviceConnection() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) return
        connectionAttemptCompleted = false
        _uiState.update { current ->
            current.copy(
                firmwareVersion = "",
                firmwareLoadState = DeviceSettingsFirmwareLoadState.LOADING
            )
        }
        val connectionResult = settingsOperations.connect(deviceUid)
        connectionAttemptCompleted = true
        applyDeviceConnectionResult(deviceUid, connectionResult)
        startAutomaticFirmwareAvailabilityCheckIfReady(
            deviceUid,
            settingsOperations.current(deviceUid)
        )
    }

    private fun applyDeviceConnectionResult(deviceUid: String, result: Result<Unit>) {
        if (boundDeviceUid != deviceUid || result.isSuccess) return
        _uiState.update { current ->
            current.copy(
                firmwareVersion = "",
                firmwareLoadState = DeviceSettingsFirmwareLoadState.CONNECTION_FAILED
            )
        }
    }

    private fun DeviceOtaState.toSettingsActionState(): DeviceSettingsUpdateActionState =
        when (this) {
            is DeviceOtaState.Idle -> DeviceSettingsUpdateActionState.Idle
            is DeviceOtaState.Checking -> DeviceSettingsUpdateActionState.Checking
            is DeviceOtaState.Unsupported -> DeviceSettingsUpdateActionState.Unsupported
            is DeviceOtaState.UpToDate,
            is DeviceOtaState.Succeeded -> DeviceSettingsUpdateActionState.UpToDate
            is DeviceOtaState.RolledBack -> postUpdateAttention(
                DeviceSettingsUpdateAttention.ROLLED_BACK
            )
            is DeviceOtaState.PostRestartTimeout -> postUpdateAttention(
                DeviceSettingsUpdateAttention.CONNECTION_TIMEOUT
            )
            is DeviceOtaState.UnexpectedFirmware -> postUpdateAttention(
                DeviceSettingsUpdateAttention.UNEXPECTED_FIRMWARE
            )
            is DeviceOtaState.UpdateAvailable -> DeviceSettingsUpdateActionState.UpdateAvailable(
                plan.targetVersion
            )
            is DeviceOtaState.Starting -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = plan.targetVersion,
                progressPermille = 0
            )
            is DeviceOtaState.InProgress -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = targetVersion,
                progressPermille = progressPermille
            )
            is DeviceOtaState.Recovering -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = targetVersion,
                progressPermille = progressPermille
            )
            is DeviceOtaState.RestartRequired -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = targetVersion,
                progressPermille = COMPLETE_PROGRESS_PERMILLE
            )
            is DeviceOtaState.Failed -> DeviceSettingsUpdateActionState.Failed(failure)
        }

    private fun postUpdateAttention(
        attention: DeviceSettingsUpdateAttention
    ): DeviceSettingsUpdateActionState =
        DeviceSettingsUpdateActionState.PostUpdateAttention(attention)

    private fun applyDeviceSnapshot(deviceUid: String, snapshot: DeviceRootSnapshot?) {
        if (snapshot == null || snapshot.catalogState != DeviceRootCatalogState.VALID) {
            preserveStableDeviceInformation(snapshot)
        } else {
            val deviceState = snapshot.toDeviceFamilySettingsUiState()
            _uiState.value = deviceState.copy(
                deviceNameSaving = _uiState.value.deviceNameSaving,
                updateActionState = _uiState.value.updateActionState,
                informationLoadState = DeviceSettingsInformationLoadState.READY
            )
        }

        startAutomaticFirmwareAvailabilityCheckIfReady(deviceUid, snapshot)
    }

    private fun preserveStableDeviceInformation(snapshot: DeviceRootSnapshot?) {
        _uiState.update { current ->
            val nameSnapshot = snapshot?.takeIf { it.productDisplayName.isNotBlank() }
            current.copy(
                deviceName = when {
                    nameSnapshot != null -> nameSnapshot.title
                    current.deviceName.isNotBlank() -> current.deviceName
                    else -> snapshot?.title.orEmpty()
                },
                productDisplayName = nameSnapshot?.productDisplayName
                    ?: current.productDisplayName,
                hasCustomDeviceName = nameSnapshot?.hasCustomName
                    ?: current.hasCustomDeviceName,
                serialNumber = current.serialNumber.ifBlank {
                    snapshot?.serialNumber.orEmpty()
                },
                firmwareVersion = "",
                firmwareLoadState = if (
                    current.firmwareLoadState ==
                    DeviceSettingsFirmwareLoadState.CONNECTION_FAILED
                ) {
                    DeviceSettingsFirmwareLoadState.CONNECTION_FAILED
                } else {
                    DeviceSettingsFirmwareLoadState.LOADING
                },
                informationLoadState = if (current.hardwareRevision.isNotBlank()) {
                    DeviceSettingsInformationLoadState.READY
                } else {
                    DeviceSettingsInformationLoadState.LOADING
                }
            )
        }
    }

    private fun reset() {
        cancelBoundJobs()
        boundDeviceUid = ""
        automaticFirmwareCheckPending = false
        connectionAttemptCompleted = false
        _uiState.value = DeviceFamilySettingsUiState()
    }

    private fun cancelBoundJobs() {
        observeDeviceJob?.cancel()
        observeFirmwareJob?.cancel()
        deviceNameUpdateJob?.cancel()
        updateCheckJob?.cancel()
    }

    override fun onCleared() {
        cancelBoundJobs()
        eventChannel.close()
        super.onCleared()
    }

    private companion object {
        const val COMPLETE_PROGRESS_PERMILLE = 1_000
    }
}

sealed interface DeviceFamilySettingsEvent {
    data object DeviceNameUpdateFailed : DeviceFamilySettingsEvent
    data object OpenFirmwareUpdate : DeviceFamilySettingsEvent
}

enum class DeviceSettingsInformationLoadState {
    LOADING,
    READY
}

enum class DeviceSettingsFirmwareLoadState {
    LOADING,
    READY,
    CONNECTION_FAILED
}

sealed interface DeviceSettingsUpdateActionState {
    data object Idle : DeviceSettingsUpdateActionState
    data object Checking : DeviceSettingsUpdateActionState
    data object UpToDate : DeviceSettingsUpdateActionState

    data class UpdateAvailable(
        val version: String
    ) : DeviceSettingsUpdateActionState

    data class UpdateInProgress(
        val version: String,
        val progressPermille: Int
    ) : DeviceSettingsUpdateActionState

    data class Failed(
        val failure: DeviceOtaFailure
    ) : DeviceSettingsUpdateActionState

    data class PostUpdateAttention(
        val kind: DeviceSettingsUpdateAttention
    ) : DeviceSettingsUpdateActionState

    data object Unsupported : DeviceSettingsUpdateActionState
}

enum class DeviceSettingsUpdateAttention {
    ROLLED_BACK,
    CONNECTION_TIMEOUT,
    UNEXPECTED_FIRMWARE
}

internal val DeviceOtaFailure.canRetryAvailabilityCheck: Boolean
    get() = stage == DeviceOtaFailureStage.AVAILABILITY_CHECK && recoverable

private fun DeviceSettingsUpdateActionState.allowsAvailabilityCheck(
    automatic: Boolean
): Boolean = when (this) {
    DeviceSettingsUpdateActionState.Idle,
    DeviceSettingsUpdateActionState.UpToDate -> true
    is DeviceSettingsUpdateActionState.Failed ->
        !automatic && failure.canRetryAvailabilityCheck
    DeviceSettingsUpdateActionState.Checking,
    is DeviceSettingsUpdateActionState.UpdateAvailable,
    is DeviceSettingsUpdateActionState.UpdateInProgress,
    is DeviceSettingsUpdateActionState.PostUpdateAttention,
    DeviceSettingsUpdateActionState.Unsupported -> false
}

data class DeviceFamilySettingsUiState(
    val deviceName: String = "",
    val productDisplayName: String = "",
    val hasCustomDeviceName: Boolean = false,
    val serialNumber: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val deviceNameSaving: Boolean = false,
    val informationLoadState: DeviceSettingsInformationLoadState =
        DeviceSettingsInformationLoadState.LOADING,
    val firmwareLoadState: DeviceSettingsFirmwareLoadState =
        DeviceSettingsFirmwareLoadState.LOADING,
    val updateActionState: DeviceSettingsUpdateActionState =
        DeviceSettingsUpdateActionState.Idle
)

internal fun DeviceRootSnapshot.toDeviceFamilySettingsUiState(): DeviceFamilySettingsUiState {
    val hasAuthoritativeFirmware = catalogState == DeviceRootCatalogState.VALID &&
        firmwareLabel.isNotBlank()
    return DeviceFamilySettingsUiState(
        deviceName = title,
        productDisplayName = productDisplayName,
        hasCustomDeviceName = hasCustomName,
        serialNumber = serialNumber,
        hardwareRevision = hardwareRevision,
        firmwareVersion = firmwareLabel.takeIf {
            hasAuthoritativeFirmware
        }.orEmpty(),
        family = family,
        informationLoadState = if (
            catalogState == DeviceRootCatalogState.VALID && hardwareRevision.isNotBlank()
        ) {
            DeviceSettingsInformationLoadState.READY
        } else {
            DeviceSettingsInformationLoadState.LOADING
        },
        firmwareLoadState = if (hasAuthoritativeFirmware) {
            DeviceSettingsFirmwareLoadState.READY
        } else {
            DeviceSettingsFirmwareLoadState.LOADING
        }
    )
}

private fun DeviceRootSnapshot?.toInitialDeviceFamilySettingsUiState(): DeviceFamilySettingsUiState =
    this?.toDeviceFamilySettingsUiState()
        ?: DeviceFamilySettingsUiState()
