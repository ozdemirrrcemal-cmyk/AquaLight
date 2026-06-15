package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramCompileResult
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramScheduleCompiler
import com.aqua.aqualight.data.devices.light.programs.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.programs.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.programs.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramPreviewEngine
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

class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val programStore = LightProgramDataStoreManager.create(application)

    private val _uiState = MutableStateFlow(
        DeviceLightProgramEditorUiState.default()
    )
    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightProgramEditorEvent>()
    val events: SharedFlow<DeviceLightProgramEditorEvent> =
        _events.asSharedFlow()

    private var deviceId: Long = 0L
    private var programId: String? = null
    private var programName: String = "New Program"
    private var previewJob: Job? = null

    fun initialize(
        deviceId: Long,
        programId: String?
    ) {
        this.deviceId = deviceId
        this.programId = programId
        this.programName = if (programId.isNullOrBlank()) {
            "New Program"
        } else {
            "Program"
        }

        previewJob?.cancel()
        _uiState.value = DeviceLightProgramEditorUiState.default()

        if (!programId.isNullOrBlank()) {
            viewModelScope.launch {
                programStore.findProgram(
                    deviceId = deviceId,
                    programId = programId
                )?.let { savedProgram ->
                    programName = savedProgram.name
                    val draft = savedProgram.toDraft()
                    _uiState.update { state ->
                        state.copy(
                            start = draft.start,
                            peakStart = draft.peakStart,
                            peakEnd = draft.peakEnd,
                            end = draft.end,
                            channelValues = draft.channelValues,
                            repeatMode = draft.repeatMode,
                            selectedDays = draft.selectedDays,
                            transitionMode = draft.transitionMode,
                            currentDeviceTime = state.currentDeviceTime,
                            isPreviewRunning = false,
                            previewProgressPercent = 0,
                            previewSimulationTime = null
                        )
                    }
                }
            }
        }
    }

    fun updateStartTime(
        point: LightCurvePoint
    ) {
        stopPreview()
        _uiState.update { state -> state.copy(start = point) }
    }

    fun updatePeakStartTime(
        point: LightCurvePoint
    ) {
        stopPreview()
        _uiState.update { state -> state.copy(peakStart = point) }
    }

    fun updatePeakEndTime(
        point: LightCurvePoint
    ) {
        stopPreview()
        _uiState.update { state -> state.copy(peakEnd = point) }
    }

    fun updateEndTime(
        point: LightCurvePoint
    ) {
        stopPreview()
        _uiState.update { state -> state.copy(end = point) }
    }

    fun updateChannelValues(
        values: LightCurveChannelValues
    ) {
        _uiState.update { state ->
            state.copy(channelValues = values.normalized())
        }
    }

    fun updateTransitionMode(
        mode: LightCurveTransitionMode
    ) {
        stopPreview()
        _uiState.update { state ->
            state.copy(transitionMode = mode)
        }
    }

    fun updateRepeatEvery() {
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.EVERY,
                selectedDays = LEGACY_ALL_DAYS
            )
        }
    }

    fun updateRepeatWeekdays() {
        keepLegacyRepeatLocked()
    }

    fun updateRepeatWeekend() {
        keepLegacyRepeatLocked()
    }

    fun updateCustomDays(
        days: Set<Int>
    ) {
        keepLegacyRepeatLocked()
    }

    fun startPreview(
        speed: PreviewSpeed
    ) {
        val state = _uiState.value
        val schedule = when (val result = LightProgramScheduleCompiler.compile(state.draft)) {
            is LightProgramCompileResult.Valid -> result.schedule
            is LightProgramCompileResult.Invalid -> {
                viewModelScope.launch {
                    _events.emit(DeviceLightProgramEditorEvent.ShowError(result.message))
                }
                return
            }
        }

        previewJob?.cancel()
        _uiState.update { current ->
            current.copy(
                previewSpeed = speed,
                isPreviewRunning = true,
                previewProgressPercent = 0,
                previewSimulationTime = LightCurvePoint.of(0, 0)
            )
        }

        previewJob = viewModelScope.launch {
            val durationMillis = speed.durationMinutes * 60_000L
            val startedAt = System.currentTimeMillis()

            while (true) {
                val elapsed = System.currentTimeMillis() - startedAt
                val frame = LightProgramPreviewEngine.frameAt(
                    schedule = schedule,
                    elapsedMillis = elapsed,
                    durationMillis = durationMillis
                )

                _uiState.update { current ->
                    current.copy(
                        previewSimulationTime = frame.time,
                        previewProgressPercent = frame.progressPercent,
                        isPreviewRunning = !frame.isFinished
                    )
                }

                if (frame.isFinished) {
                    _events.emit(DeviceLightProgramEditorEvent.ShowMessage("Preview finished."))
                    break
                }

                delay(PREVIEW_FRAME_INTERVAL_MILLIS)
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        _uiState.update { state ->
            state.copy(
                isPreviewRunning = false,
                previewProgressPercent = 0,
                previewSimulationTime = null
            )
        }
    }

    fun currentProgramName(): String {
        return programName
    }

    fun isEditingExistingProgram(): Boolean {
        return !programId.isNullOrBlank()
    }

    fun saveProgram(
        name: String,
        activateOnDevice: Boolean
    ) {
        val safeName = name.ifBlank { programName }
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                programStore.saveProgram(
                    deviceId = deviceId,
                    programId = if (activateOnDevice) programId else null,
                    name = safeName,
                    draft = state.draft.copy(
                        repeatMode = RepeatMode.EVERY,
                        selectedDays = LEGACY_ALL_DAYS
                    ),
                    activate = activateOnDevice
                )
            }.onSuccess { savedProgram ->
                programId = savedProgram.id
                programName = savedProgram.name
                _events.emit(
                    DeviceLightProgramEditorEvent.ShowMessage(
                        if (activateOnDevice) {
                            "Program saved and marked active locally. Device schedule write is ready for firmware connection."
                        } else {
                            "Program saved as local copy."
                        }
                    )
                )
                _events.emit(DeviceLightProgramEditorEvent.NavigateBack)
            }.onFailure { error ->
                _events.emit(
                    DeviceLightProgramEditorEvent.ShowError(
                        error.message ?: "Program could not be saved."
                    )
                )
            }
        }
    }

    private fun keepLegacyRepeatLocked() {
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.EVERY,
                selectedDays = LEGACY_ALL_DAYS
            )
        }
        viewModelScope.launch {
            _events.emit(
                DeviceLightProgramEditorEvent.ShowMessage(
                    "Repeat is locked to Every day for legacy firmware."
                )
            )
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val PREVIEW_FRAME_INTERVAL_MILLIS = 250L
        val LEGACY_ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
    }
}
