package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightError
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightReadError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightLibraryViewModel(
    private val operations: DeviceLightLibraryOperations,
    private val rootOperations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightLibraryUiState())
    internal val uiState: StateFlow<DeviceLightLibraryUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DeviceLightLibraryEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val effects: SharedFlow<DeviceLightLibraryEffect> = _effects.asSharedFlow()

    private var boundDeviceUid = ""
    private var observationJob: Job? = null
    private var rootObservationJob: Job? = null

    fun bind(rawDeviceUid: String) {
        val deviceUid = rawDeviceUid.trim()
        require(deviceUid.isNotBlank()) { "Light-library deviceUid must not be blank." }
        if (deviceUid == boundDeviceUid) return
        boundDeviceUid = deviceUid
        _uiState.value = DeviceLightLibraryUiState(deviceUid = deviceUid)
            .withRootSnapshot(rootOperations.current(deviceUid))
        startObservation()
        startRootObservation()
    }

    private fun startRootObservation() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        rootObservationJob?.cancel()
        rootObservationJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                _uiState.update { state -> state.withRootSnapshot(snapshot) }
            }
        }
    }

    private fun startObservation() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            operations.observeLibrary(deviceUid)
                .catch {
                    emit(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.INVALID_DATA))
                }
                .collect { result ->
                    _uiState.update { state -> state.withResult(result) }
                }
        }
    }

    internal val selectTab: (DeviceLightLibraryTab) -> Unit = { tab ->
        _uiState.update { state -> state.copy(selectedTab = tab) }
    }

    internal fun retry() {
        _uiState.update { state ->
            state.copy(
                initialLoading = !state.hasPresentationSnapshot,
                readError = null
            )
        }
        startObservation()
    }

    internal fun requestActions(entryId: String) {
        val entry = _uiState.value.entries.singleOrNull { item -> item.id == entryId } ?: return
        _effects.tryEmit(DeviceLightLibraryEffect.OpenActions(entry.id, entry.name))
    }

    internal fun requestRename(entryId: String) {
        val entry = _uiState.value.entries.singleOrNull { item -> item.id == entryId } ?: return
        viewModelScope.launch {
            val disallowedNames = runCatching { operations.usedNames(entry.kind) }
                .getOrDefault(
                    _uiState.value.entries
                        .filter { item -> item.kind == entry.kind }
                        .map(DeviceLightLibraryEntry::name)
                )
                .filterNot { name -> name == entry.name }
            _effects.emit(
                DeviceLightLibraryEffect.OpenRename(
                    entryId = entry.id,
                    currentName = entry.name,
                    disallowedNames = disallowedNames
                )
            )
        }
    }

    internal fun rename(entryId: String, name: String) {
        viewModelScope.launch {
            _effects.emit(
                operations.rename(entryId, name).toEffect(
                    R.string.device_light_library_renamed_success
                )
            )
        }
    }

    internal fun requestDelete(entryId: String) {
        val entry = _uiState.value.entries.singleOrNull { item -> item.id == entryId } ?: return
        _effects.tryEmit(DeviceLightLibraryEffect.OpenDeleteConfirmation(entry.id, entry.name))
    }

    internal fun delete(entryId: String) {
        viewModelScope.launch {
            _effects.emit(
                operations.delete(entryId).toEffect(
                    R.string.device_light_library_deleted_success
                )
            )
        }
    }
}

private fun DeviceLightLibraryUiState.withRootSnapshot(
    snapshot: DeviceRootSnapshot?
): DeviceLightLibraryUiState = copy(
    connectionVisualState = snapshot.connectionVisualState(),
    initialLoading = if (
        snapshot != null &&
        snapshot.availability != OwnerDeviceAvailability.REACHABLE &&
        !hasPresentationSnapshot
    ) {
        false
    } else {
        initialLoading
    }
)

private fun DeviceLightLibraryUiState.withResult(
    result: DeviceLightLibraryResult
): DeviceLightLibraryUiState = when (result) {
    is DeviceLightLibraryResult.Available -> copy(
        target = result.snapshot.target,
        entries = result.snapshot.entries,
        initialLoading = false,
        readError = null
    )
    is DeviceLightLibraryResult.Failed -> copy(
        initialLoading = initialLoading && !hasPresentationSnapshot,
        readError = result.failure.toCommercialLightReadError()
    )
}

private fun DeviceRootSnapshot?.connectionVisualState(): DeviceConnectionVisualState =
    if (this?.availability == OwnerDeviceAvailability.REACHABLE) {
        DeviceConnectionVisualState.ONLINE
    } else {
        DeviceConnectionVisualState.OFFLINE
    }

private fun DeviceLightLibraryMutationResult.toEffect(
    @StringRes successMessage: Int
): DeviceLightLibraryEffect.ShowMessage = when (this) {
    is DeviceLightLibraryMutationResult.Success ->
        DeviceLightLibraryEffect.ShowMessage(successMessage, success = true)
    is DeviceLightLibraryMutationResult.Failed -> DeviceLightLibraryEffect.ShowMessage(
        messageRes = failure.toCommercialLightError().messageRes,
        success = false
    )
}

internal sealed interface DeviceLightLibraryEffect {
    data class OpenActions(
        val entryId: String,
        val entryName: String
    ) : DeviceLightLibraryEffect

    data class OpenRename(
        val entryId: String,
        val currentName: String,
        val disallowedNames: List<String>
    ) : DeviceLightLibraryEffect

    data class OpenDeleteConfirmation(
        val entryId: String,
        val entryName: String
    ) : DeviceLightLibraryEffect

    data class ShowMessage(
        @StringRes val messageRes: Int,
        val success: Boolean
    ) : DeviceLightLibraryEffect
}
