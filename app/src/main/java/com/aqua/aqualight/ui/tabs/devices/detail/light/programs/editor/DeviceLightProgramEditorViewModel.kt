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
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightProgramCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeRepository

class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val lightProgramsDataStoreManager =
    LightProgramsDataStoreManager(application.applicationContext)

    private val lightDeviceTimeRepository =
    LightDeviceTimeRepository(
        context = application.applicationContext
    )

    private val lightProgramCommandManager =
    Esp32LightProgramCommandManager(
        context = application.applicationContext
    )

    private val _uiState =
    MutableStateFlow(DeviceLightProgramEditorUiState.default())

    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
    _uiState.asStateFlow()

    private val eventsChannel =
    Channel<DeviceLightProgramEditorEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L

    private var editingProgramId: String? = null
    private var editingProgramName: String? = null
    private var editingProgramDeviceId: Long = 0L
    private var editingProgramCreatedAt: Long = 0L
    private var editingProgramWasActive: Boolean = false

    fun initialize(
        deviceId: Long,
        programId: String?
    ) {
        this.deviceId = deviceId

        refreshDeviceTime()

        if (!programId.isNullOrBlank()) {
            loadProgram(programId)
        }
    }

    fun updateStartTime(
        point: LightCurvePoint
    ) {
        _uiState.update {
            it.copy(start = point)
        }
    }

    fun updatePeakStartTime(
        point: LightCurvePoint
    ) {
        _uiState.update {
            it.copy(peakStart = point)
        }
    }

    fun updatePeakEndTime(
        point: LightCurvePoint
    ) {
        _uiState.update {
            it.copy(peakEnd = point)
        }
    }

    fun updateEndTime(
        point: LightCurvePoint
    ) {
        _uiState.update {
            it.copy(end = point)
        }
    }

    fun updateChannelValues(
        values: LightCurveChannelValues
    ) {
        _uiState.update {
            it.copy(channelValues = values)
        }
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

    fun updateCustomDays(
        days: Set<Int>
    ) {
        _uiState.update {
            it.copy(
                repeatMode = RepeatMode.CUSTOM,
                selectedDays = days
            )
        }
    }

    fun updateMoonlight(
        settings: MoonlightSettings
    ) {
        _uiState.update {
            it.copy(moonlightSettings = settings)
        }
    }

    fun updateCloudSimulation(
        settings: CloudSimulationSettings
    ) {
        _uiState.update {
            it.copy(cloudSimulationSettings = settings)
        }
    }

    fun updateTransitionMode(
        mode: LightCurveTransitionMode
    ) {
        _uiState.update {
            it.copy(transitionMode = mode)
        }
    }

    fun updatePreviewSpeed(
        speed: PreviewSpeed
    ) {
        _uiState.update {
            it.copy(previewSpeed = speed)
        }
    }

    fun updateDeviceTime(
        hour: Int,
        minute: Int
    ) {
        _uiState.update {
            it.copy(
                currentDeviceTime = LightCurvePoint.of(
                    hour = hour,
                    minute = minute
                )
            )
        }
    }

    private fun loadProgram(
        programId: String
    ) {
        viewModelScope.launch {
            val program = lightProgramsDataStoreManager.getProgram(programId)

            if (program == null) {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError(
                        "Program could not be found"
                    )
                )
                return@launch
            }

            editingProgramId = program.id
            editingProgramName = program.name
            editingProgramDeviceId = program.deviceId
            editingProgramCreatedAt = program.createdAt
            editingProgramWasActive = program.isActive

            if (deviceId <= 0L) {
                deviceId = program.deviceId
            }

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
            val draft = _uiState.value.draft

            when (val validation = LightProgramDraftValidator.validate(draft)) {
                LightProgramValidationResult.Valid -> Unit

                is LightProgramValidationResult.Invalid -> {
                    eventsChannel.send(
                        DeviceLightProgramEditorEvent.ShowError(
                            validation.message
                        )
                    )
                    return@launch
                }
            }

            val resolvedDeviceId = resolveProgramDeviceId()

            if (resolvedDeviceId <= 0L) {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError(
                        "Device information is missing"
                    )
                )
                return@launch
            }

            val savedProgram = buildSavedProgram(
                name = name,
                isActive = isActive,
                deviceId = resolvedDeviceId,
                draft = draft
            )

            if (savedProgram.isActive) {
                val conflict = findConflictForProgram(savedProgram)

                if (conflict != null) {
                    eventsChannel.send(
                        DeviceLightProgramEditorEvent.ShowError(
                            "This program overlaps with ${conflict.name}"
                        )
                    )
                    return@launch
                }
            }

            runCatching {
                val existingPrograms = lightProgramsDataStoreManager.programsFlow.first()

                val programsToLoad = if (savedProgram.isActive) {
                    existingPrograms
                    .filter {
                        program ->
                        program.deviceId == savedProgram.deviceId &&
                        program.isActive &&
                        program.id != savedProgram.id
                    } + savedProgram
                } else {
                    emptyList()
                }

                if (savedProgram.isActive) {
                    val loadResult = lightProgramCommandManager.loadPrograms(
                        deviceId = savedProgram.deviceId,
                        programs = programsToLoad
                    )

                    if (!loadResult.isSuccess) {
                        throw IllegalStateException(
                            loadResult.message ?: "Program could not be loaded to device"
                        )
                    }
                }

                lightProgramsDataStoreManager.saveProgram(savedProgram)
            }.onSuccess {
                editingProgramId = savedProgram.id
                editingProgramName = savedProgram.name
                editingProgramDeviceId = savedProgram.deviceId
                editingProgramCreatedAt = savedProgram.createdAt
                editingProgramWasActive = savedProgram.isActive

                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowMessage(
                        if (isActive) {
                            "Program loaded to device"
                        } else {
                            "Program saved"
                        }
                    )
                )

                eventsChannel.send(DeviceLightProgramEditorEvent.NavigateBack)
            }.onFailure {
                error ->
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError(
                        error.message ?: "Program could not be saved"
                    )
                )
            }
        }
    }

    private fun refreshDeviceTime() {
        viewModelScope.launch {
            val timeState = lightDeviceTimeRepository.readDeviceTime(
                deviceId = deviceId,
                fallbackToPhone = true
            )

            updateDeviceTime(
                hour = timeState.hour,
                minute = timeState.minute
            )
        }
    }

    private fun buildSavedProgram(
        name: String,
        isActive: Boolean,
        deviceId: Long,
        draft: com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
    ): SavedLightProgram {
        val cleanName = name.ifBlank {
            editingProgramName.orEmpty().ifBlank {
                "Light Program"
            }
        }

        val shouldBeActive = if (isActive) {
            true
        } else {
            editingProgramWasActive
        }

        return if (editingProgramId != null) {
            SavedLightProgram(
                id = editingProgramId.orEmpty(),
                deviceId = deviceId,
                name = cleanName,
                draft = draft,
                isActive = shouldBeActive,
                createdAt = editingProgramCreatedAt.takeIf {
                    it > 0L
                } ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            LightProgramDraftMapper.toSavedProgram(
                draft = draft,
                name = cleanName,
                deviceId = deviceId,
                isActive = isActive
            )
        }
    }

    private suspend fun findConflictForProgram(
        savedProgram: SavedLightProgram
    ): SavedLightProgram? {
        val existingPrograms = lightProgramsDataStoreManager.programsFlow.first()

        val comparablePrograms = existingPrograms.filter {
            program ->
            program.deviceId == savedProgram.deviceId &&
            program.isActive &&
            program.id != savedProgram.id
        }

        return LightProgramScheduleConflictValidator.findConflict(
            candidate = savedProgram,
            existingPrograms = comparablePrograms
        )
    }

    private fun resolveProgramDeviceId(): Long {
        return when {
            editingProgramDeviceId > 0L -> editingProgramDeviceId
            deviceId > 0L -> deviceId
            else -> 0L
        }
    }

    fun startPreview(
        speed: PreviewSpeed
    ) {
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