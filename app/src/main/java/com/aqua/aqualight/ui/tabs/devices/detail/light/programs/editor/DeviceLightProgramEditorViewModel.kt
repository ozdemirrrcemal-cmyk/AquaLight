package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightProgramCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
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
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation.LightProgramScheduleConflictValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightDeviceCommandManager
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
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

    private var previewJob: Job? = null

    private val _uiState =
    MutableStateFlow(DeviceLightProgramEditorUiState.default())

    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
    _uiState.asStateFlow()

    private val eventsChannel =
    Channel<DeviceLightProgramEditorEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    private var deviceId: Long = 0L
    private var liveStateJob: Job? = null

    private val liveRefreshOwnerKey =
    "DeviceLightProgramEditorViewModel_${System.identityHashCode(this)}"

    private var editingProgramId: String? = null
    private var editingProgramName: String? = null
    private var editingProgramDeviceId: Long = 0L
    private var editingProgramCreatedAt: Long = 0L
    private var editingProgramWasActive: Boolean = false

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
        }
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
            ).collect {
                liveState ->
                val deviceTime = liveState.deviceTime
                ?: return@collect

                _uiState.update {
                    state ->
                    state.copy(
                        currentDeviceTime = deviceTime.curvePoint
                    )
                }
            }
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

            if (deviceId <= 0L && program.deviceId > 0L) {
                deviceId = program.deviceId
                startLiveRefreshIfPossible()
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
                val existingPrograms =
                lightProgramsDataStoreManager.programsFlow.first()

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

                if (savedProgram.isActive) {
                    LightDeviceLiveRefreshManager.refreshNow(
                        context = appContext,
                        deviceId = savedProgram.deviceId
                    )
                }

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

    private fun buildSavedProgram(
        name: String,
        isActive: Boolean,
        deviceId: Long,
        draft: LightProgramDraft
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

        previewJob?.cancel()

        _uiState.update {
            state ->
            state.copy(
                previewSimulationTime = null,
                isPreviewRunning = false
            )
        }

        previewJob = viewModelScope.launch {
            if (deviceId <= 0L) {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowError(
                        "Device information is missing"
                    )
                )
                return@launch
            }

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

            var previewStarted = false
            var previewFinishedNormally = false

            try {
                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowMessage(
                        "Preview started: ${speed.label}"
                    )
                )

                previewStarted = true

                val frameCount = PREVIEW_FRAME_COUNT
                val delayMs = speed.previewFrameDelayMillis()

                for (frame in 0 until frameCount) {
                    if (!isActive) return@launch

                    val minute = ((24 * 60) * frame) / (frameCount - 1)

                    _uiState.update {
                        state ->
                        state.copy(
                            previewSimulationTime = pointFromMinute(minute),
                            isPreviewRunning = true
                        )
                    }

                    val red = calculatePreviewValueAtMinute(
                        draft = draft,
                        minute = minute,
                        peakPercent = draft.channelValues.red
                    )

                    val green = calculatePreviewValueAtMinute(
                        draft = draft,
                        minute = minute,
                        peakPercent = draft.channelValues.green
                    )

                    val blue = calculatePreviewValueAtMinute(
                        draft = draft,
                        minute = minute,
                        peakPercent = draft.channelValues.blue
                    )

                    val white = calculatePreviewValueAtMinute(
                        draft = draft,
                        minute = minute,
                        peakPercent = draft.channelValues.white
                    )

                    val result = lightRuntimeRepository.applyManualScene(
                        deviceId = deviceId,
                        sceneName = "Preview Day",
                        red = red,
                        green = green,
                        blue = blue,
                        white = white
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

                eventsChannel.send(
                    DeviceLightProgramEditorEvent.ShowMessage(
                        "Preview finished"
                    )
                )
            } finally {
                if (previewStarted) {
                    withContext(NonCancellable) {
                        resumeAutoAfterPreview()
                    }
                } else {
                    _uiState.update {
                        state ->
                        state.copy(
                            previewSimulationTime = null,
                            isPreviewRunning = false
                        )
                    }
                }

                if (previewFinishedNormally) {
                    previewJob = null
                }
            }
        }
    }

    private fun calculatePreviewValueAtMinute(
        draft: LightProgramDraft,
        minute: Int,
        peakPercent: Int
    ): Int {
        val safePeak = peakPercent.coerceIn(0, 100)

        if (safePeak <= 0) {
            return 0
        }

        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = draft.start.totalMinutes,
            peakStartMinute = draft.peakStart.totalMinutes,
            peakEndMinute = draft.peakEnd.totalMinutes,
            endMinute = LightProgramTimeMath.endMinutes(draft.end),
            peakPercent = safePeak,
            transitionMode = draft.transitionMode
        ).sortedBy {
            point ->
            point.x
        }

        if (points.isEmpty()) {
            return 0
        }

        val currentMinute = minute.toDouble()

        val previous = points.lastOrNull {
            point ->
            point.x <= currentMinute
        }

        val next = points.firstOrNull {
            point ->
            point.x >= currentMinute
        }

        val value = when {
            previous == null -> points.first().y
            next == null -> points.last().y
            previous.x == next.x -> previous.y

            else -> {
                val progress =
                (currentMinute - previous.x) / (next.x - previous.x)

                previous.y + ((next.y - previous.y) * progress)
            }
        }

        return value
        .roundToInt()
        .coerceIn(0, 100)
    }

    private suspend fun resumeAutoAfterPreview() {
        lightRuntimeRepository.resumeAuto(
            deviceId = deviceId
        )

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )

        _uiState.update {
            state ->
            state.copy(
                previewSimulationTime = null,
                isPreviewRunning = false
            )
        }
    }

    private fun PreviewSpeed.previewFrameDelayMillis(): Long {
        return when (name) {
            "ONE_MINUTE" -> 625L
            "TWO_MINUTES" -> 1_250L
            "FIVE_MINUTES" -> 3_125L
            "FAST" -> 300L
            "NORMAL" -> 625L
            "SLOW" -> 1_250L
            else -> 625L
        }
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

        _uiState.update {
            state ->
            state.copy(
                previewSimulationTime = null,
                isPreviewRunning = false
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