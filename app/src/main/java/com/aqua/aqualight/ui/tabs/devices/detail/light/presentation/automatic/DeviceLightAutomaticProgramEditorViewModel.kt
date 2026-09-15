package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticMutationResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgramDraft
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticReadResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticSnapshot
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetCatalog
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetId
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeviceLightAutomaticProgramEditorViewModel(
    private val operations: DeviceLightAutomaticOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightAutomaticProgramEditorUiState())
    val uiState: StateFlow<DeviceLightAutomaticProgramEditorUiState> = _uiState.asStateFlow()
    val currentState: DeviceLightAutomaticProgramEditorUiState get() = _uiState.value

    private val _effects = MutableSharedFlow<DeviceLightAutomaticProgramEditorEffect>(
        extraBufferCapacity = EFFECT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightAutomaticProgramEditorEffect> = _effects.asSharedFlow()

    private var boundDeviceUid = ""

    val draftEditor = DeviceLightAutomaticDraftEditor(
        currentState = { currentState },
        updateDraft = { update ->
            _uiState.update { state ->
                state.copy(
                    draft = update(state.draft),
                    selectedPresetId = null
                )
            }
        },
        emit = ::emit
    )

    fun bind(
        deviceUidText: String,
        mode: DeviceLightAutomaticEditorMode,
        restoredDraft: DeviceLightAutomaticEditorDraft?,
        restoredPresetId: DeviceLightPresetId?
    ) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Automatic editor destination deviceUid is required." }
        if (boundDeviceUid == deviceUid) return
        boundDeviceUid = deviceUid
        _uiState.value = DeviceLightAutomaticProgramEditorUiState(
            mode = mode,
            initialLoading = true
        )
        viewModelScope.launch {
            when (val result = operations.read(deviceUid)) {
                is DeviceLightAutomaticReadResult.Available ->
                    applySnapshot(result.snapshot, restoredDraft, restoredPresetId)
                is DeviceLightAutomaticReadResult.Failed -> applyReadFailure(result.failure)
            }
        }
    }

    fun applyPreset(presetId: DeviceLightPresetId) {
        val state = currentState
        val source = state.source ?: return
        val preset = DeviceLightPresetCatalog.find(presetId) ?: return
        if (!state.contentEnabled) return
        _uiState.value = state.copy(
            draft = state.draft.withPreset(preset, source),
            selectedPresetId = presetId
        )
    }

    fun save() {
        val state = currentState
        val source = state.source
        val draft = source?.let(state.draft::toMutationDraftOrNull)
        if (state.canSave && source != null && draft != null) {
            viewModelScope.launch {
                _uiState.update { value -> value.copy(operationInProgress = true) }
                when (val result = mutate(state, source, draft)) {
                    DeviceLightAutomaticMutationResult.Success -> {
                        val message = if (state.mode is DeviceLightAutomaticEditorMode.Edit) {
                            R.string.device_light_auto_editor_updated
                        } else {
                            R.string.device_light_auto_editor_created
                        }
                        _uiState.update { value -> value.copy(operationInProgress = false) }
                        emit(DeviceLightAutomaticProgramEditorEffect.Saved(message))
                    }
                    is DeviceLightAutomaticMutationResult.Failed ->
                        applyMutationFailure(result.failure)
                }
            }
        }
    }

    private fun applySnapshot(
        snapshot: DeviceLightAutomaticSnapshot,
        restoredDraft: DeviceLightAutomaticEditorDraft?,
        restoredPresetId: DeviceLightPresetId?
    ) {
        val mode = currentState.mode
        val selectedProgram = when (mode) {
            DeviceLightAutomaticEditorMode.Create -> null
            is DeviceLightAutomaticEditorMode.Edit -> snapshot.programs.singleOrNull { program ->
                program.programId == mode.programId
            }
            is DeviceLightAutomaticEditorMode.Duplicate -> snapshot.programs.singleOrNull { program ->
                program.programId == mode.sourceProgramId
            }
        }
        if (mode !is DeviceLightAutomaticEditorMode.Create && selectedProgram == null) {
            applyReadFailure(DeviceLightAutomaticFailure.NOT_FOUND)
            return
        }
        val emptyDraft = DeviceLightAutomaticEditorDraft.empty(snapshot.channels)
        val loadedDraft = selectedProgram?.let(DeviceLightAutomaticEditorDraft::fromProgram)
            ?: DeviceLightAutomaticEditorDraft.forNewProgram(snapshot.channels)
        val modeDraft = if (mode is DeviceLightAutomaticEditorMode.Duplicate) {
            loadedDraft.copy(enabled = true)
        } else {
            loadedDraft
        }
        val baseline = when (mode) {
            DeviceLightAutomaticEditorMode.Create,
            is DeviceLightAutomaticEditorMode.Edit -> modeDraft
            is DeviceLightAutomaticEditorMode.Duplicate -> emptyDraft
        }
        val restored = restoredDraft?.takeIf { draft ->
            draft.channels.keys == snapshot.channels.toSet()
        }
        _uiState.value = DeviceLightAutomaticProgramEditorUiState(
            mode = mode,
            source = DeviceLightAutomaticEditorSource(
                deviceUid = snapshot.deviceUid,
                revision = snapshot.revision,
                programCount = snapshot.programs.size,
                policy = snapshot.policy,
                channels = snapshot.channels,
                baselineDraft = baseline
            ),
            draft = restored ?: modeDraft,
            selectedPresetId = restoredPresetId?.takeIf { restored != null },
            connectionVisualState = DeviceConnectionVisualState.ONLINE
        )
    }

    private suspend fun mutate(
        state: DeviceLightAutomaticProgramEditorUiState,
        source: DeviceLightAutomaticEditorSource,
        draft: DeviceLightAutomaticProgramDraft
    ): DeviceLightAutomaticMutationResult = when (val mode = state.mode) {
        DeviceLightAutomaticEditorMode.Create,
        is DeviceLightAutomaticEditorMode.Duplicate -> operations.create(
            deviceUid = source.deviceUid,
            expectedRevision = source.revision,
            enabled = state.draft.enabled,
            draft = draft
        )
        is DeviceLightAutomaticEditorMode.Edit -> operations.update(
            deviceUid = source.deviceUid,
            expectedRevision = source.revision,
            programId = mode.programId,
            draft = draft
        )
    }

    private fun applyReadFailure(failure: DeviceLightAutomaticFailure) {
        _uiState.update { state ->
            state.copy(
                connectionVisualState = failure.connectionState(),
                initialLoading = false,
                operationInProgress = false,
                loadFailed = true
            )
        }
        emit(DeviceLightAutomaticProgramEditorEffect.ShowMessage(failure.messageRes()))
    }

    private fun applyMutationFailure(failure: DeviceLightAutomaticFailure) {
        _uiState.update { state -> state.copy(operationInProgress = false) }
        emit(DeviceLightAutomaticProgramEditorEffect.ShowMessage(failure.messageRes()))
    }

    private fun emit(effect: DeviceLightAutomaticProgramEditorEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}

