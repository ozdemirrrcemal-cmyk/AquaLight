package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightChannelSemantic
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphSegment
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class DeviceLightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
    application.applicationContext

    private val lightProgramsDataStoreManager =
    LightProgramsDataStoreManager(appContext)

    private val lightRuntimeRepository =
    LightRuntimeRepository()

    private val _uiState = MutableStateFlow(
        createDeviceTimeUnavailableState(
            programs = emptyList(),
            liveState = LightDeviceLiveState.initial(0L)
        )
    )

    val uiState: StateFlow<DeviceLightDashboardUiState> =
    _uiState.asStateFlow()

    private var deviceId: Long = 0L
    private var observeJob: Job? = null

    private val liveRefreshOwnerKey =
    "DeviceLightViewModel_${System.identityHashCode(this)}"

    fun initialize(
        deviceId: Long
    ) {
        val previousDeviceId = this.deviceId

        if (
            previousDeviceId > 0L &&
            previousDeviceId != deviceId
        ) {
            LightDeviceLiveRefreshManager.stop(
                deviceId = previousDeviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        this.deviceId = deviceId

        observeJob?.cancel()

        LightDeviceLiveRefreshManager.start(
            context = appContext,
            deviceId = deviceId,
            ownerKey = liveRefreshOwnerKey
        )

        observeJob = viewModelScope.launch {
            combine(
                lightProgramsDataStoreManager.programsFlow,
                lightRuntimeRepository.observeManualRuntime(deviceId),
                LightDeviceLiveRefreshManager.observe(deviceId)
            ) {
                programs, manualRuntime, liveState ->
                Triple(
                    programs,
                    manualRuntime,
                    liveState
                )
            }.collect {
                (programs, manualRuntime, liveState) ->
                val activeProgramsForDevice = programs.filter {
                    program ->
                    program.deviceId == this@DeviceLightViewModel.deviceId &&
                    program.isActive
                }

                val baseState = createStateFromPrograms(
                    programs = activeProgramsForDevice,
                    liveState = liveState
                )

                val finalState = if (manualRuntime.isManualMode) {
                    createManualRuntimeState(
                        baseState = baseState,
                        manualRuntime = manualRuntime,
                        liveState = liveState
                    )
                } else {
                    baseState
                }

                _uiState.value = finalState
            }
        }
    }

    fun refreshNow() {
        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )
    }

    private fun createManualRuntimeState(
        baseState: DeviceLightDashboardUiState,
        manualRuntime: LightManualRuntimeState,
        liveState: LightDeviceLiveState
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

        val outputPercent = if (liveState.hasLiveChannels) {
            liveState.actualOutputPercent
        } else if (manualRuntime.isPowerOn) {
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
            currentWattText = liveState.actualPowerText,
            outputPercentText = "$outputPercent%",
            nextEventText = "Auto paused",
            timelineStatusText = "Paused · Today plan below",
            todayPlanGraphState = pausedGraphState
        ).withLiveIndicators(
            liveState = liveState,
            modeText = if (manualRuntime.isManualScene) {
                "SCENE"
            } else {
                "MANUAL"
            }
        )
    }

    private fun createStateFromPrograms(
        programs: List<SavedLightProgram>,
        liveState: LightDeviceLiveState
    ): DeviceLightDashboardUiState {
        val deviceTime = liveState.deviceTime

        if (deviceTime == null) {
            return createDeviceTimeUnavailableState(
                programs = programs,
                liveState = liveState
            )
        }

        val currentTime = deviceTime.curvePoint
        val currentMinute = currentTime.totalMinutes

        if (programs.isEmpty()) {
            return createEmptyState(
                currentTime = currentTime,
                liveState = liveState
            )
        }

        val todayPrograms = programs
        .filter {
            program ->
            isScheduledToday(
                program = program,
                deviceTime = deviceTime
            )
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
                currentWattText = liveState.actualPowerText,
                outputPercentText = "${liveState.actualOutputPercent}%",
                deviceTimeText = liveState.deviceTimeText,
                nextEventText = "Next scheduled day",
                timelineStatusText = "No active plan today",
                todayPlanGraphState = TodayLightPlanGraphState.empty(
                    currentTime = currentTime
                )
            ).withLiveIndicators(
                liveState = liveState,
                modeText = if (liveState.actualOutputPercent > 0) {
                    "AUTO"
                } else {
                    "IDLE"
                }
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

        val expectedOutput = runningProgram?.let {
            program ->
            calculateCurrentOutputPercent(
                program = program,
                currentMinute = currentMinute
            )
        } ?: 0

        val outputPercent = if (liveState.hasLiveChannels) {
            liveState.actualOutputPercent
        } else {
            expectedOutput
        }

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
            currentWattText = liveState.actualPowerText,
            outputPercentText = "$outputPercent%",
            deviceTimeText = liveState.deviceTimeText,
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
        ).withLiveIndicators(
            liveState = liveState,
            modeText = buildAutoModeText(
                liveState = liveState,
                runningProgram = runningProgram,
                nextProgramToday = nextProgramToday
            )
        )
    }

    private fun createDeviceTimeUnavailableState(
        programs: List<SavedLightProgram>,
        liveState: LightDeviceLiveState
    ): DeviceLightDashboardUiState {
        val hasPrograms = programs.isNotEmpty()

        return DeviceLightDashboardUiState(
            activeProgramName = if (hasPrograms) {
                "Device time unavailable"
            } else {
                "No active program"
            },
            runStatus = if (hasPrograms) {
                "Reading ESP32 time"
            } else {
                "Create or load a light program"
            },
            onlineStatusText = "UNKNOWN",
            currentWattText = liveState.actualPowerText,
            outputPercentText = "${liveState.actualOutputPercent}%",
            deviceTimeText = "--:--",
            nextEventText = if (hasPrograms) {
                "Waiting for ESP32 time"
            } else {
                "No upcoming event"
            },
            timelineStatusText = if (hasPrograms) {
                "Device time unavailable"
            } else {
                "No active plan"
            },
            todayPlanGraphState = TodayLightPlanGraphState.empty(
                currentTime = LightCurvePoint.of(0, 0)
            )
        ).withLiveIndicators(
            liveState = liveState,
            modeText = "SYNC"
        )
    }

    private fun createEmptyState(
        currentTime: LightCurvePoint,
        liveState: LightDeviceLiveState
    ): DeviceLightDashboardUiState {
        return DeviceLightDashboardUiState(
            activeProgramName = "No active program",
            runStatus = "Create or load a light program",
            onlineStatusText = "ONLINE",
            currentWattText = liveState.actualPowerText,
            outputPercentText = "${liveState.actualOutputPercent}%",
            deviceTimeText = liveState.deviceTimeText,
            nextEventText = "No upcoming event",
            timelineStatusText = "No active plan",
            todayPlanGraphState = TodayLightPlanGraphState.empty(
                currentTime = currentTime
            )
        ).withLiveIndicators(
            liveState = liveState,
            modeText = if (liveState.actualOutputPercent > 0) {
                "AUTO"
            } else {
                "IDLE"
            }
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
        program: SavedLightProgram,
        deviceTime: LightDeviceTimeState
    ): Boolean {
        val selectedDays = program.draft.selectedDays

        if (selectedDays.isEmpty()) {
            return true
        }

        return selectedDays.contains(
            appDayFromDeviceWeekDay(deviceTime.weekDay)
        )
    }

    private fun appDayFromDeviceWeekDay(
        weekDay: Int
    ): Int {
        return when (weekDay) {
            in 1..7 -> weekDay
            else -> todayAppDay()
        }
    }

    private fun DeviceLightDashboardUiState.withLiveIndicators(
        liveState: LightDeviceLiveState,
        modeText: String
    ): DeviceLightDashboardUiState {
        return copy(
            liveModeText = modeText,

            redChannelText = buildChannelText(
                prefix = "R",
                liveState = liveState,
                semantic = LightChannelSemantic.RED
            ),
            greenChannelText = buildChannelText(
                prefix = "G",
                liveState = liveState,
                semantic = LightChannelSemantic.GREEN
            ),
            blueChannelText = buildChannelText(
                prefix = "B",
                liveState = liveState,
                semantic = LightChannelSemantic.BLUE
            ),
            whiteChannelText = buildChannelText(
                prefix = "W",
                liveState = liveState,
                semantic = LightChannelSemantic.WHITE
            ),

            healthTemperatureText =
            liveState.thermalProtection.currentTemperatureText,

            healthTemperatureStatusText =
            normalizeHealthStatus(
                liveState.thermalProtection.statusText
            ),

            healthFanText =
            liveState.cooling.statusText,

            healthFanStatusText =
            liveState.cooling.fansText
        )
    }

    private fun buildChannelText(
        prefix: String,
        liveState: LightDeviceLiveState,
        semantic: LightChannelSemantic
    ): String {
        val value = liveState.channelFor(
            semantic = semantic
        )?.valuePercent

        return if (value == null) {
            "$prefix --"
        } else {
            "$prefix $value%"
        }
    }

    private fun normalizeHealthStatus(
        value: String
    ): String {
        val cleanValue = value.trim()

        return when {
            cleanValue.equals(
                "ACTIVE",
                ignoreCase = true
            ) -> {
                "Active"
            }

            cleanValue.equals(
                "REDUCING",
                ignoreCase = true
            ) -> {
                "Reducing"
            }

            cleanValue.equals(
                "SYNC",
                ignoreCase = true
            ) || cleanValue.equals(
                "SYNCING",
                ignoreCase = true
            ) -> {
                "Syncing"
            }

            cleanValue.isBlank() -> {
                "Syncing"
            } else -> {
                cleanValue
                .lowercase(Locale.getDefault())
                .replaceFirstChar {
                    char ->
                    char.uppercase(Locale.getDefault())
                }
            }
        }
    }

    private fun buildAutoModeText(
        liveState: LightDeviceLiveState,
        runningProgram: SavedLightProgram?,
        nextProgramToday: SavedLightProgram?
    ): String {
        return when {
            runningProgram != null -> "AUTO"
            liveState.actualOutputPercent > 0 -> "AUTO"
            nextProgramToday != null -> "WAIT"
            else -> "IDLE"
        }
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

    override fun onCleared() {
        observeJob?.cancel()

        if (deviceId > 0L) {
            LightDeviceLiveRefreshManager.stop(
                deviceId = deviceId,
                ownerKey = liveRefreshOwnerKey
            )
        }

        super.onCleared()
    }
}