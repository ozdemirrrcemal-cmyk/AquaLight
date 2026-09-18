package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomPoint
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomScene
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    private var previewJob: Job? = null

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
        previewJob?.cancel()
        previewJob = null
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
                        _uiState.applyReadFailure(result.failure)
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
                    _uiState.applyReadFailure(result.failure)
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
        val points = state.draft.points.map { point -> point.toApplicationPoint() }
        val previousTimeMs = state.previewTimeMs
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operationInProgress = true,
                    previewPlaybackActive = false,
                    previewTimeMs = 0L
                )
            }
            when (val result = customOperations.preview(boundDeviceUid, points)) {
                DeviceLightCustomMutationResult.Success -> playCustomDayPreview()
                is DeviceLightCustomMutationResult.Failed -> {
                    emit(
                        DeviceLightCustomCurveEffect.ShowError(
                            result.failure.toCommercialLightError().messageRes
                        )
                    )
                    _uiState.update {
                        it.copy(
                            operationInProgress = false,
                            previewPlaybackActive = false,
                            previewTimeMs = previousTimeMs
                        )
                    }
                }
            }
        }
    }

    val clearPreview: () -> Unit = {
        previewJob?.cancel()
        previewJob = null
        _uiState.update {
            it.copy(
                operationInProgress = false,
                previewPlaybackActive = false
            )
        }
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
        val previewTimeWhileAnimating = _uiState.value.previewTimeMs
            .takeIf { previewJob?.isActive == true }
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
            previewTimeMs = previewTimeWhileAnimating ?: initialPointTimeMs ?: currentTimeMs,
            maxPoints = snapshot.maxPoints,
            timeStepMs = snapshot.timeStepMs,
            contentEnabled = true,
            firmwareWriteAuthoritative = snapshot.firmwareWriteAuthoritative,
            initialLoading = false,
            operationInProgress = current.operationInProgress,
            previewPlaybackActive = current.previewPlaybackActive,
            blockingOperationInProgress = current.blockingOperationInProgress,
            hasUnsavedChanges = draft != firmwareDraft
        )
        restoreDirty = false
    }

    private suspend fun playCustomDayPreview() {
        _uiState.update { it.copy(previewPlaybackActive = true) }
        val startedAtNanos = System.nanoTime()
        var elapsedMs = 0L
        while (
            kotlinx.coroutines.currentCoroutineContext().isActive &&
            elapsedMs < CUSTOM_DAY_PREVIEW_DURATION_MS
        ) {
            elapsedMs = ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND)
                .coerceIn(0L, CUSTOM_DAY_PREVIEW_DURATION_MS)
            _uiState.update { state ->
                state.copy(previewTimeMs = customDayPreviewVirtualTimeMs(elapsedMs))
            }
            if (elapsedMs < CUSTOM_DAY_PREVIEW_DURATION_MS) {
                delay(PREVIEW_FRAME_MS)
            }
        }
        if (!kotlinx.coroutines.currentCoroutineContext().isActive) return

        _uiState.update { it.copy(previewTimeMs = MILLIS_PER_DAY) }
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank)
        if (deviceUid != null) {
            val refreshed = customOperations.read(deviceUid)
            val snapshot = (refreshed as? DeviceLightCustomReadResult.Available)?.snapshot
                ?: (customOperations.current(deviceUid) as? DeviceLightCustomReadResult.Available)
                    ?.snapshot
            if (snapshot != null) {
                applySnapshot(snapshot)
                snapshot.currentTimeMs?.let { currentTimeMs ->
                    _uiState.update { state ->
                        state.copy(previewTimeMs = currentTimeMs.alignedTime())
                    }
                }
            }
        }
        _uiState.update {
            it.copy(
                operationInProgress = false,
                previewPlaybackActive = false
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

private fun MutableStateFlow<DeviceLightCustomCurveUiState>.applyReadFailure(
    failure: DeviceLightCustomFailure
) {
    update { state ->
        state.copy(
            connectionVisualState = failure.connectionState(),
            initialLoading = false,
            contentEnabled = state.channels.isNotEmpty(),
            firmwareWriteAuthoritative = false,
            readFailed = true
        )
    }
}

internal fun customDayPreviewVirtualTimeMs(elapsedPreviewMs: Long): Long {
    val elapsed = elapsedPreviewMs.coerceIn(0L, CUSTOM_DAY_PREVIEW_DURATION_MS)
    return elapsed * MILLIS_PER_DAY / CUSTOM_DAY_PREVIEW_DURATION_MS
}

private fun DeviceLightCustomPointUiState.toApplicationPoint(): DeviceLightCustomPoint =
    DeviceLightCustomPoint(
        timeMs = timeMs,
        scene = DeviceLightCustomScene(
            channels = channels.mapKeys { (channel, _) -> channel.toApplicationChannel() }
        )
    )

private fun DeviceLightCustomChannelId.toApplicationChannel(): DeviceLightCustomChannel = when (this) {
    DeviceLightCustomChannelId.RED -> DeviceLightCustomChannel.RED
    DeviceLightCustomChannelId.GREEN -> DeviceLightCustomChannel.GREEN
    DeviceLightCustomChannelId.BLUE -> DeviceLightCustomChannel.BLUE
    DeviceLightCustomChannelId.WHITE -> DeviceLightCustomChannel.WHITE
}

private const val PREVIEW_FRAME_MS = 16L
private const val NANOS_PER_MILLISECOND = 1_000_000L
