package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            // Device connectivity is gated before this destination is entered. A transient read
            // failure therefore never replaces the editor with a synthetic error/retry surface.
            // Existing authoritative values remain visible; otherwise the structural editor keeps
            // unavailable placeholders until the runtime state flow publishes a valid snapshot.
        }
    }

    fun updateStartTemperature(value: Double) {
        _uiState.update { state ->
            val policy = state.policy ?: return@update state
            val maximum = state.draftMaximumSpeedTemperatureC ?: return@update state
            if (!value.isValidStart(policy, maximum)) state
            else state.copy(
                draftStartTemperatureC = value,
                saveState = DeviceCoolingAutomaticSaveState.IDLE
            )
        }
    }

    fun updateMaximumSpeedTemperature(value: Double) {
        _uiState.update { state ->
            val policy = state.policy ?: return@update state
            val start = state.draftStartTemperatureC ?: return@update state
            if (!value.isValidMaximum(policy, start)) state
            else state.copy(
                draftMaximumSpeedTemperatureC = value,
                saveState = DeviceCoolingAutomaticSaveState.IDLE
            )
        }
    }

    fun save() {
        val state = _uiState.value
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        val start = state.draftStartTemperatureC ?: return
        val maximum = state.draftMaximumSpeedTemperatureC ?: return
        if (!state.canSave) return

        saveJob?.cancel()
        _uiState.update { current ->
            current.copy(saveState = DeviceCoolingAutomaticSaveState.SAVING)
        }
        saveJob = viewModelScope.launch {
            val result = operations.saveAutomaticTemperatureRange(
                deviceUid = deviceUid,
                startTemperatureC = start,
                maximumSpeedTemperatureC = maximum
            )
            if (boundDeviceUid != deviceUid) return@launch
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        persistedStartTemperatureC = start,
                        persistedMaximumSpeedTemperatureC = maximum,
                        draftStartTemperatureC = start,
                        draftMaximumSpeedTemperatureC = maximum,
                        saveState = DeviceCoolingAutomaticSaveState.SAVED
                    )
                } else {
                    current.copy(saveState = DeviceCoolingAutomaticSaveState.ERROR)
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
    val tankTemperatureC: Double? = null,
    val fanPercentNow: Double? = null,
    val policy: DeviceCoolingAutomaticTemperaturePolicy? = null
) {
    val hasFirmwareSnapshot: Boolean
        get() = persistedStartTemperatureC != null &&
            persistedMaximumSpeedTemperatureC != null &&
            policy != null

    val hasChanges: Boolean
        get() = !sameTemperature(persistedStartTemperatureC, draftStartTemperatureC) ||
            !sameTemperature(
                persistedMaximumSpeedTemperatureC,
                draftMaximumSpeedTemperatureC
            )

    val canSave: Boolean
        get() = loadState == DeviceCoolingAutomaticLoadState.CONTENT &&
            editable &&
            hasChanges &&
            saveState != DeviceCoolingAutomaticSaveState.SAVING
}

private fun DeviceCoolingAutomaticSettingsUiState.withSnapshot(
    snapshot: DeviceCoolingAutomaticSettingsSnapshot
): DeviceCoolingAutomaticSettingsUiState {
    val start = snapshot.startTemperatureC
    val maximum = snapshot.maximumSpeedTemperatureC
    val policy = snapshot.policy
    if (!snapshot.available || start == null || maximum == null || policy == null) {
        return copy(
            loadState = DeviceCoolingAutomaticLoadState.CONTENT,
            editable = false,
            tankTemperatureC = snapshot.tankTemperatureC,
            fanPercentNow = snapshot.fanPercentNow
        )
    }
    val preserveDraft = hasChanges
    return copy(
        loadState = DeviceCoolingAutomaticLoadState.CONTENT,
        editable = snapshot.editable,
        persistedStartTemperatureC = start,
        persistedMaximumSpeedTemperatureC = maximum,
        draftStartTemperatureC = if (preserveDraft) draftStartTemperatureC else start,
        draftMaximumSpeedTemperatureC = if (preserveDraft) {
            draftMaximumSpeedTemperatureC
        } else {
            maximum
        },
        tankTemperatureC = snapshot.tankTemperatureC,
        fanPercentNow = snapshot.fanPercentNow,
        policy = policy
    )
}

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
