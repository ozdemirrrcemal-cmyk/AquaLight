package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightDeviceCommandManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightProgramCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.CloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.DeviceLightProgramEditorUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewSpeed
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation.LightProgramDraftValidator
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation.LightProgramValidationResult
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper.LightProgramDraftMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelineBuilder
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelineEvaluator
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val lightProgramsDataStoreManager =
        LightProgramsDataStoreManager(appContext)

    private val lightProgramCommandManager =
        Esp32LightProgramCommandManager(
            context = appContext
        )

    private val lightRuntimeRepository =
        LightRuntimeRepository(
            commandManager = Esp32LightDeviceCommandManager(
                context = appContext
            )
        )

    private val _uiState =
        MutableStateFlow(DeviceLightProgramEditorUiState.default())

    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
        _uiState.asStateFlow()

    private val eventsChannel =
        Channel<DeviceLightProgramEditorEvent>(Channel.BUFFERED)

    val events =
        eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var liveStateJob: Job? = null

    private var previewJob: Job? = null
    private var previewSessionVersion: Long = 0L

    private var isSaveOperationRunning: Boolean = false

    private val liveRefreshOwnerKey =
        "DeviceLightProgramEditorViewModel_${System.identityHashCode(this)}"

    private var editingProgramId: String? = null
    private var editingProgramName: String? = null
    private var editingProgramDeviceId: Long = 0L
    private var editingProgramCreatedAt: Long = 0L

    fun initialize(
        deviceId: Long,
        programId: String?
    ) {
        val previousDeviceId = this.deviceId

        if (
            previousDeviceId > 0L &&
            previousDeviceId != deviceId
        ) {
            stopLiveRefresh(
                targetDeviceId = previousDeviceId
            )
        }

        this.deviceId = deviceId

        startLiveRefreshIfPossible()

        if (!programId.isNullOrBlank()) {
            loadProgram(programId)
        } else {
            clearEditingMetadata()
        }
    }

    fun isEditingExistingProgram(): Boolean {
        return !editingProgramId.isNullOrBlank()
    }

    fun currentProgramName(): String {
        return editingProgramName.orEmpty()
    }

    private fun clearEditingMetadata() {
        editingProgramId = null
        editingProgramName = null
        editingProgramDeviceId = 0L
        editingProgramCreatedAt = 0L
    }

    private fun startLiveRefreshIfPossible() {
        if (deviceId <= 0L) {
            return
        }

        LightDeviceLiveRefreshManager.start(
            context = appContext,
            deviceId = deviceId,
            ownerKey = liveRefreshOwnerKey
        )

        observeLiveDeviceTime()

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )
    }

    private fun stopLiveRefresh(
        targetDeviceId: Long
    ) {
        if (targetDeviceId <= 0L) {
            return
        }

        LightDeviceLiveRefreshManager.stop(
            deviceId = targetDeviceId,
            ownerKey = liveRefreshOwnerKey
        )
    }

    private fun observeLiveDeviceTime() {
        liveStateJob?.cancel()

        liveStateJob = viewModelScope.launch {
            LightDeviceLiveRefreshManager.observe(
                deviceId = deviceId
            ).collect { liveState ->
                val deviceTime = liveState.deviceTime
                    ?: return@collect

                _uiState.update { state ->
                    state.copy(
                        currentDeviceTime = deviceTime.curvePoint
                    )
                }
            }
        }
    }

    private fun loadProgram(
        programId: String
    ) {
        viewModelScope.launch {
            val program =
                lightProgramsDataStoreManager.getProgram(programId)

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

            if (deviceId <= 0L && program.deviceId > 0L) {
                deviceId = program.deviceId
                startLiveRefreshIfPossible()
            }

            _uiState.update { state ->
                state.copy(
                    start = program.draft.start,
                    peakStart = program.draft.peakStart,
                    peakEnd = program.draft.peakEnd,
                    end = program.draft.end,
                    channelValues = program.draft.channelValues.normalized(),
                    repeatMode = program.draft.repeatMode,
                    selectedDays = program.draft.selectedDays,
                    moonlightSettings = program.draft.moonlightSettings,
                    cloudSimulationSettings = program.draft.cloudSimulationSettings,
                    transitionMode = program.draft.transitionMode
                )
            }
        }
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
            state.copy(
                channelValues = values.normalized()
            )
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
        val safeDays = days
            .filter { day ->
                day in 1..7
            }
            .toSet()

        if (safeDays.isEmpty()) {
            viewModelScope.launch {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError(
                        "Select at least one day"
                    )
                )
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.CUSTOM,
                selectedDays = safeDays
            )
        }
    }

    fun updateMoonlight(
        settings: MoonlightSettings
    ) {
        _uiState.update { state ->
            state.copy(moonlightSettings = settings)
        }
    }

    fun updateCloudSimulation(
        settings: CloudSimulationSettings
    ) {
        _uiState.update { state ->
            state.copy(cloudSimulationSettings = settings)
        }
    }

    fun updateTransitionMode(
        mode: LightCurveTransitionMode
    ) {
        _uiState.update { state ->
            state.copy(transitionMode = mode)
        }
    }

    fun updatePreviewSpeed(
        speed: PreviewSpeed
    ) {
        _uiState.update { state ->
            state.copy(previewSpeed = speed)
        }
    }

    fun saveProgram(
        name: String,
        activateOnDevice: Boolean
    ) {
        if (isSaveOperationRunning) {
            return
        }

        isSaveOperationRunning = true

        viewModelScope.launch {
            var shouldShowLoading = false
            var successMessage: String? = null
            var shouldNavigateBack = false

            try {
                if (_uiState.value.isPreviewRunning) {
                    eventsChannel.send(
                        DeviceLightProgramEditorEvent.ShowError(
                            "Stop preview before saving"
                        )
                    )
                    return@launch
                }

                val draft =
                    _uiState.value.draft

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

                val existingEditingProgram = editingProgramId?.let { id ->
                    lightProgramsDataStoreManager.getProgram(id)
                }

                if (
                    editingProgramId != null &&
                    existingEditingProgram == null
                ) {
                    eventsChannel.send(
                        DeviceLightProgramEditorEvent.ShowError(
                            "Program could not be found"
                        )
                    )
                    return@launch
                }

                val resolvedDeviceId = resolveProgramDeviceId(
                    existingProgram = existingEditingProgram
                )

                if (resolvedDeviceId <= 0L) {
                    eventsChannel.send(
                        DeviceLightProgramEditorEvent.ShowError(
                            "Device information is missing"
                        )
                    )
                    return@launch
                }

                eventsChannel.send(
                    DeviceLightProgramEditorEvent.SetLoading(true)
                )
                shouldShowLoading = true

                val savedProgram = buildSavedProgram(
                    name = name,
                    activateOnDevice = activateOnDevice,
                    existingProgram = existingEditingProgram,
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

                val existingPrograms =
                    lightProgramsDataStoreManager.programsFlow.first()

                val allProgramsAfterSave =
                    existingPrograms.withSavedProgram(savedProgram)

                val activeProgramsForDevice =
                    allProgramsAfterSave.activeProgramsForDevice(
                        deviceId = savedProgram.deviceId
                    )

                if (savedProgram.isActive) {
                    val loadResult =
                        lightProgramCommandManager.loadPrograms(
                            deviceId = savedProgram.deviceId,
                            programs = activeProgramsForDevice
                        )

                    if (!loadResult.isSuccess) {
                        eventsChannel.send(
                            DeviceLightProgramEditorEvent.ShowError(
                                loadResult.message ?: "Program could not be loaded to device"
                            )
                        )
                        return@launch
                    }
                }

                lightProgramsDataStoreManager.saveProgram(savedProgram)

                editingProgramId = savedProgram.id
                editingProgramName = savedProgram.name
                editingProgramDeviceId = savedProgram.deviceId
                editingProgramCreatedAt = savedProgram.createdAt

                if (savedProgram.isActive) {
                    LightDeviceLiveRefreshManager.refreshNow(
                        context = appContext,
                        deviceId = savedProgram.deviceId
                    )
                }

                successMessage = if (savedProgram.isActive) {
                    if (activateOnDevice) {
                        "Program loaded to device"
                    } else {
                        "Active program updated"
                    }
                } else {
                    "Program saved"
                }

                shouldNavigateBack = true
            } catch (error: Throwable) {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError(
                        error.message ?: "Program could not be saved"
                    )
                )
            } finally {
                if (shouldShowLoading) {
                    eventsChannel.send(
                        DeviceLightProgramEditorEvent.SetLoading(false)
                    )
                }

                isSaveOperationRunning = false
            }

            if (successMessage != null) {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowMessage(
                        successMessage.orEmpty()
                    )
                )
            }

            if (shouldNavigateBack) {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.NavigateBack
                )
            }
        }
    }

    private fun buildSavedProgram(
        name: String,
        activateOnDevice: Boolean,
        existingProgram: SavedLightProgram?,
        deviceId: Long,
        draft: LightProgramDraft
    ): SavedLightProgram {
        val now = System.currentTimeMillis()

        val cleanName = name
            .trim()
            .ifBlank {
                existingProgram?.name
                    ?: editingProgramName
                    ?: "Light Program"
            }

        val shouldBeActive =
            activateOnDevice || existingProgram?.isActive == true

        if (existingProgram != null) {
            return existingProgram.copy(
                deviceId = deviceId,
                name = cleanName,
                draft = draft,
                isActive = shouldBeActive,
                updatedAt = now
            )
        }

        return LightProgramDraftMapper.toSavedProgram(
            draft = draft,
            name = cleanName,
            deviceId = deviceId,
            isActive = shouldBeActive
        )
    }

    private suspend fun findConflictForProgram(
        savedProgram: SavedLightProgram
    ): SavedLightProgram? {
        val existingPrograms =
            lightProgramsDataStoreManager.programsFlow.first()

        val comparablePrograms = existingPrograms.filter { program ->
            program.deviceId == savedProgram.deviceId &&
                program.isActive &&
                program.id != savedProgram.id
        }

        return LightProgramScheduleConflictValidator.findConflict(
            candidate = savedProgram,
            existingPrograms = comparablePrograms
        )
    }

    private fun resolveProgramDeviceId(
        existingProgram: SavedLightProgram?
    ): Long {
        return when {
            existingProgram?.deviceId != null && existingProgram.deviceId > 0L -> {
                existingProgram.deviceId
            }

            editingProgramDeviceId > 0L -> {
                editingProgramDeviceId
            }

            deviceId > 0L -> {
                deviceId
            }

            else -> {
                0L
            }
        }
    }

    private fun List<SavedLightProgram>.withSavedProgram(
        savedProgram: SavedLightProgram
    ): List<SavedLightProgram> {
        var replaced = false

        val updated = map { program ->
            if (program.id == savedProgram.id) {
                replaced = true
                savedProgram
            } else {
                program
            }
        }

        return if (replaced) {
            updated
        } else {
            updated + savedProgram
        }
    }

    private fun List<SavedLightProgram>.activeProgramsForDevice(
        deviceId: Long
    ): List<SavedLightProgram> {
        return filter { program ->
            program.deviceId == deviceId &&
                program.isActive
        }
    }

    fun startPreview(
        speed: PreviewSpeed
    ) {
        previewSessionVersion += 1L
        val sessionVersion = previewSessionVersion

        updatePreviewSpeed(speed)

        previewJob?.cancel()

        _uiState.update { state ->
            state.copy(
                previewSimulationTime = null,
                isPreviewRunning = false,
                previewProgressPercent = 0
            )
        }

        var runningJob: Job? = null

        runningJob = viewModelScope.launch {
            var previewStarted = false
            var previewFinishedNormally = false

            try {
                if (deviceId <= 0L) {
                    eventsChannel.send(
                        DeviceLightProgramEditorEvent.ShowError(
                            "Device information is missing"
                        )
                    )
                    return@launch
                }

                val draft =
                    _uiState.value.draft

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

                val timeline =
                    LightProgramTimelineBuilder.build(draft)

                previewStarted = true

                _uiState.update { state ->
                    state.copy(
                        previewSimulationTime = pointFromMinute(0),
                        isPreviewRunning = true,
                        previewProgressPercent = 0
                    )
                }

                val frameCount = PREVIEW_FRAME_COUNT
                val delayMs = speed.previewFrameDelayMillis()

                for (frame in 0 until frameCount) {
                    if (!isActive) {
                        return@launch
                    }

                    val minute =
                        ((24 * 60) * frame) / (frameCount - 1)

                    val progressPercent =
                        ((frame.toDouble() / (frameCount - 1).toDouble()) * 100.0)
                            .roundToInt()
                            .coerceIn(0, 100)

                    _uiState.update { state ->
                        state.copy(
                            previewSimulationTime = pointFromMinute(minute),
                            isPreviewRunning = true,
                            previewProgressPercent = progressPercent
                        )
                    }

                    val output =
                        LightProgramTimelineEvaluator.outputAtMinute(
                            timeline = timeline,
                            minute = minute
                        )

                    val result =
                        lightRuntimeRepository.applyManualScene(
                            deviceId = deviceId,
                            sceneName = "Preview Day",
                            red = output.red,
                            green = output.green,
                            blue = output.blue,
                            white = output.white
                        )

                    if (!result.isSuccess) {
                        eventsChannel.send(
                            DeviceLightProgramEditorEvent.ShowError(
                                result.message ?: "Preview could not be sent to device"
                            )
                        )
                        return@launch
                    }

                    delay(delayMs)
                }

                previewFinishedNormally = true

                _uiState.update { state ->
                    state.copy(
                        previewProgressPercent = 100
                    )
                }
            } finally {
                if (
                    previewStarted &&
                    previewSessionVersion == sessionVersion
                ) {
                    withContext(NonCancellable) {
                        resumeAutoAfterPreview(
                            finalProgressPercent = if (previewFinishedNormally) {
                                100
                            } else {
                                0
                            }
                        )
                    }
                } else if (previewSessionVersion == sessionVersion) {
                    _uiState.update { state ->
                        state.copy(
                            previewSimulationTime = null,
                            isPreviewRunning = false,
                            previewProgressPercent = 0
                        )
                    }
                }

                if (previewJob == runningJob) {
                    previewJob = null
                }
            }
        }

        previewJob = runningJob
    }

    fun stopPreview() {
        val runningJob = previewJob

        if (runningJob?.isActive == true) {
            runningJob.cancel()
            return
        }

        _uiState.update { state ->
            state.copy(
                previewSimulationTime = null,
                isPreviewRunning = false,
                previewProgressPercent = 0
            )
        }
    }

    private suspend fun resumeAutoAfterPreview(
        finalProgressPercent: Int
    ) {
        lightRuntimeRepository.resumeAuto(
            deviceId = deviceId
        )

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )

        _uiState.update { state ->
            state.copy(
                previewSimulationTime = null,
                isPreviewRunning = false,
                previewProgressPercent = finalProgressPercent.coerceIn(0, 100)
            )
        }
    }

    private fun PreviewSpeed.previewFrameDelayMillis(): Long {
        val totalDurationMillis =
            durationMinutes * 60_000L

        return (totalDurationMillis / (PREVIEW_FRAME_COUNT - 1))
            .coerceAtLeast(100L)
    }

    private fun pointFromMinute(
        minute: Int
    ): LightCurvePoint {
        val safeMinute = minute.coerceIn(
            0,
            24 * 60
        )

        if (safeMinute >= 24 * 60) {
            return LightCurvePoint.of(
                hour = 0,
                minute = 0
            )
        }

        return LightCurvePoint.of(
            hour = safeMinute / 60,
            minute = safeMinute % 60
        )
    }

    override fun onCleared() {
        previewJob?.cancel()
        previewJob = null

        _uiState.update { state ->
            state.copy(
                previewSimulationTime = null,
                isPreviewRunning = false,
                previewProgressPercent = 0
            )
        }

        liveStateJob?.cancel()

        if (deviceId > 0L) {
            stopLiveRefresh(
                targetDeviceId = deviceId
            )
        }

        super.onCleared()
    }

    companion object {
        private const val PREVIEW_FRAME_COUNT = 96
    }
}