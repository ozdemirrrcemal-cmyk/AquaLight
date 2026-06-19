package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewSpeed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Temporary UI shell for the program editor.
 *
 * It keeps local form editing alive without loading from or saving to any
 * external Light source.
 */
class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

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
        _uiState.value = DeviceLightProgramEditorUiState.default()
    }

    fun updateStartTime(
        point: LightCurvePoint
    ) {
        _uiState.update { state ->
            state.copy(start = point)
        }
    }

    fun updatePeakStartTime(
        point: LightCurvePoint
    ) {
        _uiState.update { state ->
            state.copy(peakStart = point)
        }
    }

    fun updatePeakEndTime(
        point: LightCurvePoint
    ) {
        _uiState.update { state ->
            state.copy(peakEnd = point)
        }
    }

    fun updateEndTime(
        point: LightCurvePoint
    ) {
        _uiState.update { state ->
            state.copy(end = point)
        }
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
        _uiState.update { state ->
            state.copy(transitionMode = mode)
        }
    }

    fun updateRepeatEvery() {
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.EVERY,
                selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
            )
        }
    }

    fun updateRepeatWeekdays() {
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.WEEK,
                selectedDays = setOf(1, 2, 3, 4, 5)
            )
        }
    }

    fun updateRepeatWeekend() {
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.WEEKEND,
                selectedDays = setOf(6, 7)
            )
        }
    }

    fun updateCustomDays(
        days: Set<Int>
    ) {
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.CUSTOM,
                selectedDays = days
            )
        }
    }

    fun startPreview(
        speed: PreviewSpeed
    ) {
        _uiState.update { state ->
            state.copy(
                previewSpeed = speed,
                isPreviewRunning = true,
                previewProgressPercent = 0
            )
        }
    }

    fun stopPreview() {
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
        programName = name.ifBlank { programName }
        emitUnavailable()
    }

    private fun emitUnavailable() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightProgramEditorEvent.ShowError(
                    LIGHT_DATA_LAYER_NOT_CONNECTED
                )
            )
        }
    }
}
