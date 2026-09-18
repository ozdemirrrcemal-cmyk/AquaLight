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
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
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
import kotlinx.coroutines.flow.first
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
    private var deviceClockAnchorTimeMs: Long? = null
    private var deviceClockAnchorNanos: Long = 0L

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

    val tickDeviceClock: () -> Unit = {
        val anchorTimeMs = deviceClockAnchorTimeMs
        if (anchorTimeMs != null) {
            val elapsedMs = (
                (System.nanoTime() - deviceClockAnchorNanos) / NANOS_PER_MILLISECOND
            ).coerceAtLeast(0L)
            val projectedTimeMs = (anchorTimeMs + elapsedMs).mod(MILLIS_PER_DAY)
            _uiState.update { state ->
                state.copy(
                    deviceTimeMs = projectedTimeMs,
                    previewTimeMs = if (
                        state.playheadMode == DeviceLightCustomPlayheadMode.CLOCK
                    ) {
                        projectedTimeMs
                    } else {
                        state.previewTimeMs
                    }
                )
            }
        }
    }

    val loadLibraryDraft: (String) -> Unit = { entryId ->
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank)
        if (deviceUid != null) {
            viewModelScope.launch {
                val result = runCatching {
                    libraryOperations.observeLibrary(deviceUid).first()
                }.getOrNull()
                val entry = (result as? DeviceLightLibraryResult.Available)
                    ?.snapshot
                    ?.entries
                    ?.singleOrNull { item -> item.id == entryId }
                val state = _uiState.value
                val draft = entry?.toCustomEditorDraft(state.channels)
                if (draft != null) {
                    val referenceTimeMs = state.deviceTimeMs ?: state.previewTimeMs
                    val selectedTimeMs = draft.points.minByOrNull { point ->
                        kotlin.math.abs(point.timeMs - referenceTimeMs)
                    }?.timeMs
                    setDraft(draft, selectedTimeMs)
                } else {
                    val failure = (result as? DeviceLightLibraryResult.Failed)?.failure
                        ?: DeviceLightLibraryFailure.NOT_FOUND
                    emit(
                        DeviceLightCustomCurveEffect.ShowError(
                            failure.toCommercialLightError().messageRes
                        )
                    )
                }
            }
        }
    }

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
        deviceClockAnchorTimeMs = null
        deviceClockAnchorNanos = 0L
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
        val previousPlayheadMode = state.playheadMode
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true) }
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
                            playheadMode = previousPlayheadMode,
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
        _uiState.update { state ->
            state.copy(
                operationInProgress = false,
                playheadMode = DeviceLightCustomPlayheadMode.CLOCK,
                previewTimeMs = state.deviceTimeMs ?: state.previewTimeMs
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

    fun resetDraft() {
        clearPreview()
        val state = _uiState.value
        setDraft(
            draft = state.draft.copy(points = emptyList()),
            selectedTimeMs = null
        )
    }

    private fun applySnapshot(snapshot: DeviceLightCustomSnapshot) {
        val channels = snapshot.channels.map(DeviceLightCustomChannel::toUiChannel)
        val firmwareDraft = snapshot.toUiDraft()
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
        val deviceTimeMs = snapshot.currentTimeMs
            ?.takeIf { timeMs -> timeMs in 0 until MILLIS_PER_DAY }
            ?: current.deviceTimeMs
        val selectedTimeMs = draft.resolveSelection(current, deviceTimeMs)
        val playheadTimeMs = current.resolvePlayheadTime(deviceTimeMs)
        _uiState.value = DeviceLightCustomCurveUiState(
            deviceUid = snapshot.deviceUid,
            connectionVisualState = if (snapshot.firmwareWriteAuthoritative) {
                DeviceConnectionVisualState.ONLINE
            } else {
                DeviceConnectionVisualState.OFFLINE
            },
            channels = channels,
            draft = draft,
            selectedTimeMs = selectedTimeMs,
            previewTimeMs = playheadTimeMs,
            deviceTimeMs = deviceTimeMs,
            playheadMode = current.playheadMode,
            maxPoints = snapshot.maxPoints,
            timeStepMs = snapshot.timeStepMs,
            contentEnabled = true,
            firmwareWriteAuthoritative = snapshot.firmwareWriteAuthoritative,
            initialLoading = false,
            operationInProgress = current.operationInProgress,
            blockingOperationInProgress = current.blockingOperationInProgress,
            hasUnsavedChanges = draft != firmwareDraft
        )
        deviceClockAnchorTimeMs = deviceTimeMs
        deviceClockAnchorNanos = System.nanoTime()
        restoreDirty = false
    }

    private suspend fun playCustomDayPreview() {
        _uiState.update {
            it.copy(
                playheadMode = DeviceLightCustomPlayheadMode.PREVIEW,
                previewTimeMs = 0L
            )
        }
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

        _uiState.update { state ->
            state.copy(
                playheadMode = DeviceLightCustomPlayheadMode.CLOCK,
                previewTimeMs = state.deviceTimeMs ?: state.previewTimeMs
            )
        }
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank)
        if (deviceUid != null) {
            val refreshed = customOperations.read(deviceUid)
            val snapshot = (refreshed as? DeviceLightCustomReadResult.Available)?.snapshot
                ?: (customOperations.current(deviceUid) as? DeviceLightCustomReadResult.Available)
                    ?.snapshot
            snapshot?.let(::applySnapshot)
        }
        _uiState.update { it.copy(operationInProgress = false) }
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

private fun DeviceLightCustomSnapshot.toUiDraft() = DeviceLightCustomDraft(
    weekdaysMask = weekdaysMask,
    points = points.map { point ->
        DeviceLightCustomPointUiState(
            timeMs = point.timeMs,
            channels = point.scene.channels.mapKeys { (channel, _) ->
                channel.toUiChannel()
            }
        )
    }
)

private fun DeviceLightCustomDraft.resolveSelection(
    current: DeviceLightCustomCurveUiState,
    deviceTimeMs: Long?
): Long? {
    val selectionReferenceMs = deviceTimeMs ?: current.previewTimeMs
    return current.selectedTimeMs
        ?.takeIf { selected -> points.any { point -> point.timeMs == selected } }
        ?: points.minByOrNull { point ->
            kotlin.math.abs(point.timeMs - selectionReferenceMs)
        }?.timeMs
}

private fun DeviceLightCustomCurveUiState.resolvePlayheadTime(
    deviceTimeMs: Long?
): Long = when (playheadMode) {
    DeviceLightCustomPlayheadMode.CLOCK -> deviceTimeMs ?: previewTimeMs
    DeviceLightCustomPlayheadMode.EDIT,
    DeviceLightCustomPlayheadMode.PREVIEW -> previewTimeMs
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
