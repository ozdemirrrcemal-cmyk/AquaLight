package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsRepository
import com.aqua.aqualight.data.devices.light.programs.LoadLightProgramResult
import com.aqua.aqualight.data.devices.light.programs.SaveLightProgramResult
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.toUiTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSyncState
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewSpeed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = LightProgramsRepository.get(
        application.applicationContext
    )

    private val _uiState = MutableStateFlow(
        DeviceLightProgramEditorUiState.default()
    )
    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightProgramEditorEvent>()
    val events: SharedFlow<DeviceLightProgramEditorEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L
    private var previewJob: Job? = null

    fun initialize(
        deviceId: Long,
        programId: String?
    ) {
        this.deviceId = deviceId
        previewJob?.cancel()

        val safeProgramId = programId?.takeIf { id -> id.isNotBlank() }
        if (safeProgramId == null) {
            _uiState.value = DeviceLightProgramEditorUiState.default()
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    programId = safeProgramId,
                    isLoading = true,
                    isEditingExistingProgram = true
                )
            }
            _events.emit(DeviceLightProgramEditorEvent.SetLoading(true))

            val savedProgram = repository.getProgram(
                deviceId = deviceId,
                programId = safeProgramId
            )

            if (savedProgram == null) {
                _uiState.value = DeviceLightProgramEditorUiState.default().copy(
                    isLoading = false
                )
                _events.emit(DeviceLightProgramEditorEvent.ShowError("Program could not be found"))
            } else {
                _uiState.value = savedProgram.toEditorState()
            }

            _events.emit(DeviceLightProgramEditorEvent.SetLoading(false))
        }
    }

    fun updateStartTime(
        point: LightCurvePoint
    ) {
        editState { state ->
            state.copy(start = point)
        }
    }

    fun updatePeakStartTime(
        point: LightCurvePoint
    ) {
        editState { state ->
            state.copy(peakStart = point)
        }
    }

    fun updatePeakEndTime(
        point: LightCurvePoint
    ) {
        editState { state ->
            state.copy(peakEnd = point)
        }
    }

    fun updateEndTime(
        point: LightCurvePoint
    ) {
        editState { state ->
            state.copy(end = point)
        }
    }

    fun updateChannelValues(
        values: LightCurveChannelValues
    ) {
        editState { state ->
            state.copy(channelValues = values.normalized())
        }
    }

    fun updateTransitionMode(
        mode: LightCurveTransitionMode
    ) {
        editState { state ->
            state.copy(transitionMode = mode)
        }
    }

    fun updateRepeatEvery() {
        editState { state ->
            state.copy(
                repeatMode = RepeatMode.EVERY,
                selectedDays = DeviceLightProgramEditorUiState.ALL_DAYS
            )
        }
    }

    fun updateRepeatWeekdays() {
        emitRepeatLockedMessage()
    }

    fun updateRepeatWeekend() {
        emitRepeatLockedMessage()
    }

    fun updateCustomDays(
        days: Set<Int>
    ) {
        if (!_uiState.value.repeatFeatureEnabled) {
            emitRepeatLockedMessage()
            return
        }

        editState { state ->
            state.copy(
                repeatMode = RepeatMode.CUSTOM,
                selectedDays = days.ifEmpty {
                    DeviceLightProgramEditorUiState.ALL_DAYS
                }
            )
        }
    }

    fun startPreview(
        speed: PreviewSpeed
    ) {
        previewJob?.cancel()
        _uiState.update { state ->
            state.copy(
                previewSpeed = speed,
                isPreviewRunning = true,
                previewProgressPercent = 0,
                previewSimulationTime = LightCurvePoint.of(0, 0)
            )
        }

        val durationMillis = speed.durationMinutes * 60_000L
        val startedAt = System.currentTimeMillis()

        previewJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startedAt
                val fraction = (elapsed.toDouble() / durationMillis.toDouble())
                    .coerceIn(0.0, 1.0)
                val progress = (fraction * 100.0).roundToInt()
                    .coerceIn(0, 100)
                val minuteOfDay = (fraction * (MINUTES_PER_DAY - 1)).roundToInt()
                    .coerceIn(0, MINUTES_PER_DAY - 1)

                _uiState.update { state ->
                    state.copy(
                        isPreviewRunning = fraction < 1.0,
                        previewProgressPercent = progress,
                        previewSimulationTime = minuteOfDay.toPoint()
                    )
                }

                if (fraction >= 1.0) {
                    previewJob = null
                    break
                }

                delay(PREVIEW_TICK_MILLIS)
            }
        }
    }

    fun stopPreview() {
        stopPreviewInternal(resetProgress = true)
    }

    fun currentProgramName(): String {
        return _uiState.value.programName
    }

    fun isEditingExistingProgram(): Boolean {
        return _uiState.value.isEditingExistingProgram
    }

    fun saveProgram(
        name: String,
        activateOnDevice: Boolean
    ) {
        if (deviceId <= 0L) {
            emitError("Light device information is missing")
            return
        }

        val state = _uiState.value
        val safeName = name.trim().ifBlank {
            state.programName
        }

        viewModelScope.launch {
            setSavingState(
                activateOnDevice = activateOnDevice,
                isLoading = true
            )

            if (activateOnDevice) {
                handleLoadToDevice(
                    state = state,
                    safeName = safeName
                )
            } else {
                handleLocalSave(
                    state = state,
                    safeName = safeName
                )
            }

            setSavingState(
                activateOnDevice = activateOnDevice,
                isLoading = false
            )
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        super.onCleared()
    }

    private suspend fun handleLocalSave(
        state: DeviceLightProgramEditorUiState,
        safeName: String
    ) {
        when (val result = repository.saveDraft(
            deviceId = deviceId,
            programId = state.programId,
            name = safeName,
            draft = state.copy(
                programName = safeName
            ).draft
        )) {
            is SaveLightProgramResult.Success -> {
                _uiState.value = result.program.toEditorState().copy(
                    hasUnsavedChanges = false
                )
                _events.emit(DeviceLightProgramEditorEvent.ShowMessage("Program saved"))
            }

            is SaveLightProgramResult.Error -> {
                _events.emit(DeviceLightProgramEditorEvent.ShowError(result.message))
            }
        }
    }

    private suspend fun handleLoadToDevice(
        state: DeviceLightProgramEditorUiState,
        safeName: String
    ) {
        when (val result = repository.loadDraftToDevice(
            deviceId = deviceId,
            programId = state.programId,
            name = safeName,
            draft = state.copy(
                programName = safeName
            ).draft
        )) {
            is LoadLightProgramResult.Loaded -> {
                _uiState.value = result.program.toEditorState().copy(
                    hasUnsavedChanges = false
                )
                _events.emit(DeviceLightProgramEditorEvent.ShowMessage("Program loaded to device"))
            }

            is LoadLightProgramResult.LocalOnly -> {
                _events.emit(DeviceLightProgramEditorEvent.ShowMessage(result.message))
            }

            is LoadLightProgramResult.Error -> {
                _events.emit(DeviceLightProgramEditorEvent.ShowError(result.message))
            }
        }
    }

    private fun editState(
        reducer: (DeviceLightProgramEditorUiState) -> DeviceLightProgramEditorUiState
    ) {
        previewJob?.cancel()
        previewJob = null

        _uiState.update { current ->
            val edited = reducer(
                current.copy(
                    isPreviewRunning = false,
                    previewProgressPercent = 0,
                    previewSimulationTime = null
                )
            )

            edited.copy(
                hasUnsavedChanges = true,
                repeatMode = RepeatMode.EVERY,
                selectedDays = DeviceLightProgramEditorUiState.ALL_DAYS,
                syncState = when (edited.syncState) {
                    LightProgramSyncState.ACTIVE_SYNCED -> LightProgramSyncState.ACTIVE_DIRTY
                    else -> edited.syncState
                }
            )
        }
    }

    private fun stopPreviewInternal(
        resetProgress: Boolean
    ) {
        previewJob?.cancel()
        previewJob = null

        _uiState.update { state ->
            state.copy(
                isPreviewRunning = false,
                previewProgressPercent = if (resetProgress) 0 else state.previewProgressPercent,
                previewSimulationTime = null
            )
        }
    }

    private fun setSavingState(
        activateOnDevice: Boolean,
        isLoading: Boolean
    ) {
        _uiState.update { current ->
            if (activateOnDevice) {
                current.copy(isLoadingToDevice = isLoading)
            } else {
                current.copy(isSaving = isLoading)
            }
        }

        viewModelScope.launch {
            _events.emit(DeviceLightProgramEditorEvent.SetLoading(isLoading))
        }
    }

    private fun emitRepeatLockedMessage() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightProgramEditorEvent.ShowMessage(
                    "Runs every day. Custom days will be available with a firmware update."
                )
            )
        }
    }

    private fun emitError(
        message: String
    ) {
        viewModelScope.launch {
            _events.emit(DeviceLightProgramEditorEvent.ShowError(message))
        }
    }

    private fun SavedLightProgram.toEditorState(): DeviceLightProgramEditorUiState {
        return DeviceLightProgramEditorUiState.default().copy(
            programId = id,
            programName = name,
            isEditingExistingProgram = true,
            isLoading = false,
            isSaving = false,
            isLoadingToDevice = false,
            hasUnsavedChanges = false,
            syncState = syncState,
            start = startMinute.toPoint(),
            peakStart = peakStartMinute.toPoint(),
            peakEnd = peakEndMinute.toPoint(),
            end = endMinute.toEditorEndPoint(),
            channelValues = LightCurveChannelValues(
                red = red,
                green = green,
                blue = blue,
                white = white
            ).normalized(),
            repeatMode = RepeatMode.EVERY,
            selectedDays = DeviceLightProgramEditorUiState.ALL_DAYS,
            transitionMode = transitionMode.toUiTransitionMode(),
            previewSimulationTime = null,
            isPreviewRunning = false,
            previewProgressPercent = 0
        )
    }

    private fun Int.toEditorEndPoint(): LightCurvePoint {
        return if (this >= MINUTES_PER_DAY) {
            LightCurvePoint.of(0, 0)
        } else {
            this.toPoint()
        }
    }

    private fun Int.toPoint(): LightCurvePoint {
        val safeMinute = coerceIn(0, MINUTES_PER_DAY - 1)
        return LightCurvePoint.of(
            hour = safeMinute / 60,
            minute = safeMinute % 60
        )
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
        private const val PREVIEW_TICK_MILLIS = 250L
    }
}
