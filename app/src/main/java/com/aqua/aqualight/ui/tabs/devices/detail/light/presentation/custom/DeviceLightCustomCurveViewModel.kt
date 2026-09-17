package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeviceLightCustomCurveViewModel(
    private val customOperations: DeviceLightCustomOperations,
    private val libraryOperations: DeviceLightLibraryOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightCustomCurveUiState())
    val uiState: StateFlow<DeviceLightCustomCurveUiState> = _uiState.asStateFlow()
    val currentState: DeviceLightCustomCurveUiState get() = _uiState.value

    private val _effects = MutableSharedFlow<DeviceLightCustomCurveEffect>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightCustomCurveEffect> = _effects.asSharedFlow()

    private var boundDeviceUid = ""
    private var persistedDraft: DeviceLightCustomDraft? = null
    private var restoredDraft: DeviceLightCustomDraft? = null
    private var restoreDirty = false
    private var observeJob: Job? = null

    val dayEditor = DeviceLightCustomDayEditor(
        currentState = { currentState },
        setDraft = ::setDraft
    )
    val pointEditor = DeviceLightCustomPointEditor(
        currentState = { currentState },
        updateState = { change -> _uiState.update(change) },
        setDraft = ::setDraft,
        emit = ::emit
    )

    fun bind(
        deviceUidText: String,
        restoredDraft: DeviceLightCustomDraft? = null,
        restoredDirty: Boolean = false
    ) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Custom light destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        observeJob?.cancel()
        boundDeviceUid = deviceUid
        this.restoredDraft = restoredDraft
        restoreDirty = restoredDraft != null && restoredDirty
        _uiState.value = DeviceLightCustomCurveUiState(
            deviceUid = deviceUid,
            initialLoading = true
        )
        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            customOperations.observe(deviceUid).collect { result ->
                when (result) {
                    is DeviceLightCustomReadResult.Available -> applySnapshot(result.snapshot)
                    is DeviceLightCustomReadResult.Failed -> if (!_uiState.value.initialLoading) {
                        applyReadFailure(result.failure)
                    }
                }
            }
        }
        refreshFromDevice()
    }

    val refreshIfClean: () -> Unit = {
        if (!_uiState.value.hasUnsavedChanges) refreshFromDevice()
    }

    fun refreshFromDevice() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    initialLoading = state.channels.isEmpty(),
                    readFailed = false
                )
            }
            when (val result = customOperations.read(deviceUid)) {
                is DeviceLightCustomReadResult.Available -> applySnapshot(result.snapshot)
                is DeviceLightCustomReadResult.Failed -> {
                    applyReadFailure(result.failure)
                    emit(
                        DeviceLightCustomCurveEffect.ShowError(
                            result.failure.toCommercialLightError().messageRes
                        )
                    )
                }
            }
        }
    }

    fun preview() {
        val state = _uiState.value
        if (!state.canPreview) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true) }
            when (val result = customOperations.preview(boundDeviceUid, state.previewTimeMs)) {
                DeviceLightCustomMutationResult.Success -> Unit
                is DeviceLightCustomMutationResult.Failed -> emit(
                    DeviceLightCustomCurveEffect.ShowError(
                        result.failure.toCommercialLightError().messageRes
                    )
                )
            }
            _uiState.update { it.copy(operationInProgress = false) }
        }
    }

    val clearPreview: () -> Unit = {
        boundDeviceUid.takeIf(String::isNotBlank)?.let { deviceUid ->
            viewModelScope.launch { customOperations.clearPreview(deviceUid) }
        }
    }

    fun requestSaveAs() {
        if (!_uiState.value.canSaveAs) return
        viewModelScope.launch {
            val names = runCatching { libraryOperations.usedNames(DeviceLightLibraryKind.CUSTOM) }
                .getOrDefault(emptyList())
            emit(DeviceLightCustomCurveEffect.OpenSaveAs(names))
        }
    }

    fun saveAs(name: String) {
        val state = _uiState.value
        if (!state.canSaveAs) return
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    operationInProgress = true,
                    blockingOperationInProgress = true
                )
            }
            val points = state.draft.points.map { point ->
                DeviceLightLibraryCustomPoint(
                    timeMs = point.timeMs,
                    scene = DeviceLightLibraryScene(
                        point.channels.mapKeys { (channel, _) -> channel.toLibraryChannel() }
                    )
                )
            }
            when (
                val result = libraryOperations.saveCustom(
                    deviceUid = boundDeviceUid,
                    name = name,
                    weekdaysMask = state.draft.weekdaysMask,
                    points = points
                )
            ) {
                is DeviceLightLibraryMutationResult.Success -> {
                    persistedDraft = state.draft
                    _uiState.update { current ->
                        current.copy(
                            operationInProgress = false,
                            blockingOperationInProgress = false,
                            hasUnsavedChanges = false
                        )
                    }
                    emit(DeviceLightCustomCurveEffect.ShowSuccess(R.string.device_light_library_saved_success))
                }
                is DeviceLightLibraryMutationResult.Failed -> {
                    _uiState.update { current ->
                        current.copy(
                            operationInProgress = false,
                            blockingOperationInProgress = false
                        )
                    }
                    emit(
                        DeviceLightCustomCurveEffect.ShowError(
                            result.failure.toCommercialLightError().messageRes
                        )
                    )
                }
            }
        }
    }

    fun resetToDevice() {
        clearPreview()
        restoredDraft = null
        restoreDirty = false
        refreshFromDevice()
    }

    private fun applySnapshot(snapshot: DeviceLightCustomSnapshot) {
        val channels = snapshot.channels.map(DeviceLightCustomChannel::toUiChannel)
        val firmwareDraft = DeviceLightCustomDraft(
            weekdaysMask = snapshot.weekdaysMask,
            points = snapshot.points.map { point ->
                DeviceLightCustomPointUiState(
                    timeMs = point.timeMs,
                    channels = point.scene.channels.mapKeys { (channel, _) ->
                        channel.toUiChannel()
                    }
                )
            }
        )
        persistedDraft = firmwareDraft
        val restored = restoredDraft?.takeIf { draft ->
            draft.points.all { point -> point.channels.keys == channels.toSet() } &&
                draft.points.size <= snapshot.maxPoints
        }
        val current = _uiState.value
        val currentDirtyDraft = current.draft.takeIf { draft ->
            current.hasUnsavedChanges &&
                draft.points.all { point -> point.channels.keys == channels.toSet() } &&
                draft.points.size <= snapshot.maxPoints
        }
        val draft = when {
            restoreDirty && restored != null -> restored
            currentDirtyDraft != null -> currentDirtyDraft
            else -> firmwareDraft
        }
        restoredDraft = null
        val currentTimeMs = snapshot.currentTimeMs?.alignedTime() ?: _uiState.value.previewTimeMs
        val initialPointTimeMs = draft.points.minByOrNull { point ->
            kotlin.math.abs(point.timeMs - currentTimeMs)
        }?.timeMs
        _uiState.value = DeviceLightCustomCurveUiState(
            deviceUid = snapshot.deviceUid,
            connectionVisualState = if (snapshot.firmwareWriteAuthoritative) {
                DeviceConnectionVisualState.ONLINE
            } else {
                DeviceConnectionVisualState.OFFLINE
            },
            channels = channels,
            draft = draft,
            selectedTimeMs = initialPointTimeMs,
            previewTimeMs = initialPointTimeMs ?: currentTimeMs,
            maxPoints = snapshot.maxPoints,
            timeStepMs = snapshot.timeStepMs,
            contentEnabled = true,
            firmwareWriteAuthoritative = snapshot.firmwareWriteAuthoritative,
            initialLoading = false,
            operationInProgress = current.operationInProgress,
            blockingOperationInProgress = current.blockingOperationInProgress,
            hasUnsavedChanges = draft != firmwareDraft
        )
        restoreDirty = false
    }

    private fun applyReadFailure(
        failure: com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
    ) {
        _uiState.update { state ->
            state.copy(
                connectionVisualState = failure.connectionState(),
                initialLoading = false,
                contentEnabled = state.channels.isNotEmpty(),
                firmwareWriteAuthoritative = false,
                readFailed = true
            )
        }
    }

    private fun setDraft(draft: DeviceLightCustomDraft, selectedTimeMs: Long?) {
        _uiState.update { state ->
            state.copy(
                draft = draft,
                selectedTimeMs = selectedTimeMs,
                hasUnsavedChanges = draft != persistedDraft
            )
        }
    }

    private fun emit(effect: DeviceLightCustomCurveEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

}
