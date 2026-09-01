package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.DEVICE_COOLING_AUTOMATIC_SILENT_MODE_MAXIMUM_FAN_PERCENT
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingAutomaticSettingsViewModel(
    private val operations: DeviceCoolingAutomaticSettingsOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingAutomaticSettingsUiState())
    val uiState: StateFlow<DeviceCoolingAutomaticSettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var saveJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        _uiState.value = DeviceCoolingAutomaticSettingsUiState(deviceUid = deviceUid)

        val current = operations.currentAutomaticSettings(deviceUid)
        if (current.loaded) {
            _uiState.value = _uiState.value.withSnapshot(current)
        }
        observeJob = viewModelScope.launch {
            operations.observeAutomaticSettings(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid || !snapshot.loaded) return@collect
                _uiState.update { state -> state.withSnapshot(snapshot) }
            }
        }
        refresh()
    }

    fun refresh() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            operations.refreshAutomaticSettings(deviceUid)
        }
    }

    fun updateStartTemperature(value: Double) {
        _uiState.update { state ->
            val policy = state.editorPolicy ?: return@update state
            val maximum = state.editorMaximumSpeedTemperatureC ?: return@update state
            if (!value.isValidStart(policy, maximum)) state
            else state.copy(
                draftStartTemperatureC = value,
                saveState = DeviceCoolingAutomaticSaveState.IDLE
            )
        }
    }

    fun updateMaximumSpeedTemperature(value: Double) {
        _uiState.update { state ->
            val policy = state.editorPolicy ?: return@update state
            val start = state.editorStartTemperatureC ?: return@update state
            if (!value.isValidMaximum(policy, start)) state
            else state.copy(
                draftMaximumSpeedTemperatureC = value,
                saveState = DeviceCoolingAutomaticSaveState.IDLE
            )
        }
    }

    fun updateSilentMode(enabled: Boolean) {
        _uiState.update { state ->
            if (!state.silentModeEditable || state.draftSilentModeEnabled == enabled) {
                state
            } else {
                state.copy(
                    draftSilentModeEnabled = enabled,
                    saveState = DeviceCoolingAutomaticSaveState.IDLE
                )
            }
        }
    }

    fun save() {
        val request = _uiState.value.pendingSave(boundDeviceUid) ?: return

        saveJob?.cancel()
        _uiState.update { current ->
            current.copy(saveState = DeviceCoolingAutomaticSaveState.SAVING)
        }
        saveJob = viewModelScope.launch {
            val result = operations.saveAutomaticSettings(
                deviceUid = request.deviceUid,
                startTemperatureC = request.startTemperatureC,
                maximumSpeedTemperatureC = request.maximumSpeedTemperatureC,
                silentModeEnabled = request.silentModeEnabled
            )
            if (boundDeviceUid == request.deviceUid) {
                _uiState.update { current ->
                    current.afterSave(request = request, successful = result.isSuccess)
                }
            }
        }
    }

    private fun clearBinding() {
        observeJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        boundDeviceUid = ""
        _uiState.value = DeviceCoolingAutomaticSettingsUiState()
    }
}

enum class DeviceCoolingAutomaticLoadState {
    LOADING,
    CONTENT,
    ERROR
}

