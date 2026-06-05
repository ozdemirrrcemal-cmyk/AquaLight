package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphSegment
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeRepository
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightDeviceTimeReader
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeState

class DeviceLightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val lightProgramsDataStoreManager =
    LightProgramsDataStoreManager(application.applicationContext)

    private val lightDeviceTimeRepository =
    LightDeviceTimeRepository(
        context = application.applicationContext
    )

    private val deviceTimeFlow =
    MutableStateFlow(
        Esp32LightDeviceTimeReader.phoneFallback()
    )

    private val _uiState = MutableStateFlow(
        createEmptyState(System.currentTimeMillis())
    )

    val uiState: StateFlow<DeviceLightDashboardUiState> =
    _uiState.asStateFlow()

    private val lightRuntimeRepository =
    LightRuntimeRepository()

    private var deviceId: Long = 0L
    private var observeJob: Job? = null
    private var clockJob: Job? = null

    fun initialize(
        deviceId: Long
    ) {
        this.deviceId = deviceId

        observeJob?.cancel()
        clockJob?.cancel()

        startClock()

        observeJob = viewModelScope.launch {
            combine(
                lightProgramsDataStoreManager.programsFlow,
                lightRuntimeRepository.observeManualRuntime(deviceId),
                deviceTimeFlow
            ) {
                programs, manualRuntime, deviceTime ->
                Triple(programs, manualRuntime, deviceTime)
            }.collect {
                (programs, manualRuntime, deviceTime) ->
                val activeProgramsForDevice = programs
                .filter {
                    program ->
                    program.deviceId == this@DeviceLightViewModel.deviceId &&
                    program.isActive
                }

                vaval baseState = createStateFromPrograms(
                    programs = activeProgramsForDevice,
                    deviceTime = deviceTime
                )

                val finalState = if (manualRuntime.isManualMode) {
                    createManualRuntimeState(
                        baseState = baseState,
                        manualRuntime = manualRuntime
                    )
                } else {
                    baseState
                }

                _uiState.update {
                    finalState
                }
            }
        }
    }

    fun refreshNow() {
        refreshDeviceTime()
    }

    private fun startClock() {
        refreshDeviceTime()

        clockJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                refreshDeviceTime()
            }
        }
    }

    private fun refreshDeviceTime() {
        viewModelScope.launch {
            val timeState = lightDeviceTimeRepository.readDeviceTime(
                deviceId = deviceId,
                fallbackToPhone = true
            )

            deviceTimeFlow.value = timeState
        }
    }

    private fun createManualRuntimeState(
        baseState: DeviceLightDashboardUiState,
        manualRuntime: com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
    ): DeviceLightDashboardUiState {
        val sceneName = manualRuntime.activeSceneName
        .orEmpty()
        .ifBlank {
            if (manualRuntime.isManualScene) {
                "Manual Scene"
            } else {
                "Manual Control"
            }
        }

        val outputPercent = if (manualRuntime.isPowerOn) {
            manualRuntime.masterOutputPercent
        } else {
            0
        }

        val pausedGraphState = baseState.todayPlanGraphState.copy(
            showPausedOverlay = true,
            pausedOverlayTitle = "Auto paused",
            pausedOverlaySubtitle = if (manualRuntime.isManualScene) {
                "$sceneName is active"
            } else {
                "Manual control is active"
            }
        )

        return baseState.copy(
            activeProgramName = sceneName,
            runStatus = if (manualRuntime.isManualScene) {
                "Manual Scene Active"
            } else {
                "Manual Mode Active"
            },
            currentWattText = "-- W",
            outputPercentText = "$outputPercent%",
            nextEventText = "Auto paused",
            timelineStatusText = "Paused · Today plan below",
            todayPlanGraphState = pausedGraphState
        )
    }

    private fun createStateFromPrograms(
        programs: List<SavedLightProgram>,
        deviceTime: LightDeviceTimeState
    ): DeviceLightDashboardUiState {
        val currentTime = deviceTime.curvePoint
        val currentMinute = currentTime.totalMinutes

        if (programs.isEmpty()) {
            return createEmptyState(nowMillis)
        }

        val todayPrograms = programs
        .filter {
            program ->
            isScheduledToday(program)
        }
        .sortedBy {
            program ->
            program.draft.start.totalMinutes
        }

        if (todayPrograms.isEmpty()) {
            return DeviceLightDashboardUiState(
                activeProgramName = "No program today",
                runStatus = "Active schedules are not planned for today",
                onlineStatusText = "ONLINE",
                currentWattText = "-- W",
                outputPercentText = "0%",
                deviceTimeText = currentTime.label,
                nextEventText = "Next scheduled day",
                timelineStatusText = "No active plan today",
                todayPlanGraphState = TodayLightPlanGraphState.empty(
                    currentTime = currentTime
                )
            )
        }

        val runningProgram = todayPrograms.firstOrNull {
            program ->
            isProgramRunningAt(
                program = program,
                minute = currentMinute
            )
        }

        val nextProgramToday = todayPrograms.firstOrNull {
            program ->
            program.draft.start.totalMinutes > currentMinute
        }

        val displayProgram = runningProgram
        ?: nextProgramToday
        ?: todayPrograms.firstOrNull()

        val currentOutput = runningProgram?.let {
            program ->
            calculateCurrentOutputPercent(
                program = program,
                currentMinute = currentMinute
            )
        } ?: 0

        val graphSegments = todayPrograms.map {
            program ->
            val isCurrent = runningProgram?.id == program.id
            val isNext = runningProgram == null &&
            nextProgramToday?.id == program.id

            TodayLightPlanGraphSegment(
                id = program.id,
                name = program.name,
                start = program.draft.start,
                peakStart = program.draft.peakStart,
                peakEnd = program.draft.peakEnd,
                end = program.draft.end,
                outputPercent = program.maxOutputPercent(),
                transitionMode = program.draft.transitionMode,
                isCurrent = isCurrent,
                isNext = isNext
            )
        }

        return DeviceLightDashboardUiState(
            activeProgramName = displayProgram?.name ?: "No active program",
            runStatus = buildRunStatus(
                runningProgram = runningProgram,
                nextProgramToday = nextProgramToday
            ),
            onlineStatusText = "ONLINE",
            currentWattText = "-- W",
            outputPercentText = "$currentOutput%",
            deviceTimeText = currentTime.label,
            nextEventText = buildNextEventText(
                runningProgram = runningProgram,
                nextProgramToday = nextProgramToday,
                todayPrograms = todayPrograms,
                currentMinute = currentMinute
            ),
            timelineStatusText = if (todayPrograms.size == 1) {
                "Today active plan"
            } else {
                "Today active plan · ${todayPrograms.size} programs"
            },
            todayPlanGraphState = TodayLightPlanGraphState(
                currentTime = currentTime,
                segments = graphSegments
            )
        )
    }

    private fun createEmptyState(
        nowMillis: Long
    ): DeviceLightDashboardUiState {
        val currentTime = currentLightPoint(nowMillis)

        return DeviceLightDashboardUiState(
            activeProgramName = "No active program",
            runStatus = "Create or load a light program",
            onlineStatusText = "ONLINE",
            currentWattText = "-- W",
            outputPercentText = "0%",
            deviceTimeText = currentTime.label,
            nextEventText = "No upcoming event",
            timelineStatusText = "No active plan",
            todayPlanGraphState = TodayLightPlanGraphState.empty(
                currentTime = currentTime
            )
        )
    }

    private fun buildRunStatus(
        runningProgram: SavedLightProgram?,
        nextProgramToday: SavedLightProgram?
    ): String {
        return when {
            runningProgram != null -> {
                "Auto program running"
            }

            nextProgramToday != null -> {
                "Waiting for ${nextProgramToday.draft.start.label}"
            } else -> {
                "Programs completed for today"
            }
        }
    }

    private fun buildNextEventText(
        runningProgram: SavedLightProgram?,
        nextProgramToday: SavedLightProgram?,
        todayPrograms: List<SavedLightProgram>,
        currentMinute: Int
    ): String {
        if (runningProgram != null) {
            val draft = runningProgram.draft

            val endMinutes = LightProgramTimeMath.endMinutes(draft.end)
            val endLabel = LightProgramTimeMath.endLabel(draft.end)

            val programEvent = when {
                currentMinute < draft.peakStart.totalMinutes -> {
                    "${draft.peakStart.label} Peak"
                }

                currentMinute < draft.peakEnd.totalMinutes -> {
                    "${draft.peakEnd.label} Peak End"
                }

                currentMinute < endMinutes -> {
                    "$endLabel End"
                } else -> {
                    null
                }
            }

            val nextProgram = todayPrograms.firstOrNull {
                program ->
                program.draft.start.totalMinutes > endMinutes
            }

            return when {
                programEvent != null && nextProgram != null -> {
                    "$programEvent · ${nextProgram.draft.start.label} ${nextProgram.name}"
                }

                programEvent != null -> {
                    programEvent
                }

                nextProgram != null -> {
                    "${nextProgram.draft.start.label} ${nextProgram.name}"
                } else -> {
                    "No upcoming event"
                }
            }
        }

        if (nextProgramToday != null) {
            return "${nextProgramToday.draft.start.label} ${nextProgramToday.name}"
        }

        return "Tomorrow"
    }

    private fun isProgramRunningAt(
        program: SavedLightProgram,
        minute: Int
    ): Boolean {
        val draft = program.draft

        val startMinutes = draft.start.totalMinutes
        val endMinutes = LightProgramTimeMath.endMinutes(draft.end)

        return minute >= startMinutes && minute < endMinutes
    }

    private fun calculateCurrentOutputPercent(
        program: SavedLightProgram,
        currentMinute: Int
    ): Int {
        val draft = program.draft

        if (!isProgramRunningAt(program, currentMinute)) {
            return 0
        }

        val peakPercent = program.maxOutputPercent()

        if (peakPercent <= 0) {
            return 0
        }

        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = draft.start.totalMinutes,
            peakStartMinute = draft.peakStart.totalMinutes,
            peakEndMinute = draft.peakEnd.totalMinutes,
            endMinute = LightProgramTimeMath.endMinutes(draft.end),
            peakPercent = peakPercent,
            transitionMode = draft.transitionMode
        ).sortedBy {
            point ->
            point.x
        }

        if (points.isEmpty()) {
            return 0
        }

        val current = currentMinute.toDouble()

        val previous = points.lastOrNull {
            point ->
            point.x <= current
        }

        val next = points.firstOrNull {
            point ->
            point.x >= current
        }

        val output = when {
            previous == null -> points.first().y

            next == null -> points.last().y

            previous.x == next.x -> previous.y

            else -> {
                val progress =
                (current - previous.x) / (next.x - previous.x)

                previous.y + ((next.y - previous.y) * progress)
            }
        }

        return output
        .toInt()
        .coerceIn(0, 100)
    }

    private fun SavedLightProgram.maxOutputPercent(): Int {
        return maxOf(
            draft.channelValues.red,
            draft.channelValues.green,
            draft.channelValues.blue,
            draft.channelValues.white
        ).coerceIn(0, 100)
    }

    private fun isScheduledToday(
        program: SavedLightProgram
    ): Boolean {
        val selectedDays = program.draft.selectedDays

        if (selectedDays.isEmpty()) {
            return true
        }

        return selectedDays.contains(todayAppDay())
    }

    private fun todayAppDay(): Int {
        val dayOfWeek = Calendar.getInstance()
        .get(Calendar.DAY_OF_WEEK)

        return if (dayOfWeek == Calendar.SUNDAY) {
            7
        } else {
            dayOfWeek - 1
        }
    }

    private fun currentLightPoint(
        millis: Long
    ): LightCurvePoint {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
        }

        return LightCurvePoint.of(
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE)
        )
    }

    override fun onCleared() {
        observeJob?.cancel()
        clockJob?.cancel()
        super.onCleared()
    }
}