internal sealed interface DeviceLightAutomaticProgramEditorEffect {
    data class OpenTimePicker(
        val field: DeviceLightAutomaticTimeField,
        val currentTimeMs: Long?
    ) : DeviceLightAutomaticProgramEditorEffect

    data class ShowMessage(@StringRes val messageRes: Int) :
        DeviceLightAutomaticProgramEditorEffect

    data class Saved(@StringRes val messageRes: Int) : DeviceLightAutomaticProgramEditorEffect
}

@StringRes
private fun DeviceLightAutomaticFailure.messageRes(): Int = when (this) {
    DeviceLightAutomaticFailure.STALE_REVISION -> R.string.device_light_auto_editor_stale
    DeviceLightAutomaticFailure.CAPACITY_REACHED -> R.string.device_light_auto_editor_capacity
    DeviceLightAutomaticFailure.OVERLAP -> R.string.device_light_auto_editor_overlap
    DeviceLightAutomaticFailure.NOT_FOUND -> R.string.device_light_auto_editor_not_found
    DeviceLightAutomaticFailure.NOT_CONNECTED -> R.string.device_light_auto_editor_not_connected
    DeviceLightAutomaticFailure.UNAVAILABLE,
    DeviceLightAutomaticFailure.UNSUPPORTED,
    DeviceLightAutomaticFailure.REJECTED,
    DeviceLightAutomaticFailure.INVALID_DATA -> R.string.device_light_auto_operation_error
}

private fun DeviceLightAutomaticFailure.connectionState(): DeviceConnectionVisualState = when (this) {
    DeviceLightAutomaticFailure.NOT_CONNECTED -> DeviceConnectionVisualState.OFFLINE
    DeviceLightAutomaticFailure.UNAVAILABLE -> DeviceConnectionVisualState.WARNING
    DeviceLightAutomaticFailure.UNSUPPORTED,
    DeviceLightAutomaticFailure.STALE_REVISION,
    DeviceLightAutomaticFailure.CAPACITY_REACHED,
    DeviceLightAutomaticFailure.OVERLAP,
    DeviceLightAutomaticFailure.NOT_FOUND,
    DeviceLightAutomaticFailure.REJECTED,
    DeviceLightAutomaticFailure.INVALID_DATA -> DeviceConnectionVisualState.WARNING
}

private const val EFFECT_BUFFER_CAPACITY = 2
