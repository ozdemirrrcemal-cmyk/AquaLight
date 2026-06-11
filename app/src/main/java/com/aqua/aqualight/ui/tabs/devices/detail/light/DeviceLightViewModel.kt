package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightActualDataPolicy
import com.aqua.aqualight.data.devices.light.runtime.LightChannelSemantic
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveRefreshManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceTimeState
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeState
import com.aqua.aqualight.data.devices.light.runtime.LightRuntimeRepository
import com.aqua.aqualight.data.devices.presence.DeviceConnectionStatus
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.data.devices.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.curve.model.TodayLightPlanGraphSegment
import com.aqua.aqualight.data.devices.light.curve.model.TodayLightPlanGraphSegmentType
import com.aqua.aqualight.data.devices.light.curve.model.TodayLightPlanGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.LightDashboardMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.timeline.LightProgramPhaseType
import com.aqua.aqualight.data.devices.light.programs.timeline.LightProgramTimelineBuilder
import com.aqua.aqualight.data.devices.light.programs.timeline.LightProgramTimelinePhase
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DeviceLightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
    application.applicationContext

    private val lightProgramsDataStoreManager =
    LightProgramsDataStoreManager(appContext)

    private val lightRuntimeRepository =
    LightRuntimeRepository(appContext)

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

        if (deviceId <= 0L) {
            _uiState.value = createDeviceTimeUnavailableState(
                programs = emptyList(),
                liveState = LightDeviceLiveState.initial(0L)
            )
            return
        }

        DevicePresenceMonitor.start(appContext)

        LightDeviceLiveRefreshManager.start(
            context = appContext,
            deviceId = deviceId,
            ownerKey = liveRefreshOwnerKey
        )

        observeJob = viewModelScope.launch {
            combine(
                lightProgramsDataStoreManager.programsFlow,
                lightRuntimeRepository.observeManualRuntime(deviceId),
                LightDeviceLiveRefreshManager.observe(deviceId),
                DevicePresenceMonitor.statuses
            ) {
                programs, manualRuntime, liveState, statuses ->
                DashboardInputs(
                    programs = programs,
                    manualRuntime = manualRuntime,
                    liveState = liveState,
                    presenceState = statuses[this@DeviceLightViewModel.deviceId]
                )
            }.collect { inputs ->
                val activeProgramsForDevice = inputs.programs.filter {
                    program ->
                    program.deviceId == this@DeviceLightViewModel.deviceId &&
                    program.isActive
                }

                val baseState = createStateFromPrograms(
                    programs = activeProgramsForDevice,
                    liveState = inputs.liveState
                )

                val finalState = if (
                    inputs.manualRuntime.isManualMode ||
                    inputs.manualRuntime.isManualScene
                ) {
                    createManualRuntimeState(
                        baseState = baseState,
                        manualRuntime = inputs.manualRuntime,
                        liveState = inputs.liveState
                    )
                } else {
                    baseState
                }

                _uiState.value = finalState.withConnectionState(
                    presenceState = inputs.presenceState,
                    liveState = inputs.liveState
                )
            }
        }
    }

    fun refreshNow() {
        if (deviceId <= 0L) {
            return
        }

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

        val outputPercent = LightActualDataPolicy.actualOutputPercent(liveState)

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
            outputPercentText = if (LightActualDataPolicy.hasActualData(liveState)) {
                "$outputPercent%"
            } else {
                "--%"
            },
            nextEventText = "Auto paused",
            timelineStatusText = "Paused · Today plan below",
            todayPlanGraphState = pausedGraphState
        ).withLiveIndicators(
            liveState = liveState,
            mode = if (manualRuntime.isManualScene) {
                LightDashboardMode.SCENE
            } else {
                LightDashboardMode.MANUAL
            }
        )
    }

    private fun createStateFromPrograms(
        programs: List<SavedLightProgram>,
        liveState: LightDeviceLiveState
    ): DeviceLightDashboardUiState {
        val deviceTime = liveState.deviceTime

        if (!liveState.hasDeviceTime || deviceTime == null) {
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
            val graphSegments = buildTodayGraphSegments(
                programs = programs,
                deviceTime = deviceTime,
                currentMinute = currentMinute,
                runningProgram = null,
                nextProgramToday = null
            )

            val currentMoonlightSegment = graphSegments.firstOrNull {
                segment ->
                segment.type == TodayLightPlanGraphSegmentType.MOONLIGHT &&
                currentMinute >= segment.startMinute &&
                currentMinute < segment.endMinute
            }

            if (currentMoonlightSegment != null) {
                val outputPercent = LightActualDataPolicy.actualOutputPercent(liveState)

                return DeviceLightDashboardUiState(
                    activeProgramName = "Moonlight",
                    runStatus = if (liveState.hasLiveChannels) {
                        "Moonlight active"
                    } else {
                        "Moonlight scheduled · waiting for live data"
                    },
                    currentWattText = liveState.actualPowerText,
                    outputPercentText = if (LightActualDataPolicy.hasActualData(liveState)) {
                "$outputPercent%"
            } else {
                "--%"
            },
                    deviceTimeText = liveState.deviceTimeText,
                    nextEventText = "Moonlight until ${labelForMinute(currentMoonlightSegment.endMinute)}",
                    timelineStatusText = "Moonlight phase active",
                    todayPlanGraphState = TodayLightPlanGraphState(
                        currentTime = currentTime,
                        segments = graphSegments
                    )
                ).withLiveIndicators(
                    liveState = liveState,
                    mode = LightDashboardMode.MOON
                )
            }

            return DeviceLightDashboardUiState(
                activeProgramName = "No program today",
                runStatus = "Active schedules are not planned for today",
                currentWattText = liveState.actualPowerText,
                outputPercentText = LightActualDataPolicy.actualOutputText(liveState),
                deviceTimeText = liveState.deviceTimeText,
                nextEventText = "Next scheduled day",
                timelineStatusText = "No active plan today",
                todayPlanGraphState = TodayLightPlanGraphState.empty(
                    currentTime = currentTime
                )
            ).withLiveIndicators(
                liveState = liveState,
                mode = if (liveState.actualOutputPercent > 0) {
                    LightDashboardMode.AUTO
                } else {
                    LightDashboardMode.IDLE
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

        val graphSegments = buildTodayGraphSegments(
            programs = programs,
            deviceTime = deviceTime,
            currentMinute = currentMinute,
            runningProgram = runningProgram,
            nextProgramToday = nextProgramToday
        )

        val currentTimelineSegment = graphSegments.firstOrNull {
            segment ->
            currentMinute >= segment.startMinute &&
            currentMinute < segment.endMinute
        }

        val isMoonlightActive =
        currentTimelineSegment?.type == TodayLightPlanGraphSegmentType.MOONLIGHT

        val outputPercent = LightActualDataPolicy.actualOutputPercent(liveState)

        return DeviceLightDashboardUiState(
            activeProgramName = if (isMoonlightActive) {
                "Moonlight"
            } else {
                displayProgram?.name ?: "No active program"
            },
            runStatus = when {
                isMoonlightActive && liveState.hasLiveChannels -> {
                    "Moonlight active"
                }

                isMoonlightActive -> {
                    "Moonlight scheduled · waiting for live data"
                }

                runningProgram != null && !liveState.hasLiveChannels -> {
                    "Program scheduled · waiting for live data"
                }

                else -> {
                    buildRunStatus(
                        runningProgram = runningProgram,
                        nextProgramToday = nextProgramToday
                    )
                }
            },
            currentWattText = liveState.actualPowerText,
            outputPercentText = if (LightActualDataPolicy.hasActualData(liveState)) {
                "$outputPercent%"
            } else {
                "--%"
            },
            deviceTimeText = liveState.deviceTimeText,
            nextEventText = if (
                isMoonlightActive &&
                currentTimelineSegment != null
            ) {
                "Moonlight until ${labelForMinute(currentTimelineSegment.endMinute)}"
            } else {
                buildNextEventText(
                    runningProgram = runningProgram,
                    nextProgramToday = nextProgramToday,
                    todayPrograms = todayPrograms,
                    currentMinute = currentMinute
                )
            },
            timelineStatusText = when {
                isMoonlightActive -> {
                    "Moonlight phase active"
                }

                todayPrograms.size == 1 -> {
                    "Today active plan"
                } else -> {
                    "Today active plan · ${todayPrograms.size} programs"
                }
            },
            todayPlanGraphState = TodayLightPlanGraphState(
                currentTime = currentTime,
                segments = graphSegments
            )
        ).withLiveIndicators(
            liveState = liveState,
            mode = if (isMoonlightActive) {
                LightDashboardMode.MOON
            } else {
                buildAutoMode(
                    liveState = liveState,
                    runningProgram = runningProgram,
                    nextProgramToday = nextProgramToday
                )
            }
        )
    }

    private fun DeviceLightDashboardUiState.withConnectionState(
        presenceState: DeviceStatusState?,
        liveState: LightDeviceLiveState
    ): DeviceLightDashboardUiState {
        val status = presenceState?.status ?: DeviceConnectionStatus.UNKNOWN
        val isOnline = presenceState?.isOnline == true
        val statusText = connectionStatusTextFor(status)

        if (isOnline) {
            return copy(
                isDeviceOnline = true,
                controlsEnabled = true,
                connectionStatusText = statusText
            )
        }

        return copy(
            activeProgramName = "Device offline",
            runStatus = statusText,
            liveMode = LightDashboardMode.IDLE,
            currentWattText = "-- W",
            outputPercentText = "0%",
            redChannelText = "R --",
            greenChannelText = "G --",
            blueChannelText = "B --",
            whiteChannelText = "W --",
            deviceTimeText = if (liveState.hasDeviceTime) {
                liveState.deviceTimeText
            } else {
                "--:--"
            },
            nextEventText = "Reconnect the controller to continue",
            healthTemperatureText = "-- °C",
            healthTemperatureStatusText = "Unavailable",
            healthFanText = "Unavailable",
            healthFanStatusText = "Unavailable",
            timelineStatusText = "Live connection unavailable",
            isDeviceOnline = false,
            controlsEnabled = false,
            connectionStatusText = statusText
        )
    }

    private fun connectionStatusTextFor(
        status: DeviceConnectionStatus
    ): String {
        return when (status) {
            DeviceConnectionStatus.ONLINE -> "Online"
            DeviceConnectionStatus.CHECKING -> "Checking device connection"
            DeviceConnectionStatus.STALE -> "Connection is unstable · controls disabled"
            DeviceConnectionStatus.OFFLINE -> "Device offline · controls disabled"
            DeviceConnectionStatus.UNKNOWN -> "Waiting for device connection"
        }
    }

    private fun buildTodayGraphSegments(
        programs: List<SavedLightProgram>,
        deviceTime: LightDeviceTimeState,
        currentMinute: Int,
        runningProgram: SavedLightProgram?,
        nextProgramToday: SavedLightProgram?
    ): List<TodayLightPlanGraphSegment> {
        val todayAppDay = appDayFromDeviceWeekDay(
            weekDay = deviceTime.weekDay
        )

        val previousAppDay = if (todayAppDay == 1) {
            7
        } else {
            todayAppDay - 1
        }

        val result = mutableListOf<TodayLightPlanGraphSegment>()

        programs.forEach {
            program ->
            val timeline = LightProgramTimelineBuilder.build(
                draft = program.draft
            )

            val scheduledToday = isScheduledOnAppDay(
                program = program,
                appDay = todayAppDay
            )

            val scheduledYesterday = isScheduledOnAppDay(
                program = program,
                appDay = previousAppDay
            )

            timeline.phases.forEach { phase ->
                if (phase.type == LightProgramPhaseType.MAIN_CURVE && scheduledToday) {
                    result += buildMainProgramGraphSegment(
                        program = program,
                        phase = phase,
                        runningProgram = runningProgram,
                        nextProgramToday = nextProgramToday
                    )
                }
            }
        }

        return result
        .filter {
            segment ->
            segment.endMinute > segment.startMinute
        }
        .sortedWith(
            compareBy<TodayLightPlanGraphSegment> {
                it.startMinute
            }.thenBy {
                it.type.ordinal
            }
        )
    }

    private fun buildMainProgramGraphSegment(
        program: SavedLightProgram,
        phase: LightProgramTimelinePhase,
        runningProgram: SavedLightProgram?,
        nextProgramToday: SavedLightProgram?
    ): TodayLightPlanGraphSegment {
        val isCurrent = runningProgram?.id == program.id

        val isNext =
        runningProgram == null &&
        nextProgramToday?.id == program.id

        return TodayLightPlanGraphSegment(
            id = "${program.id}_main",
            name = program.name,
            start = pointFromMinuteForGraph(phase.startMinute),
            peakStart = pointFromMinuteForGraph(
                phase.peakStartMinute ?: phase.startMinute
            ),
            peakEnd = pointFromMinuteForGraph(
                phase.peakEndMinute ?: phase.endMinute
            ),
            end = pointFromMinuteForGraph(phase.endMinute),
            outputPercent = phase.outputPercent,
            transitionMode = phase.transitionMode,
            isCurrent = isCurrent,
            isNext = isNext,
            type = TodayLightPlanGraphSegmentType.MAIN_PROGRAM,
            startMinute = phase.startMinute.coerceIn(0, MINUTES_PER_DAY),
            peakStartMinute = (phase.peakStartMinute ?: phase.startMinute)
            .coerceIn(0, MINUTES_PER_DAY),
            peakEndMinute = (phase.peakEndMinute ?: phase.endMinute)
            .coerceIn(0, MINUTES_PER_DAY),
            endMinute = phase.endMinute.coerceIn(0, MINUTES_PER_DAY)
        )
    }

    private fun isScheduledOnAppDay(
        program: SavedLightProgram,
        appDay: Int
    ): Boolean {
        val selectedDays = program.draft.selectedDays

        if (selectedDays.isEmpty()) {
            return true
        }

        return selectedDays.contains(appDay)
    }

    private fun pointFromMinuteForGraph(
        minute: Int
    ): LightCurvePoint {
        val safeMinute = minute.coerceIn(
            0,
            MINUTES_PER_DAY
        )

        if (safeMinute >= MINUTES_PER_DAY) {
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

    private fun labelForMinute(
        minute: Int
    ): String {
        if (minute == MINUTES_PER_DAY) {
            return "24:00"
        }

        val normalizedMinute =
        ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

        val hour = normalizedMinute / 60
        val minutePart = normalizedMinute % 60

        return "%02d:%02d".format(
            hour,
            minutePart
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
            currentWattText = liveState.actualPowerText,
            outputPercentText = LightActualDataPolicy.actualOutputText(liveState),
            deviceTimeText = "--:--",
            nextEventText = if (hasPrograms) {
                "Waiting for ESP32 time"
            } else {
                "No event"
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
            mode = LightDashboardMode.SYNC
        )
    }

    private fun createEmptyState(
        currentTime: LightCurvePoint,
        liveState: LightDeviceLiveState
    ): DeviceLightDashboardUiState {
        return DeviceLightDashboardUiState(
            activeProgramName = "No active program",
            runStatus = "Create or load a light program",
            currentWattText = liveState.actualPowerText,
            outputPercentText = LightActualDataPolicy.actualOutputText(liveState),
            deviceTimeText = liveState.deviceTimeText,
            nextEventText = "No event",
            timelineStatusText = "No active plan",
            todayPlanGraphState = TodayLightPlanGraphState.empty(
                currentTime = currentTime
            )
        ).withLiveIndicators(
            liveState = liveState,
            mode = if (liveState.actualOutputPercent > 0) {
                LightDashboardMode.AUTO
            } else {
                LightDashboardMode.IDLE
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
                    "No event"
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
        mode: LightDashboardMode
    ): DeviceLightDashboardUiState {
        return copy(
            liveMode = mode,

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

    private fun buildAutoMode(
        liveState: LightDeviceLiveState,
        runningProgram: SavedLightProgram?,
        nextProgramToday: SavedLightProgram?
    ): LightDashboardMode {
        return when {
            runningProgram != null -> {
                LightDashboardMode.AUTO
            }

            liveState.actualOutputPercent > 0 -> {
                LightDashboardMode.AUTO
            }

            nextProgramToday != null -> {
                LightDashboardMode.WAIT
            } else -> {
                LightDashboardMode.IDLE
            }
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

    private data class DashboardInputs(
        val programs: List<SavedLightProgram>,
        val manualRuntime: LightManualRuntimeState,
        val liveState: LightDeviceLiveState,
        val presenceState: DeviceStatusState?
    )

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

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
    }
}