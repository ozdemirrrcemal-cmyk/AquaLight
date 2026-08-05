package com.aqua.aqualight.ui.tabs.devices.detail.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
import com.aqua.aqualight.application.devices.DeviceLightProtectionThresholdPolicy
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
 * presentation.
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
    private var lightProtectionRefreshJob: Job? = null
    private var deviceNameUpdateJob: Job? = null
    private var thresholdUpdateJob: Job? = null
    private var updateCheckJob: Job? = null
    private var lastDeviceAvailability: OwnerDeviceAvailability? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            reset()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelBoundJobs()
        val currentSnapshot = settingsOperations.current(deviceUid)
        lastDeviceAvailability = currentSnapshot?.availability
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
        observeFirmwareJob = viewModelScope.launch {
            firmwareUpdateOperations.observe(deviceUid).collect(::applyFirmwareState)
        }
        observeLightProtectionJob = viewModelScope.launch {
            settingsOperations.observeLightProtection(deviceUid).collect { snapshot ->
                applyLightProtectionSnapshot(deviceUid, snapshot)
            }
        }
        requestLightProtectionRefreshIfNeeded(deviceUid)
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

    fun retryLightProtection() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) return
        requestLightProtectionRefreshIfNeeded(deviceUid, force = true)
    }

    fun checkForUpdates() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank() || updateCheckJob?.isActive == true) return
        if (_uiState.value.updateActionState is DeviceSettingsUpdateActionState.UpdateInProgress) {
            return
        }

        updateCheckJob = viewModelScope.launch {
            val availability = firmwareUpdateOperations.checkAvailability(
                deviceUid = deviceUid,
                manifestUrl = manifestUrl,
                applyNow = true
            ).getOrNull()
            if (availability is DeviceOtaState.UpdateAvailable) {
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
        requestLightProtectionRefreshIfNeeded(deviceUid)
    }

    private fun requestLightProtectionRefreshIfNeeded(
        deviceUid: String,
        force: Boolean = false
    ) {
        val lightState = _uiState.value.lightProtection
        val automaticRefreshAllowed = lightState.loadState == DeviceLightProtectionLoadState.IDLE ||
            lightState.loadState == DeviceLightProtectionLoadState.LOADING
        val shouldRequest = boundDeviceUid == deviceUid &&
            _uiState.value.showLightProtectionInventory &&
            lightProtectionRefreshJob?.isActive != true &&
            (force || automaticRefreshAllowed)
        if (!shouldRequest) return

        if (lightState.loadState != DeviceLightProtectionLoadState.READY) {
            _uiState.update { current ->
                current.copy(
                    lightProtection = current.lightProtection.copy(
                        loadState = DeviceLightProtectionLoadState.LOADING
                    )
                )
            }
        }
        lightProtectionRefreshJob = viewModelScope.launch {
            refreshLightProtectionWithRetry(deviceUid)
        }
    }

    private suspend fun refreshLightProtectionWithRetry(deviceUid: String) {
        var loaded = false
        for (attempt in 0 until LIGHT_PROTECTION_REFRESH_MAX_ATTEMPTS) {
            if (attempt > 0) delay(LIGHT_PROTECTION_REFRESH_RETRY_DELAY_MILLIS)
            if (boundDeviceUid != deviceUid) return

            val result = settingsOperations.refreshLightProtection(deviceUid)
            if (boundDeviceUid != deviceUid) return
            if (result.isSuccess) {
                val snapshot = settingsOperations.currentLightProtection(deviceUid)
                applyLightProtectionSnapshot(deviceUid, snapshot)
                loaded = snapshot.loaded ||
                    _uiState.value.lightProtection.loadState ==
                    DeviceLightProtectionLoadState.READY
            }
            if (loaded) break
        }

        if (
            boundDeviceUid == deviceUid &&
            _uiState.value.lightProtection.loadState != DeviceLightProtectionLoadState.READY
        ) {
            _uiState.update { current ->
                current.copy(
                    lightProtection = current.lightProtection.copy(
                        loadState = DeviceLightProtectionLoadState.FAILED
                    )
                )
            }
        }
    }

    private fun applyFirmwareState(state: DeviceOtaState) {
        if (state.deviceUid != boundDeviceUid) return
        val actionState = when (state) {
            is DeviceOtaState.Idle -> DeviceSettingsUpdateActionState.Idle
            is DeviceOtaState.Checking -> DeviceSettingsUpdateActionState.Checking
            is DeviceOtaState.Unsupported -> DeviceSettingsUpdateActionState.Unsupported
            is DeviceOtaState.UpToDate,
            is DeviceOtaState.Succeeded -> DeviceSettingsUpdateActionState.UpToDate
            is DeviceOtaState.UpdateAvailable -> DeviceSettingsUpdateActionState.UpdateAvailable(
                state.plan.targetVersion
            )
            is DeviceOtaState.Starting -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.plan.targetVersion,
                progressPermille = 0
            )
            is DeviceOtaState.InProgress -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.targetVersion,
                progressPermille = state.progressPermille
            )
            is DeviceOtaState.Recovering -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.targetVersion,
                progressPermille = state.progressPermille
            )
            is DeviceOtaState.RestartRequired -> DeviceSettingsUpdateActionState.UpdateInProgress(
                version = state.targetVersion,
                progressPermille = COMPLETE_PROGRESS_PERMILLE
            )
            is DeviceOtaState.Failed -> DeviceSettingsUpdateActionState.Failed(state.failure)
        }
        _uiState.update { current -> current.copy(updateActionState = actionState) }
    }

    private fun applyDeviceSnapshot(deviceUid: String, snapshot: DeviceRootSnapshot?) {
        val becameReachable = recordAvailability(snapshot?.availability)
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

        if (becameReachable) {
            requestLightProtectionRefreshIfNeeded(deviceUid, force = true)
        }
    }

    private fun recordAvailability(
        availability: OwnerDeviceAvailability?
    ): Boolean {
        val previous = lastDeviceAvailability
        if (availability != null) lastDeviceAvailability = availability
        return previous == OwnerDeviceAvailability.UNREACHABLE &&
            availability == OwnerDeviceAvailability.REACHABLE
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
        lastDeviceAvailability = null
        _uiState.value = DeviceFamilySettingsUiState()
    }

    private fun cancelBoundJobs() {
        observeDeviceJob?.cancel()
        observeFirmwareJob?.cancel()
        observeLightProtectionJob?.cancel()
        lightProtectionRefreshJob?.cancel()
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
        const val LIGHT_PROTECTION_REFRESH_MAX_ATTEMPTS = 2
        const val LIGHT_PROTECTION_REFRESH_RETRY_DELAY_MILLIS = 1_000L
    }
}

sealed interface DeviceFamilySettingsEvent {
    data object DeviceNameUpdateFailed : DeviceFamilySettingsEvent
    data object TemperatureProtectionUpdateFailed : DeviceFamilySettingsEvent
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

    data object Unsupported : DeviceSettingsUpdateActionState
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
