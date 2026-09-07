package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
import com.aqua.aqualight.application.devices.DeviceLightProtectionThresholdPolicy
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
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
    private var observeLightProtectionJob: Job? = null
    private var deviceNameUpdateJob: Job? = null
    private var thresholdUpdateJob: Job? = null
    private var updateCheckJob: Job? = null
    private var automaticFirmwareCheckPending = false

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
        val currentSnapshot = settingsOperations.current(deviceUid)
        _uiState.value = currentSnapshot
            .toInitialDeviceFamilySettingsUiState(deviceUid)
            .withLightProtectionSnapshot(
                settingsOperations.currentLightProtection(deviceUid)
            )
        settingsOperations.connect(deviceUid)

        observeDeviceJob = viewModelScope.launch {
            settingsOperations.observe(deviceUid).collect { snapshot ->
                applyDeviceSnapshot(deviceUid, snapshot)
            }
        }
        val firmwareStates = firmwareUpdateOperations.observe(deviceUid)
        applyFirmwareState(firmwareStates.value)
        observeFirmwareJob = viewModelScope.launch {
            firmwareStates.collect(::applyFirmwareState)
        }
        observeLightProtectionJob = viewModelScope.launch {
            settingsOperations.observeLightProtection(deviceUid).collect { snapshot ->
                applyLightProtectionSnapshot(deviceUid, snapshot)
            }
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

    fun updateTemperatureProtectionThreshold(valueCelsius: Int) {
        val deviceUid = boundDeviceUid
        val state = _uiState.value
        val editor = state.lightProtection.editor
        val canStart = deviceUid.isNotBlank() &&
            editor != null &&
            valueCelsius in editor.minimumCelsius..editor.maximumCelsius &&
            (valueCelsius - editor.minimumCelsius) % editor.stepCelsius == 0 &&
            valueCelsius != editor.currentCelsius &&
            thresholdUpdateJob?.isActive != true
        if (!canStart) return

        _uiState.update { current ->
            current.copy(
                lightProtection = current.lightProtection.copy(updateInProgress = true)
            )
        }
        thresholdUpdateJob = viewModelScope.launch {
            val result = settingsOperations.updateLightProtectionThreshold(
                deviceUid = deviceUid,
                thresholdCelsius = valueCelsius
            )
            if (boundDeviceUid != deviceUid) return@launch

            _uiState.update { current ->
                val light = current.lightProtection
                current.copy(
                    lightProtection = light.copy(
                        thresholdCelsius = if (result.isSuccess) {
                            valueCelsius.toDouble()
                        } else {
                            light.thresholdCelsius
                        },
                        editor = if (result.isSuccess) {
                            light.editor?.copy(currentCelsius = valueCelsius)
                        } else {
                            light.editor
                        },
                        updateInProgress = false
                    )
                )
            }
            if (result.isFailure) {
                eventChannel.trySend(
                    DeviceFamilySettingsEvent.TemperatureProtectionUpdateFailed
                )
            }
        }
    }

    /**
     * Kept temporarily for the existing Fragment callback surface. Runtime/domain bootstrap owns
     * freshness; presentation must never issue a status request or retry loop.
     */
    fun retryLightProtection() = Unit

    fun checkForUpdates() {
        startFirmwareAvailabilityCheck(deviceUid = boundDeviceUid, automatic = false)
    }

    fun onFirmwareUpdateAction() {
        when (val state = _uiState.value.updateActionState) {
            DeviceSettingsUpdateActionState.Idle,
            DeviceSettingsUpdateActionState.UpToDate -> checkForUpdates()
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
            boundDeviceUid == deviceUid &&
            snapshot?.catalogState == DeviceRootCatalogState.VALID
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

    private fun applyLightProtectionSnapshot(
        deviceUid: String,
        snapshot: DeviceLightProtectionSnapshot
    ) {
        if (boundDeviceUid != deviceUid) return
        _uiState.update { state -> state.withLightProtectionSnapshot(snapshot) }
    }

    private fun applyFirmwareState(state: DeviceOtaState) {
        if (state.deviceUid != boundDeviceUid) return
        _uiState.update { current ->
            current.copy(updateActionState = state.toSettingsActionState())
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
            preserveStableDeviceInformation(deviceUid, snapshot)
        } else {
            val previous = _uiState.value
            val deviceState = snapshot.toDeviceFamilySettingsUiState()
            _uiState.value = deviceState.copy(
                showLightProtectionInventory = previous.showLightProtectionInventory,
                deviceNameSaving = previous.deviceNameSaving,
                lightProtection = previous.lightProtection,
                updateActionState = previous.updateActionState,
                informationLoadState = DeviceSettingsInformationLoadState.READY
            )
        }

        startAutomaticFirmwareAvailabilityCheckIfReady(deviceUid, snapshot)
    }

    private fun preserveStableDeviceInformation(
        deviceUid: String,
        snapshot: DeviceRootSnapshot?
    ) {
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
                    snapshot?.serialNumber?.ifBlank { deviceUid } ?: deviceUid
                },
                firmwareVersion = current.firmwareVersion.ifBlank {
                    snapshot?.firmwareLabel.orEmpty()
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
        _uiState.value = DeviceFamilySettingsUiState()
    }

    private fun cancelBoundJobs() {
        observeDeviceJob?.cancel()
        observeFirmwareJob?.cancel()
        observeLightProtectionJob?.cancel()
        deviceNameUpdateJob?.cancel()
        thresholdUpdateJob?.cancel()
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
    data object TemperatureProtectionUpdateFailed : DeviceFamilySettingsEvent
    data object OpenFirmwareUpdate : DeviceFamilySettingsEvent
}

enum class DeviceSettingsInformationLoadState {
    LOADING,
    READY
}

enum class DeviceLightProtectionLoadState {
    IDLE,
    LOADING,
    READY,
    FAILED
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

data class DeviceTemperatureProtectionEditorUiState(
    val currentCelsius: Int,
    val minimumCelsius: Int,
    val maximumCelsius: Int,
    val stepCelsius: Int
)

data class DeviceLightProtectionUiState(
    val currentTemperatureCelsius: Double? = null,
    val thresholdCelsius: Double? = null,
    val editor: DeviceTemperatureProtectionEditorUiState? = null,
    val loadState: DeviceLightProtectionLoadState = DeviceLightProtectionLoadState.IDLE,
    val updateInProgress: Boolean = false
)

data class DeviceFamilySettingsUiState(
    val deviceName: String = "",
    val productDisplayName: String = "",
    val hasCustomDeviceName: Boolean = false,
    val serialNumber: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val showLightProtectionInventory: Boolean = false,
    val deviceNameSaving: Boolean = false,
    val lightProtection: DeviceLightProtectionUiState = DeviceLightProtectionUiState(),
    val informationLoadState: DeviceSettingsInformationLoadState =
        DeviceSettingsInformationLoadState.LOADING,
    val updateActionState: DeviceSettingsUpdateActionState =
        DeviceSettingsUpdateActionState.Idle
)

internal fun DeviceRootSnapshot.toDeviceFamilySettingsUiState(): DeviceFamilySettingsUiState =
    DeviceFamilySettingsUiState(
        deviceName = title,
        productDisplayName = productDisplayName,
        hasCustomDeviceName = hasCustomName,
        serialNumber = serialNumber.ifBlank { deviceUid },
        hardwareRevision = hardwareRevision,
        firmwareVersion = firmwareLabel,
        family = family,
        informationLoadState = if (
            catalogState == DeviceRootCatalogState.VALID && hardwareRevision.isNotBlank()
        ) {
            DeviceSettingsInformationLoadState.READY
        } else {
            DeviceSettingsInformationLoadState.LOADING
        }
    )

internal fun DeviceFamilySettingsUiState.withLightProtectionSnapshot(
    snapshot: DeviceLightProtectionSnapshot
): DeviceFamilySettingsUiState {
    if (!snapshot.available) {
        return copy(
            showLightProtectionInventory = false,
            lightProtection = DeviceLightProtectionUiState()
        )
    }

    val previous = lightProtection
    return copy(
        showLightProtectionInventory = true,
        lightProtection = previous.copy(
            currentTemperatureCelsius = snapshot.currentTemperatureCelsius
                ?: previous.currentTemperatureCelsius.takeIf { !snapshot.loaded },
            thresholdCelsius = snapshot.thresholdCelsius
                ?: previous.thresholdCelsius.takeIf { !snapshot.loaded },
            editor = when {
                snapshot.loaded -> snapshot.thresholdPolicy?.toEditorUiState()
                snapshot.thresholdPolicy != null -> snapshot.thresholdPolicy.toEditorUiState()
                else -> previous.editor
            },
            loadState = when {
                snapshot.loaded -> DeviceLightProtectionLoadState.READY
                previous.loadState == DeviceLightProtectionLoadState.IDLE ->
                    DeviceLightProtectionLoadState.LOADING
                else -> previous.loadState
            }
        )
    )
}

private fun DeviceLightProtectionThresholdPolicy.toEditorUiState():
    DeviceTemperatureProtectionEditorUiState = DeviceTemperatureProtectionEditorUiState(
    currentCelsius = currentCelsius,
    minimumCelsius = minimumCelsius,
    maximumCelsius = maximumCelsius,
    stepCelsius = stepCelsius
)

private fun DeviceRootSnapshot?.toInitialDeviceFamilySettingsUiState(
    deviceUid: String
): DeviceFamilySettingsUiState {
    return this?.toDeviceFamilySettingsUiState()
        ?: DeviceFamilySettingsUiState(serialNumber = deviceUid)
}