enum class DeviceCoolingAutomaticSaveState {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

data class DeviceCoolingAutomaticSettingsUiState(
    val deviceUid: String = "",
    val loadState: DeviceCoolingAutomaticLoadState = DeviceCoolingAutomaticLoadState.CONTENT,
    val saveState: DeviceCoolingAutomaticSaveState = DeviceCoolingAutomaticSaveState.IDLE,
    val editable: Boolean = false,
    val persistedStartTemperatureC: Double? = null,
    val persistedMaximumSpeedTemperatureC: Double? = null,
    val draftStartTemperatureC: Double? = null,
    val draftMaximumSpeedTemperatureC: Double? = null,
    val persistedSilentModeEnabled: Boolean? = null,
    val draftSilentModeEnabled: Boolean = false,
    val silentModeMaximumFanPercent: Int =
        DEVICE_COOLING_AUTOMATIC_SILENT_MODE_MAXIMUM_FAN_PERCENT,
    val tankTemperatureC: Double? = null,
    val fanPercentNow: Double? = null,
    val policy: DeviceCoolingAutomaticTemperaturePolicy? = null
) {
    val hasFirmwareSnapshot: Boolean
        get() = persistedStartTemperatureC != null &&
            persistedMaximumSpeedTemperatureC != null &&
            policy != null

    val editorPolicy: DeviceCoolingAutomaticTemperaturePolicy?
        get() = policy

    val editorStartTemperatureC: Double?
        get() = draftStartTemperatureC

    val editorMaximumSpeedTemperatureC: Double?
        get() = draftMaximumSpeedTemperatureC

    val silentModeFirmwareBacked: Boolean
        get() = persistedSilentModeEnabled != null

    val silentModeEditable: Boolean
        get() = silentModeFirmwareBacked && editable

    val hasTemperatureChanges: Boolean
        get() = !sameTemperature(persistedStartTemperatureC, draftStartTemperatureC) ||
            !sameTemperature(
                persistedMaximumSpeedTemperatureC,
                draftMaximumSpeedTemperatureC
            )

    val hasSilentModeChanges: Boolean
        get() = persistedSilentModeEnabled?.let { persisted ->
            persisted != draftSilentModeEnabled
        } == true

    val hasChanges: Boolean
        get() = hasTemperatureChanges || hasSilentModeChanges

    private val hasSavePrerequisites: Boolean
        get() = loadState == DeviceCoolingAutomaticLoadState.CONTENT &&
            editable &&
            hasFirmwareSnapshot

    val canSave: Boolean
        get() = hasSavePrerequisites &&
            hasChanges &&
            saveState != DeviceCoolingAutomaticSaveState.SAVING
}

private fun DeviceCoolingAutomaticSettingsUiState.withSnapshot(
    snapshot: DeviceCoolingAutomaticSettingsSnapshot
): DeviceCoolingAutomaticSettingsUiState {
    val configuration = snapshot.completeConfiguration
    if (!snapshot.available || configuration == null) {
        return copy(
            loadState = DeviceCoolingAutomaticLoadState.CONTENT,
            editable = false,
            silentModeMaximumFanPercent = snapshot.silentModeMaximumFanPercent,
            tankTemperatureC = snapshot.tankTemperatureC,
            fanPercentNow = snapshot.fanPercentNow
        )
    }
    val start = configuration.startTemperatureC
    val maximum = configuration.maximumSpeedTemperatureC
    val preserveTemperatureDraft = hasFirmwareSnapshot && hasTemperatureChanges
    val preserveSilentModeDraft = silentModeFirmwareBacked && hasSilentModeChanges
    val incomingSilentMode = snapshot.silentModeEnabled
    return copy(
        loadState = DeviceCoolingAutomaticLoadState.CONTENT,
        editable = snapshot.editable,
        persistedStartTemperatureC = start,
        persistedMaximumSpeedTemperatureC = maximum,
        draftStartTemperatureC = if (preserveTemperatureDraft) draftStartTemperatureC else start,
        draftMaximumSpeedTemperatureC = if (preserveTemperatureDraft) {
            draftMaximumSpeedTemperatureC
        } else {
            maximum
        },
        persistedSilentModeEnabled = incomingSilentMode,
        draftSilentModeEnabled = when {
            incomingSilentMode == null -> draftSilentModeEnabled
            preserveSilentModeDraft -> draftSilentModeEnabled
            else -> incomingSilentMode
        },
        silentModeMaximumFanPercent = snapshot.silentModeMaximumFanPercent,
        tankTemperatureC = snapshot.tankTemperatureC,
        fanPercentNow = snapshot.fanPercentNow,
        policy = configuration.policy
    )
}

private val DeviceCoolingAutomaticSettingsSnapshot.completeConfiguration:
    AutomaticSnapshotConfiguration?
    get() = startTemperatureC?.let { start ->
        maximumSpeedTemperatureC?.let { maximum ->
            policy?.let { temperaturePolicy ->
                AutomaticSnapshotConfiguration(
                    startTemperatureC = start,
                    maximumSpeedTemperatureC = maximum,
                    policy = temperaturePolicy
                )
            }
        }
    }

private data class AutomaticSnapshotConfiguration(
    val startTemperatureC: Double,
    val maximumSpeedTemperatureC: Double,
    val policy: DeviceCoolingAutomaticTemperaturePolicy
)

private fun Double.isValidStart(
    policy: DeviceCoolingAutomaticTemperaturePolicy,
    maximum: Double
): Boolean = isFinite() &&
    this in policy.startMinimumC..policy.startMaximumC &&
    maximum - this >= policy.minimumGapC - TEMPERATURE_EPSILON

private fun Double.isValidMaximum(
    policy: DeviceCoolingAutomaticTemperaturePolicy,
    start: Double
): Boolean = isFinite() &&
    this in policy.maximumSpeedMinimumC..policy.maximumSpeedMaximumC &&
    this - start >= policy.minimumGapC - TEMPERATURE_EPSILON

private fun sameTemperature(first: Double?, second: Double?): Boolean = when {
    first == null || second == null -> first == second
    else -> abs(first - second) <= TEMPERATURE_EPSILON
}

private const val TEMPERATURE_EPSILON = 0.000_001
