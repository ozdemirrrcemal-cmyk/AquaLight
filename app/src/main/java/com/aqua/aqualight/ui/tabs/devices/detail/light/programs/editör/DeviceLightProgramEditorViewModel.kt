package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewSpeed
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper.LightProgramDraftMapper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val lightProgramsDataStoreManager =
        LightProgramsDataStoreManager(application.applicationContext)

    private val _uiState =
        MutableStateFlow(DeviceLightProgramEditorUiState.default())

    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<DeviceLightProgramEditorEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    fun updateStartTime(point: LightCurvePoint) {
        _uiState.update { it.copy(start = point) }
    }

    fun updatePeakStartTime(point: LightCurvePoint) {
        _uiState.update { it.copy(peakStart = point) }
    }

    fun updatePeakEndTime(point: LightCurvePoint) {
        _uiState.update { it.copy(peakEnd = point) }
    }

    fun updateEndTime(point: LightCurvePoint) {
        _uiState.update { it.copy(end = point) }
    }

    fun updateChannelValues(values: LightCurveChannelValues) {
        _uiState.update { it.copy(channelValues = values) }
    }

    fun updateRepeatEvery() {
        _uiState.update {
            it.copy(
                repeatMode = RepeatMode.EVERY,
                selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
            )
        }
    }

    fun updateRepeatWeekdays() {
        _uiState.update {
            it.copy(
                repeatMode = RepeatMode.WEEK,
                selectedDays = setOf(1, 2, 3, 4, 5)
            )
        }
    }

    fun updateRepeatWeekend() {
        _uiState.update {
            it.copy(
                repeatMode = RepeatMode.WEEKEND,
                selectedDays = setOf(6, 7)
            )
        }
    }

    fun updateCustomDays(days: Set<Int>) {
        _uiState.update {
            it.copy(
                repeatMode = RepeatMode.CUSTOM,
                selectedDays = days
            )
        }
    }

    fun updateMoonlight(settings: MoonlightSettings) {
        _uiState.update { it.copy(moonlightSettings = settings) }
    }

    fun updateCloudSimulation(settings: CloudSimulationSettings) {
        _uiState.update { it.copy(cloudSimulationSettings = settings) }
    }

    fun updateTransitionMode(mode: LightCurveTransitionMode) {
        _uiState.update { it.copy(transitionMode = mode) }
    }

    fun updatePreviewSpeed(speed: PreviewSpeed) {
        _uiState.update { it.copy(previewSpeed = speed) }
    }

    fun updateDeviceTime(
        hour: Int,
        minute: Int
    ) {
        _uiState.update {
            it.copy(currentDeviceTime = LightCurvePoint.of(hour, minute))
        }
    }

    fun saveProgram(
        name: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                val draft = _uiState.value.draft

                val savedProgram = LightProgramDraftMapper.toSavedProgram(
                    draft = draft,
                    name = name,
                    isActive = isActive
                )

                lightProgramsDataStoreManager.saveProgram(savedProgram)

                if (isActive) {
                    // TODO: Send savedProgram.draft to ESP32 as active program.
                }
            }.onSuccess {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowMessage(
                        if (isActive) "Program loaded to device" else "Program saved"
                    )
                )
                eventsChannel.send(DeviceLightProgramEditorEvent.NavigateBack)
            }.onFailure {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError(
                        "Program could not be saved"
                    )
                )
            }
        }
    }

    fun startPreview(speed: PreviewSpeed) {
        updatePreviewSpeed(speed)

        viewModelScope.launch {
            eventsChannel.send(
                DeviceLightProgramEditorEvent.ShowMessage(
                    "Preview started: ${speed.label}"
                )
            )

            // TODO: Send uiState.value.draft as temporary preview payload to ESP32.
        }
    }
}