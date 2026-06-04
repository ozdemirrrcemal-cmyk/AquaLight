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
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation.LightProgramDraftValidator
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation.LightProgramValidationResult
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper.LightProgramDraftMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private var editingProgramId: String? = null
    private var editingProgramName: String? = null
    private var editingProgramDeviceId: Long = 0L
    private var editingProgramCreatedAt: Long = 0L

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

    fun loadProgram(
        programId: String?
    ) {
        if (programId.isNullOrBlank()) return

        viewModelScope.launch {
            val program = lightProgramsDataStoreManager.getProgram(programId)

            if (program == null) {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError("Program could not be found")
                )
                return@launch
            }

            editingProgramId = program.id
            editingProgramName = program.name
            editingProgramDeviceId = program.deviceId
            editingProgramCreatedAt = program.createdAt

            _uiState.update {
                it.copy(
                    start = program.draft.start,
                    peakStart = program.draft.peakStart,
                    peakEnd = program.draft.peakEnd,
                    end = program.draft.end,
                    channelValues = program.draft.channelValues,
                    repeatMode = program.draft.repeatMode,
                    selectedDays = program.draft.selectedDays,
                    moonlightSettings = program.draft.moonlightSettings,
                    cloudSimulationSettings = program.draft.cloudSimulationSettings,
                    transitionMode = program.draft.transitionMode
                )
            }
        }
    }

    fun currentProgramName(): String {
        return editingProgramName.orEmpty()
    }

    fun saveProgram(
        name: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                val draft = _uiState.value.draft

                when (val validation = LightProgramDraftValidator.validate(draft)) {
                    LightProgramValidationResult.Valid -> Unit

                    is LightProgramValidationResult.Invalid -> {
                        eventsChannel.send(
                            DeviceLightProgramEditorEvent.ShowError(validation.message)
                        )
                        return@launch
                    }
                }

                val savedProgram = if (editingProgramId != null) {
                    SavedLightProgram(
                        id = editingProgramId.orEmpty(),
                        deviceId = editingProgramDeviceId,
                        name = name,
                        draft = draft,
                        isActive = isActive,
                        createdAt = editingProgramCreatedAt,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    LightProgramDraftMapper.toSavedProgram(
                        draft = draft,
                        name = name,
                        deviceId = 0L, // TODO: replace with real deviceId argument
                        isActive = isActive
                    )
                }

                if (isActive) {
                    val existingPrograms = lightProgramsDataStoreManager.programsFlow.first()

                    val conflict = LightProgramScheduleConflictValidator.findConflict(
                        candidate = savedProgram,
                        existingPrograms = existingPrograms
                    )

                    if (conflict != null) {
                        eventsChannel.send(
                            DeviceLightProgramEditorEvent.ShowError(
                                "This program overlaps with ${conflict.name}"
                            )
                        )
                        return@launch
                    }
                }

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