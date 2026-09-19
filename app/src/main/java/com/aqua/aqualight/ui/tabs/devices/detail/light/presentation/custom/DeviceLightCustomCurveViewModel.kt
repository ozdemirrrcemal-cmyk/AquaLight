package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomPoint
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomScene
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomWriteResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.common.devicepresence.observeConnectionVisualState
import com.aqua.aqualight.ui.common.devicepresence.toDeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightError
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
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
    private val libraryOperations: DeviceLightLibraryOperations,
    private val rootOperations: DeviceRootOperations
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
    private var deviceBaselineDraft: DeviceLightCustomDraft? = null
    private var editorCheckpointDraft: DeviceLightCustomDraft? = null
    val currentEditorCheckpoint: DeviceLightCustomDraft?
        get() = editorCheckpointDraft

    private var authoritativeRevision: Long? = null
    private var restoredDraft: DeviceLightCustomDraft? = null
    private var restoreDirty = false
    private var restoreUnapplied = false
    private var observeJob: Job? = null
    private var connectionJob: Job? = null
    private var previewJob: Job? = null
    private var deviceClockAnchorTimeMs: Long? = null
    private var deviceClockAnchorNanos: Long = 0L

    private val emitEffect: (DeviceLightCustomCurveEffect) -> Unit = { effect ->
        viewModelScope.launch { _effects.emit(effect) }
    }
    private val updateDraft: (DeviceLightCustomDraft, Long?) -> Unit =
        { draft, selectedTimeMs ->
            _uiState.update { state ->
                state.copy(
                    draft = draft,
                    selectedTimeMs = selectedTimeMs,
                    hasUnsavedChanges = draft != editorCheckpointDraft,
                    hasUnappliedChanges = draft != deviceBaselineDraft
                )
            }
        }

    val dayEditor = DeviceLightCustomDayEditor(
        currentState = { currentState },
        setDraft = updateDraft
    )
    val pointEditor = DeviceLightCustomPointEditor(
        currentState = { currentState },
        updateState = { change -> _uiState.update(change) },
        setDraft = updateDraft,
        emit = emitEffect
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

    val openLibraryProfile: (String) -> Unit = { entryId ->
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
                val draft = entry?.toCustomEditorDraft(
                    editorChannels = state.channels,
                    maxPoints = state.maxPoints
                )
                if (draft != null) {
                    val referenceTimeMs = state.deviceTimeMs ?: state.previewTimeMs
                    val selectedTimeMs = draft.points.minByOrNull { point ->
                        kotlin.math.abs(point.timeMs - referenceTimeMs)
                    }?.timeMs
                    editorCheckpointDraft = draft
                    updateDraft(draft, selectedTimeMs)
                    if (selectedTimeMs != null) {
                        _uiState.update { current ->
                            current.copy(
                                previewTimeMs = selectedTimeMs,
                                playheadMode = DeviceLightCustomPlayheadMode.EDIT
                            )
                        }
                    }
                } else {
                    val failure = when (result) {
                        is DeviceLightLibraryResult.Failed -> result.failure
                        is DeviceLightLibraryResult.Available ->
                            if (entry == null) {
                                DeviceLightLibraryFailure.NOT_FOUND
                            } else {
                                DeviceLightLibraryFailure.INCOMPATIBLE
                            }
                        null -> DeviceLightLibraryFailure.UNAVAILABLE
                    }
                    emitEffect(
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
        restoredDirty: Boolean = false,
        restoredUnapplied: Boolean = false,
        restoredCheckpoint: DeviceLightCustomDraft? = null
    ) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Custom light destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        observeJob?.cancel()
        connectionJob?.cancel()
        previewJob?.cancel()
        previewJob = null
        deviceClockAnchorTimeMs = null
        deviceClockAnchorNanos = 0L
        boundDeviceUid = deviceUid
        deviceBaselineDraft = null
        editorCheckpointDraft = restoredCheckpoint
        authoritativeRevision = null
        this.restoredDraft = restoredDraft
        restoreDirty = restoredDraft != null && restoredDirty
        restoreUnapplied = restoredDraft != null && restoredUnapplied
        _uiState.value = DeviceLightCustomCurveUiState(
            deviceUid = deviceUid,
            connectionVisualState = rootOperations.current(deviceUid).toDeviceConnectionVisualState(),
            initialLoading = true
        )
        connectionJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            rootOperations.observeConnectionVisualState(deviceUid).collect { connection ->
                _uiState.update { state -> state.copy(connectionVisualState = connection) }
            }
        }
        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            customOperations.observe(deviceUid).collect { result ->
                when (result) {
                    is DeviceLightCustomReadResult.Available -> applySnapshot(result.snapshot)
                    is DeviceLightCustomReadResult.Failed -> if (!_uiState.value.initialLoading) {
                        _uiState.applyReadFailure()
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
                    _uiState.applyReadFailure()
                    emitEffect(
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
        val points = state.draft.toApplicationPoints()
        val previousTimeMs = state.previewTimeMs
        val previousPlayheadMode = state.playheadMode
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true) }
            when (val result = customOperations.preview(boundDeviceUid, points)) {
                DeviceLightCustomMutationResult.Success -> playCustomDayPreview()
                is DeviceLightCustomMutationResult.Failed -> {
                    emitEffect(
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
            emitEffect(DeviceLightCustomCurveEffect.OpenSaveAs(names))
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
                    editorCheckpointDraft = state.draft
                    _uiState.update { current ->
                        current.copy(
                            operationInProgress = false,
                            blockingOperationInProgress = false,
                            hasUnsavedChanges = current.draft != editorCheckpointDraft,
                            hasUnappliedChanges = current.draft != deviceBaselineDraft
                        )
                    }
                    emitEffect(
                        DeviceLightCustomCurveEffect.ShowSuccess(
                            R.string.device_light_library_saved_success
                        )
                    )
                }
                is DeviceLightLibraryMutationResult.Failed -> {
                    _uiState.update { current ->
                        current.copy(
                            operationInProgress = false,
                            blockingOperationInProgress = false
                        )
                    }
                    emitEffect(
                        DeviceLightCustomCurveEffect.ShowError(
                            result.failure.toCommercialLightError().messageRes
                        )
                    )
                }
            }
        }
    }

    fun applyToDevice() {
        val state = _uiState.value
        val revision = authoritativeRevision
        if (!state.canApplyToDevice || revision == null) return
        val candidate = state.draft
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operationInProgress = true,
                    blockingOperationInProgress = true
                )
            }
            when (
                val result = customOperations.applyToDevice(
                    deviceUid = boundDeviceUid,
                    expectedRevision = revision,
                    weekdaysMask = candidate.weekdaysMask,
                    points = candidate.toApplicationPoints()
                )
            ) {
                is DeviceLightCustomWriteResult.Success -> {
                    applySnapshot(
                        snapshot = result.snapshot,
                        forceDeviceDraft = true
                    )
                    _uiState.update {
                        it.copy(
                            operationInProgress = false,
                            blockingOperationInProgress = false
                        )
                    }
                    emitEffect(
                        DeviceLightCustomCurveEffect.ShowSuccess(
                            R.string.device_light_custom_applied_success
                        )
                    )
                }
                is DeviceLightCustomWriteResult.Failed ->
                    finishPersistentWriteFailure(result.failure)
            }
        }
    }

    val requestDeviceProgramActions: () -> Unit = {
        if (_uiState.value.canClearDeviceProgram) {
            emitEffect(DeviceLightCustomCurveEffect.OpenDeviceProgramActions)
        }
    }

    fun clearDeviceProgram() {
        val state = _uiState.value
        val revision = authoritativeRevision
        if (!state.canClearDeviceProgram || revision == null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operationInProgress = true,
                    blockingOperationInProgress = true
                )
            }
            when (
                val result = customOperations.clearDeviceProgram(
                    deviceUid = boundDeviceUid,
                    expectedRevision = revision
                )
            ) {
                is DeviceLightCustomWriteResult.Success -> {
                    applySnapshot(
                        snapshot = result.snapshot,
                        forceDeviceDraft = true,
                        resetEditorFocus = true
                    )
                    _uiState.update {
                        it.copy(
                            operationInProgress = false,
                            blockingOperationInProgress = false
                        )
                    }
                    emitEffect(
                        DeviceLightCustomCurveEffect.ShowSuccess(
                            R.string.device_light_custom_device_program_deleted_success
                        )
                    )
                }
                is DeviceLightCustomWriteResult.Failed ->
                    finishPersistentWriteFailure(result.failure)
            }
        }
    }

    private suspend fun finishPersistentWriteFailure(failure: DeviceLightCustomFailure) {
        _uiState.update {
            it.copy(
                operationInProgress = false,
                blockingOperationInProgress = false
            )
        }
        if (failure == DeviceLightCustomFailure.STALE_REVISION) {
            when (val refreshed = customOperations.read(boundDeviceUid)) {
                is DeviceLightCustomReadResult.Available -> applySnapshot(refreshed.snapshot)
                is DeviceLightCustomReadResult.Failed -> Unit
            }
        }
        emitEffect(
            DeviceLightCustomCurveEffect.ShowError(
                failure.toCommercialLightError().messageRes
            )
        )
    }

    private fun applySnapshot(
        snapshot: DeviceLightCustomSnapshot,
        forceDeviceDraft: Boolean = false,
        resetEditorFocus: Boolean = false
    ) {
        val current = _uiState.value
        val channels = snapshot.channels.map(DeviceLightCustomChannel::toUiChannel)
        val firmwareDraft = snapshot.toUiDraft()
        val resolution = resolveSnapshotDraft(
            context = DeviceLightCustomSnapshotDraftContext(
                current = current,
                restoredDraft = restoredDraft,
                previousCheckpoint = editorCheckpointDraft,
                restoreDirty = restoreDirty,
                restoreUnapplied = restoreUnapplied,
                forceDeviceDraft = forceDeviceDraft
            ),
            channels = channels,
            maxPoints = snapshot.maxPoints,
            firmwareDraft = firmwareDraft
        )

        deviceBaselineDraft = firmwareDraft
        editorCheckpointDraft = resolution.checkpoint
        authoritativeRevision = snapshot.revision
        restoredDraft = null

        val deviceTimeMs = snapshot.currentTimeMs
            ?.takeIf { timeMs -> timeMs in 0 until MILLIS_PER_DAY }
            ?: current.deviceTimeMs
        val focus = resolveSnapshotFocus(
            resetEditorFocus = resetEditorFocus,
            draft = resolution.draft,
            current = current,
            deviceTimeMs = deviceTimeMs
        )
        _uiState.value = snapshot.toEditorUiState(
            DeviceLightCustomSnapshotPresentation(
                current = current,
                channels = channels,
                draftResolution = resolution,
                focus = focus,
                deviceTimeMs = deviceTimeMs
            )
        )
        deviceClockAnchorTimeMs = deviceTimeMs
        deviceClockAnchorNanos = System.nanoTime()
        restoreDirty = false
        restoreUnapplied = false
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

}

private fun MutableStateFlow<DeviceLightCustomCurveUiState>.applyReadFailure() {
    update { state ->
        state.copy(
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

private fun DeviceLightCustomDraft.toApplicationPoints(): List<DeviceLightCustomPoint> =
    points.map(DeviceLightCustomPointUiState::toApplicationPoint)

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
