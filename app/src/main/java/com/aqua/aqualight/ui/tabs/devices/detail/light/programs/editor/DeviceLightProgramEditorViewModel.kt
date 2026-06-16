package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.capability.LightProgramFirmwareCapabilities
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramPreviewEngine
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramPreviewFrame
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramPreviewUseCase
import com.aqua.aqualight.data.devices.light.programs.preview.LightProgramTemporaryManualSender
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DATA_LAYER_NOT_CONNECTED
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Temporary UI shell for the program editor.
 *
 * It keeps local form editing alive without loading from or saving to any
 * external Light source. Preview is intentionally compiled through the same
 * controller-ready point schedule that the repository will later upload.
 */
class DeviceLightProgramEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val runtimeRepository = LightRuntimeRepository.get(
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
    private var previewUseCase: LightProgramPreviewUseCase? = null
    private var hasReportedLivePreviewError: Boolean = false

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
        stopPreviewInternal(resetProgress = true)
        previewUseCase = if (deviceId > 0L) {
            LightProgramPreviewUseCase(
                temporaryManualSender = LightProgramTemporaryManualSender(
                    runtimeSession = runtimeRepository.session(deviceId)
                )
            )
        } else {
            null
        }
        _uiState.value = DeviceLightProgramEditorUiState.default(
            capabilities = firmwareCapabilities
        )
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


    fun startPreview(
        speed: PreviewSpeed
    ) {
        if (!firmwareCapabilities.supportsTemporaryLivePreview) {
            emitUnavailable()
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
        programName = name.ifBlank { programName }
        stopPreviewInternal(resetProgress = true)
        emitUnavailable()
    }

    override fun onCleared() {
        previewJob?.cancel()
        previewJob = null
        super.onCleared()
    }


    private fun canUpdateWeeklyRepeatSelection(): Boolean {
        return _uiState.value.repeatSelectionEnabled
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
