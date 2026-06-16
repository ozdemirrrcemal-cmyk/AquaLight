package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.LightProgramRepository
import com.aqua.aqualight.data.devices.light.programs.capability.LightProgramFirmwareCapabilities
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramPreviewEngine
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramPreviewFrame
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramPreviewUseCase
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramTemporaryManualSender
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramDraftValidator
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramValidationResult
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSession
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeState
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val runtimeRepository = LightRuntimeRepository.get(
        context = appContext
    )

    private val programRepository = LightProgramRepository.get(
        context = appContext
    )

    private val firmwareCapabilities =
        LightProgramFirmwareCapabilities.CURRENT_ESP32_LP_POINTS_ONLY

    private val _uiState = MutableStateFlow(
        DeviceLightProgramEditorUiState.default(
            capabilities = firmwareCapabilities
        )
    )
    val uiState: StateFlow<DeviceLightProgramEditorUiState> =
        _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceLightProgramEditorEvent>()
    val events: SharedFlow<DeviceLightProgramEditorEvent> =
        _events.asSharedFlow()

    private var previewJob: Job? = null
    private var loadProgramJob: Job? = null
    private var runtimeCollectorJob: Job? = null
    private var runtimeSession: LightRuntimeSession? = null
    private var previewUseCase: LightProgramPreviewUseCase? = null
    private var hasReportedLivePreviewError: Boolean = false

    private var isInitialized: Boolean = false
    private var deviceId: Long = 0L
    private var programId: String? = null
    private var programName: String = "New Program"

    fun initialize(
        deviceId: Long,
        programId: String?
    ) {
        val normalizedProgramId = programId?.takeIf { it.isNotBlank() }
        val isSameEditorTarget = isInitialized &&
            this.deviceId == deviceId &&
            this.programId == normalizedProgramId

        if (isSameEditorTarget) {
            return
        }

        stopPreviewInternal(resetProgress = true)
        loadProgramJob?.cancel()
        runtimeCollectorJob?.cancel()
        runtimeSession?.release(CONSUMER_KEY)
        runtimeSession = null

        this.deviceId = deviceId
        this.programId = normalizedProgramId
        this.programName = if (normalizedProgramId == null) {
            "New Program"
        } else {
            "Program"
        }
        val session = if (deviceId > 0L) {
            runtimeRepository.session(deviceId)
        } else {
            null
        }
        runtimeSession = session
        previewUseCase = session?.let { runtimeSession ->
            LightProgramPreviewUseCase(
                temporaryManualSender = LightProgramTemporaryManualSender(
                    runtimeSession = runtimeSession
                )
            )
        }
        runtimeCollectorJob = session?.let { runtimeSession ->
            viewModelScope.launch {
                runtimeSession.state.collectLatest { runtimeState ->
                    applyRuntimeDeviceTime(runtimeState)
                }
            }
        }
        _uiState.value = DeviceLightProgramEditorUiState.default(
            capabilities = firmwareCapabilities
        )
        isInitialized = true

        if (deviceId <= 0L) {
            emitPreviewDeviceMissing()
            return
        }

        if (normalizedProgramId != null) {
            loadExistingProgram(
                deviceId = deviceId,
                programId = normalizedProgramId
            )
        }
    }

    fun onEditorVisible() {
        runtimeSession?.acquire(CONSUMER_KEY)
    }

    fun onEditorHidden() {
        runtimeSession?.release(CONSUMER_KEY)
    }

    fun updateStartTime(
        point: LightCurvePoint
    ) {
        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(start = point)
        }
    }

    fun updatePeakStartTime(
        point: LightCurvePoint
    ) {
        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(peakStart = point)
        }
    }

    fun updatePeakEndTime(
        point: LightCurvePoint
    ) {
        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(peakEnd = point)
        }
    }

    fun updateEndTime(
        point: LightCurvePoint
    ) {
        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(end = point)
        }
    }

    fun updateChannelValues(
        values: LightCurveChannelValues
    ) {
        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(channelValues = values.normalized())
        }
    }

    fun updateTransitionMode(
        mode: LightCurveTransitionMode
    ) {
        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(transitionMode = mode)
        }
    }

    fun updateRepeatEvery() {
        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.EVERY,
                selectedDays = DeviceLightProgramEditorUiState.EVERY_DAY_SELECTION
            )
        }
    }

    fun updateRepeatWeekdays() {
        if (!canUpdateWeeklyRepeatSelection()) {
            return
        }

        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.WEEK,
                selectedDays = setOf(1, 2, 3, 4, 5)
            )
        }
    }

    fun updateRepeatWeekend() {
        if (!canUpdateWeeklyRepeatSelection()) {
            return
        }

        stopPreviewForEdit()
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
        if (!canUpdateWeeklyRepeatSelection()) {
            return
        }

        stopPreviewForEdit()
        _uiState.update { state ->
            state.copy(
                repeatMode = RepeatMode.CUSTOM,
                selectedDays = days
            )
        }
    }


    fun validateBeforeProgramAction(): Boolean {
        return requireValidDraft()
    }

    fun startPreview(
        speed: PreviewSpeed
    ) {
        if (!requireValidDraft()) {
            return
        }

        if (!firmwareCapabilities.supportsTemporaryLivePreview) {
            emitUnavailablePreview()
            return
        }

        val useCase = previewUseCase
        if (useCase == null) {
            emitPreviewDeviceMissing()
            return
        }

        previewJob?.cancel()
        hasReportedLivePreviewError = false

        val draft = _uiState.value.draft
        val schedule = useCase.compileSchedule(draft)
        val durationMillis = speed.durationMillis
        val initialFrame = useCase.frameAt(
            schedule = schedule,
            elapsedMillis = 0L,
            previewDurationMillis = durationMillis
        )

        _uiState.update { state ->
            state.copy(
                previewSpeed = speed,
                isPreviewRunning = true,
                previewProgressPercent = initialFrame.progressPercent,
                previewSimulationTime = initialFrame.simulatedTime,
                previewOutputValues = initialFrame.outputValues
            )
        }

        previewJob = viewModelScope.launch {
            when (val result = useCase.beginLivePreview()) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> {
                    hasReportedLivePreviewError = true
                    _events.emit(
                        DeviceLightProgramEditorEvent.ShowError(
                            result.error.message
                        )
                    )
                    stopPreviewInternal(
                        resetProgress = true,
                        resumeDevice = false
                    )
                    return@launch
                }
            }

            val startMillis = SystemClock.elapsedRealtime()

            while (true) {
                val elapsedMillis = SystemClock.elapsedRealtime() - startMillis
                val frame = useCase.frameAt(
                    schedule = schedule,
                    elapsedMillis = elapsedMillis,
                    previewDurationMillis = durationMillis
                )

                applyPreviewFrame(
                    frame = frame,
                    isRunning = elapsedMillis < durationMillis
                )
                sendLivePreviewFrame(frame)

                if (elapsedMillis >= durationMillis) {
                    previewJob = null
                    stopLivePreviewOnDeviceNow()
                    break
                }

                delay(LightProgramPreviewEngine.DEFAULT_FRAME_INTERVAL_MILLIS)
            }
        }
    }

    fun stopPreview() {
        stopPreviewInternal(resetProgress = true)
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
        val cleanedName = name.trim()
        when (val result = LightProgramDraftValidator.validateName(cleanedName)) {
            LightProgramValidationResult.Valid -> Unit
            is LightProgramValidationResult.Invalid -> {
                emitValidationError(result.message)
                return
            }
        }

        if (!requireValidDraft()) {
            return
        }

        if (deviceId <= 0L) {
            emitPreviewDeviceMissing()
            return
        }

        val draft = _uiState.value.draft
        stopPreviewInternal(resetProgress = true)

        viewModelScope.launch {
            _events.emit(DeviceLightProgramEditorEvent.SetLoading(true))
            var savedLocally = false
            try {
                val savedProgram = programRepository.saveProgram(
                    deviceId = deviceId,
                    programId = programId,
                    name = cleanedName,
                    draft = draft,
                    makeActiveLocally = false
                )
                savedLocally = true

                val finalProgram = if (activateOnDevice) {
                    programRepository.activateProgram(
                        deviceId = deviceId,
                        programId = savedProgram.id
                    ).program
                } else {
                    savedProgram
                }

                programId = finalProgram.id
                programName = finalProgram.name

                _events.emit(
                    DeviceLightProgramEditorEvent.ShowMessage(
                        if (activateOnDevice) {
                            "Program saved, uploaded to the device and activated."
                        } else {
                            "Program saved."
                        }
                    )
                )
                _events.emit(DeviceLightProgramEditorEvent.NavigateBack)
            } catch (exception: Exception) {
                val fallbackMessage = if (savedLocally && activateOnDevice) {
                    "Program was saved, but device upload failed."
                } else {
                    "Program could not be saved."
                }
                _events.emit(
                    DeviceLightProgramEditorEvent.ShowError(
                        exception.message ?: fallbackMessage
                    )
                )
            } finally {
                _events.emit(DeviceLightProgramEditorEvent.SetLoading(false))
            }
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        previewJob = null
        loadProgramJob?.cancel()
        loadProgramJob = null
        runtimeCollectorJob?.cancel()
        runtimeCollectorJob = null
        runtimeSession?.release(CONSUMER_KEY)
        runtimeSession = null
        super.onCleared()
    }

    private fun loadExistingProgram(
        deviceId: Long,
        programId: String
    ) {
        loadProgramJob = viewModelScope.launch {
            _events.emit(DeviceLightProgramEditorEvent.SetLoading(true))
            try {
                val savedProgram = programRepository.getProgram(
                    deviceId = deviceId,
                    programId = programId
                )

                if (savedProgram == null) {
                    _events.emit(
                        DeviceLightProgramEditorEvent.ShowError(
                            "Program not found."
                        )
                    )
                    return@launch
                }

                programName = savedProgram.name
                _uiState.value = DeviceLightProgramEditorUiState.fromDraft(
                    draft = savedProgram.draft,
                    capabilities = firmwareCapabilities,
                    currentDeviceTime = _uiState.value.currentDeviceTime,
                    previewSpeed = _uiState.value.previewSpeed
                )
            } catch (exception: Exception) {
                _events.emit(
                    DeviceLightProgramEditorEvent.ShowError(
                        exception.message ?: "Program could not be loaded."
                    )
                )
            } finally {
                _events.emit(DeviceLightProgramEditorEvent.SetLoading(false))
            }
        }
    }

    private fun applyRuntimeDeviceTime(
        runtimeState: LightRuntimeState
    ) {
        val minuteOfDay = runtimeState.snapshot
            ?.deviceTime
            ?.currentMinuteOfDay
            ?: return

        val safeMinute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY)
        val point = LightCurvePoint.of(
            hour = safeMinute / 60,
            minute = safeMinute % 60
        )

        _uiState.update { state ->
            state.copy(
                currentDeviceTime = point
            )
        }
    }

    private fun canUpdateWeeklyRepeatSelection(): Boolean {
        return _uiState.value.repeatSelectionEnabled
    }

    private fun requireValidDraft(): Boolean {
        return when (val result = LightProgramDraftValidator.validate(_uiState.value.draft)) {
            LightProgramValidationResult.Valid -> true
            is LightProgramValidationResult.Invalid -> {
                emitValidationError(result.message)
                false
            }
        }
    }

    private fun emitValidationError(
        message: String
    ) {
        viewModelScope.launch {
            _events.emit(
                DeviceLightProgramEditorEvent.ShowError(
                    message
                )
            )
        }
    }

    private fun applyPreviewFrame(
        frame: LightProgramPreviewFrame,
        isRunning: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                isPreviewRunning = isRunning,
                previewProgressPercent = frame.progressPercent,
                previewSimulationTime = frame.simulatedTime,
                previewOutputValues = frame.outputValues
            )
        }
    }

    private fun stopPreviewForEdit() {
        val state = _uiState.value
        if (!state.isPreviewRunning &&
            state.previewProgressPercent == 0 &&
            state.previewSimulationTime == null
        ) {
            return
        }

        stopPreviewInternal(resetProgress = true)
    }

    private fun stopPreviewInternal(
        resetProgress: Boolean,
        resumeDevice: Boolean = true
    ) {
        val shouldResumeDevice = resumeDevice && (
            previewJob != null ||
                _uiState.value.isPreviewRunning ||
                _uiState.value.previewOutputValues != null
            )

        previewJob?.cancel()
        previewJob = null

        _uiState.update { state ->
            state.copy(
                isPreviewRunning = false,
                previewProgressPercent = if (resetProgress) {
                    0
                } else {
                    state.previewProgressPercent
                },
                previewSimulationTime = null,
                previewOutputValues = null
            )
        }

        if (shouldResumeDevice) {
            stopLivePreviewOnDeviceAsync()
        }
    }

    private suspend fun sendLivePreviewFrame(
        frame: LightProgramPreviewFrame
    ) {
        val useCase = previewUseCase ?: return
        when (val result = useCase.sendLivePreviewFrame(frame)) {
            is ApiResult.Success -> Unit
            is ApiResult.Error -> {
                if (!hasReportedLivePreviewError) {
                    hasReportedLivePreviewError = true
                    _events.emit(
                        DeviceLightProgramEditorEvent.ShowError(
                            result.error.message
                        )
                    )
                }
            }
        }
    }

    private fun stopLivePreviewOnDeviceAsync() {
        val useCase = previewUseCase ?: return
        viewModelScope.launch {
            stopLivePreviewOnDevice(
                useCase = useCase
            )
        }
    }

    private suspend fun stopLivePreviewOnDeviceNow() {
        val useCase = previewUseCase ?: return
        stopLivePreviewOnDevice(
            useCase = useCase
        )
    }

    private suspend fun stopLivePreviewOnDevice(
        useCase: LightProgramPreviewUseCase
    ) {
        when (val result = useCase.stopLivePreview()) {
            is ApiResult.Success -> Unit
            is ApiResult.Error -> {
                if (!hasReportedLivePreviewError) {
                    hasReportedLivePreviewError = true
                    _events.emit(
                        DeviceLightProgramEditorEvent.ShowError(
                            result.error.message
                        )
                    )
                }
            }
        }
    }

    private fun emitPreviewDeviceMissing() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightProgramEditorEvent.ShowError(
                    LIGHT_DEVICE_INFORMATION_MISSING
                )
            )
        }
    }

    private fun emitUnavailablePreview() {
        viewModelScope.launch {
            _events.emit(
                DeviceLightProgramEditorEvent.ShowError(
                    "Live preview is not supported by this firmware."
                )
            )
        }
    }

    private companion object {
        const val CONSUMER_KEY = "light-program-editor"
        const val MINUTES_PER_DAY = 24 * 60
    }
}
