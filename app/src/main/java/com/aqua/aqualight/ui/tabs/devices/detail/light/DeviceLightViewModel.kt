package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
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

class DeviceLightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val lightProgramsDataStoreManager =
        LightProgramsDataStoreManager(application.applicationContext)

    private val clockMillisFlow =
        MutableStateFlow(System.currentTimeMillis())

    private val _uiState = MutableStateFlow(
        createEmptyState(System.currentTimeMillis())
    )

    val uiState: StateFlow<DeviceLightDashboardUiState> =
        _uiState.asStateFlow()

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
                clockMillisFlow
            ) { programs, nowMillis ->
                programs to nowMillis
            }.collect { (programs, nowMillis) ->
                val activeProgram = programs
                    .filter { program ->
                        program.deviceId == this@DeviceLightViewModel.deviceId &&
                            program.isActive
                    }
                    .maxByOrNull { program ->
                        program.updatedAt
                    }

                if (activeProgram == null) {
                    _uiState.update {
                        createEmptyState(nowMillis)
                    }
                } else {
                    _uiState.update {
                        createStateFromActiveProgram(
                            program = activeProgram,
                            nowMillis = nowMillis
                        )
                    }
                }
            }
        }
    }

    fun refreshNow() {
        clockMillisFlow.value = System.currentTimeMillis()
    }

    private fun startClock() {
        clockMillisFlow.value = System.currentTimeMillis()

        clockJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                clockMillisFlow.value = System.currentTimeMillis()
            }
        }
    }

    private fun createStateFromActiveProgram(
        program: SavedLightProgram,
        nowMillis: Long
    ): DeviceLightDashboardUiState {
        val currentTime = currentLightPoint(nowMillis)
        val currentMinute = currentTime.totalMinutes
        val draft = program.draft

        val scheduledToday = isScheduledToday(program)
        val outputPercent = if (scheduledToday) {
            calculateCurrentOutputPercent(
                program = program,
                currentMinute = currentMinute
            )
        } else {
            0
        }

        val graphState = LightCurveGraphState(
            start = draft.start,
            peakStart = draft.peakStart,
            peakEnd = draft.peakEnd,
            end = draft.end,
            channelValues = if (scheduledToday) {
                draft.channelValues
            } else {
                LightCurveChannelValues(
                    red = 0,
                    green = 0,
                    blue = 0,
                    white = 0
                )
            },
            currentTime = currentTime,
            transitionMode = draft.transitionMode
        )

        return DeviceLightDashboardUiState(
            activeProgramName = program.name,
            runStatus = buildRunStatus(
                program = program,
                currentMinute = currentMinute,
                scheduledToday = scheduledToday
            ),
            onlineStatusText = "ONLINE",
            currentWattText = "-- W",
            outputPercentText = "$outputPercent%",
            deviceTimeText = currentTime.label,
            nextEventText = buildNextEventText(
                program = program,
                currentMinute = currentMinute,
                scheduledToday = scheduledToday
            ),
            timelineStatusText = if (scheduledToday) {
                "Active program"
            } else {
                "Not scheduled today"
            },
            graphState = graphState
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
            timelineStatusText = "No active program",
            graphState = LightCurveGraphState(
                start = LightCurvePoint.of(8, 0),
                peakStart = LightCurvePoint.of(10, 0),
                peakEnd = LightCurvePoint.of(16, 0),
                end = LightCurvePoint.of(18, 0),
                channelValues = LightCurveChannelValues(
                    red = 0,
                    green = 0,
                    blue = 0,
                    white = 0
                ),
                currentTime = currentTime,
                transitionMode = LightCurveTransitionMode.LINEAR
            )
        )
    }

    private fun calculateCurrentOutputPercent(
        program: SavedLightProgram,
        currentMinute: Int
    ): Int {
        val draft = program.draft

        if (
            currentMinute < draft.start.totalMinutes ||
            currentMinute > draft.end.totalMinutes
        ) {
            return 0
        }

        val maxChannelPercent = maxOf(
            draft.channelValues.red,
            draft.channelValues.green,
            draft.channelValues.blue,
            draft.channelValues.white
        ).coerceIn(0, 100)

        if (maxChannelPercent <= 0) {
            return 0
        }

        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = draft.start.totalMinutes,
            peakStartMinute = draft.peakStart.totalMinutes,
            peakEndMinute = draft.peakEnd.totalMinutes,
            endMinute = draft.end.totalMinutes,
            peakPercent = maxChannelPercent,
            transitionMode = draft.transitionMode
        ).sortedBy { point ->
            point.x
        }

        if (points.isEmpty()) {
            return 0
        }

        val current = currentMinute.toDouble()

        val previous = points.lastOrNull { point ->
            point.x <= current
        }

        val next = points.firstOrNull { point ->
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

    private fun buildRunStatus(
        program: SavedLightProgram,
        currentMinute: Int,
        scheduledToday: Boolean
    ): String {
        if (!scheduledToday) {
            return "Program is not scheduled today"
        }

        val draft = program.draft

        return when {
            currentMinute < draft.start.totalMinutes -> {
                "Waiting for start"
            }

            currentMinute in draft.start.totalMinutes..draft.end.totalMinutes -> {
                "Auto program running"
            }

            else -> {
                "Program completed for today"
            }
        }
    }

    private fun buildNextEventText(
        program: SavedLightProgram,
        currentMinute: Int,
        scheduledToday: Boolean
    ): String {
        if (!scheduledToday) {
            return "Next scheduled day"
        }

        val draft = program.draft

        return when {
            currentMinute < draft.start.totalMinutes -> {
                "${draft.start.label} Start"
            }

            currentMinute < draft.peakStart.totalMinutes -> {
                "${draft.peakStart.label} Peak"
            }

            currentMinute < draft.peakEnd.totalMinutes -> {
                "${draft.peakEnd.label} Peak End"
            }

            currentMinute < draft.end.totalMinutes -> {
                "${draft.end.label} Sunset"
            }

            else -> {
                "Tomorrow ${draft.start.label}"
            }
        }
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
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

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