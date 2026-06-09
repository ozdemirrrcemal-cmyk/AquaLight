package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.graphics.Color
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.light.runtime.LightChannelSemantic
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.MoonlightChannel
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import java.util.Calendar
import kotlin.math.roundToInt

class TankAssignedDeviceUiMapper {

    fun isLightDeviceForObserver(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): Boolean {
        return device.isLightDevice()
    }

    fun map(
        device: DevicesDataStoreManager.DeviceInfoUi,
        statuses: Map<Long, DeviceStatusState>,
        programs: List<SavedLightProgram>,
        lightState: LightDeviceLiveState?,
        now: Long,
        modeOverride: TankLightModeOverride? = null
    ): TankAssignedDeviceUi {
        val title =
            getDeviceTitle(
                device = device
            )

        val subtitle =
            getDeviceTypeText(
                device = device
            )

        val online =
            isDeviceOnline(
                device = device,
                statuses = statuses,
                now = now
            )

        val iconRes =
            DeviceIconMapper.iconFor(
                device.deviceType
            )

        return if (device.isLightDevice()) {
            mapLightDevice(
                device = device,
                title = title,
                subtitle = subtitle,
                iconRes = iconRes,
                online = online,
                programs = programs,
                lightState = lightState,
                modeOverride = modeOverride
            )
        } else {
            TankAssignedDeviceUi.Generic(
                deviceId = device.id,
                title = title,
                subtitle = subtitle,
                iconRes = iconRes,
                isOnline = online
            )
        }
    }

    private fun mapLightDevice(
        device: DevicesDataStoreManager.DeviceInfoUi,
        title: String,
        subtitle: String,
        iconRes: Int,
        online: Boolean,
        programs: List<SavedLightProgram>,
        lightState: LightDeviceLiveState?,
        modeOverride: TankLightModeOverride?
    ): TankAssignedDeviceUi.Light {
        val liveState =
            lightState ?: LightDeviceLiveState.initial(
                deviceId = device.id
            )

        val activePrograms =
            programs
                .filter { program ->
                    program.deviceId == device.id && program.isActive
                }
                .sortedBy { program ->
                    program.draft.start.totalMinutes
                }

        val deviceTime =
            liveState.deviceTime

        val currentMinute =
            deviceTime?.curvePoint?.totalMinutes ?: currentPhoneMinute()

        val todayPrograms =
            activePrograms
                .filter { program ->
                    if (deviceTime == null) {
                        true
                    } else {
                        isScheduledToday(
                            program = program,
                            weekDay = deviceTime.weekDay
                        )
                    }
                }
                .sortedBy { program ->
                    program.draft.start.totalMinutes
                }

        val runningProgram =
            todayPrograms.firstOrNull { program ->
                isProgramRunningAt(
                    program = program,
                    minute = currentMinute
                )
            }

        val nextProgram =
            todayPrograms.firstOrNull { program ->
                program.draft.start.totalMinutes > currentMinute
            }

        val displayProgram =
            runningProgram
                ?: nextProgram
                ?: todayPrograms.firstOrNull()
                ?: activePrograms.firstOrNull()

        val moonlightOverride =
            if (modeOverride == null) {
                buildMoonlightOverride(
                    programs = todayPrograms.ifEmpty {
                        activePrograms
                    },
                    currentMinute = currentMinute
                )
            } else {
                null
            }

        val effectiveModeOverride =
            modeOverride ?: moonlightOverride

        val outputPercent =
            when {
                effectiveModeOverride?.outputPercent != null -> {
                    effectiveModeOverride.outputPercent
                }

                liveState.hasLiveChannels -> {
                    liveState.actualOutputPercent
                }

                runningProgram != null -> {
                    calculateCurrentOutputPercent(
                        program = runningProgram,
                        currentMinute = currentMinute
                    )
                }

                else -> {
                    0
                }
            }

        val modeContent =
            buildModeContent(
                displayProgram = displayProgram,
                modeOverride = effectiveModeOverride
            )

        return TankAssignedDeviceUi.Light(
            deviceId = device.id,
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            isOnline = online,
            mode = modeContent.mode,
            modeLabel = modeContent.label,
            programName = modeContent.title,
            startTimeText = modeContent.leftText,
            endTimeText = modeContent.rightText,
            outputPercent = outputPercent.coerceIn(
                0,
                100
            ),
            timelineProgressPercent = modeContent.timelineProgressPercent
                ?: calculateTimelineProgressPercent(
                    program = displayProgram,
                    currentMinute = currentMinute
                ),
            accentColorInt = modeContent.accentColorInt,
            channels = buildLightChannels(
                device = device,
                liveState = liveState,
                runningProgram = runningProgram,
                displayProgram = displayProgram,
                currentMinute = currentMinute,
                modeOverride = effectiveModeOverride
            )
        )
    }

    private fun buildMoonlightOverride(
        programs: List<SavedLightProgram>,
        currentMinute: Int
    ): TankLightModeOverride? {
        return programs.firstNotNullOfOrNull { program ->
            buildMoonlightOverrideForProgram(
                program = program,
                currentMinute = currentMinute
            )
        }
    }

    private fun buildMoonlightOverrideForProgram(
        program: SavedLightProgram,
        currentMinute: Int
    ): TankLightModeOverride? {
        val settings =
            program.draft.moonlightSettings

        if (!settings.enabled) {
            return null
        }

        val startPoint =
            if (settings.followProgramEnd) {
                program.draft.end
            } else {
                settings.startTime
            }

        val endPoint =
            settings.endTime

        val startMinute =
            normalizeMinute(
                minute = startPoint.totalMinutes
            )

        val endMinute =
            normalizeMinute(
                minute = endPoint.totalMinutes
            )

        val safeCurrentMinute =
            normalizeMinute(
                minute = currentMinute
            )

        if (
            !isMinuteInRange(
                currentMinute = safeCurrentMinute,
                startMinute = startMinute,
                endMinute = endMinute
            )
        ) {
            return null
        }

        val intensity =
            settings.intensityPercent.coerceIn(
                0,
                100
            )

        val blue =
            when (settings.channel) {
                MoonlightChannel.BLUE,
                MoonlightChannel.BLUE_WHITE -> {
                    intensity
                }

                MoonlightChannel.WHITE -> {
                    0
                }
            }

        val white =
            when (settings.channel) {
                MoonlightChannel.WHITE,
                MoonlightChannel.BLUE_WHITE -> {
                    intensity
                }

                MoonlightChannel.BLUE -> {
                    0
                }
            }

        return TankLightModeOverride(
            mode = TankLightCardMode.MOONLIGHT,
            title = "Moonlight Mode",
            outputPercent = intensity,
            red = 0,
            green = 0,
            blue = blue,
            white = white,
            leftText = startPoint.label,
            rightText = endPoint.label,
            timelineProgressPercent = moonlightProgressPercent(
                currentMinute = safeCurrentMinute,
                startMinute = startMinute,
                endMinute = endMinute
            )
        )
    }

    private fun isMinuteInRange(
        currentMinute: Int,
        startMinute: Int,
        endMinute: Int
    ): Boolean {
        if (startMinute == endMinute) {
            return false
        }

        return if (startMinute < endMinute) {
            currentMinute >= startMinute && currentMinute < endMinute
        } else {
            currentMinute >= startMinute || currentMinute < endMinute
        }
    }

    private fun moonlightProgressPercent(
        currentMinute: Int,
        startMinute: Int,
        endMinute: Int
    ): Int {
        if (startMinute == endMinute) {
            return 0
        }

        val duration =
            if (endMinute > startMinute) {
                endMinute - startMinute
            } else {
                (MINUTES_PER_DAY - startMinute) + endMinute
            }

        if (duration <= 0) {
            return 0
        }

        val elapsed =
            if (currentMinute >= startMinute) {
                currentMinute - startMinute
            } else {
                (MINUTES_PER_DAY - startMinute) + currentMinute
            }

        return ((elapsed.toDouble() / duration.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun normalizeMinute(
        minute: Int
    ): Int {
        val value =
            minute % MINUTES_PER_DAY

        return if (value < 0) {
            value + MINUTES_PER_DAY
        } else {
            value
        }
    }

    private fun buildLightChannels(
        device: DevicesDataStoreManager.DeviceInfoUi,
        liveState: LightDeviceLiveState,
        runningProgram: SavedLightProgram?,
        displayProgram: SavedLightProgram?,
        currentMinute: Int,
        modeOverride: TankLightModeOverride?
    ): List<TankLightChannelUi> {
        return device.supportedLightChannels().map { channel ->
            val currentPercent =
                currentLightChannelPercent(
                    channel = channel,
                    liveState = liveState,
                    runningProgram = runningProgram,
                    currentMinute = currentMinute,
                    modeOverride = modeOverride
                )

            val targetPercent =
                targetLightChannelPercent(
                    channel = channel,
                    program = displayProgram
                )

            TankLightChannelUi(
                key = channel.key,
                label = channel.label,
                currentPercent = currentPercent,
                targetPercent = targetPercent,
                colorInt = channel.colorInt
            )
        }
    }

    private fun currentLightChannelPercent(
        channel: LightChannelConfig,
        liveState: LightDeviceLiveState,
        runningProgram: SavedLightProgram?,
        currentMinute: Int,
        modeOverride: TankLightModeOverride?
    ): Int {
        overrideChannelPercent(
            semantic = channel.semantic,
            modeOverride = modeOverride
        )?.let { percent ->
            return percent
        }

        if (liveState.hasLiveChannels) {
            return when (channel.semantic) {
                LightChannelSemantic.RED,
                LightChannelSemantic.GREEN,
                LightChannelSemantic.BLUE,
                LightChannelSemantic.WHITE -> {
                    liveState.channelFor(
                        semantic = channel.semantic
                    )?.valuePercent?.coerceIn(
                        0,
                        100
                    ) ?: 0
                }

                LightChannelSemantic.UNKNOWN -> {
                    liveState.actualOutputPercent.coerceIn(
                        0,
                        100
                    )
                }
            }
        }

        if (runningProgram == null) {
            return 0
        }

        return calculateChannelOutputPercent(
            program = runningProgram,
            currentMinute = currentMinute,
            semantic = channel.semantic
        )
    }

    private fun overrideChannelPercent(
        semantic: LightChannelSemantic,
        modeOverride: TankLightModeOverride?
    ): Int? {
        if (modeOverride == null) {
            return null
        }

        return when (semantic) {
            LightChannelSemantic.RED -> modeOverride.red
            LightChannelSemantic.GREEN -> modeOverride.green
            LightChannelSemantic.BLUE -> modeOverride.blue
            LightChannelSemantic.WHITE -> modeOverride.white
            LightChannelSemantic.UNKNOWN -> modeOverride.outputPercent
        }?.coerceIn(
            0,
            100
        )
    }

    private fun targetLightChannelPercent(
        channel: LightChannelConfig,
        program: SavedLightProgram?
    ): Int {
        val values =
            program?.draft?.channelValues ?: return 0

        return when (channel.semantic) {
            LightChannelSemantic.RED -> values.red
            LightChannelSemantic.GREEN -> values.green
            LightChannelSemantic.BLUE -> values.blue
            LightChannelSemantic.WHITE -> values.white
            LightChannelSemantic.UNKNOWN -> {
                maxOf(
                    values.red,
                    values.green,
                    values.blue,
                    values.white
                )
            }
        }.coerceIn(
            0,
            100
        )
    }

    private fun calculateChannelOutputPercent(
        program: SavedLightProgram,
        currentMinute: Int,
        semantic: LightChannelSemantic
    ): Int {
        if (
            !isProgramRunningAt(
                program = program,
                minute = currentMinute
            )
        ) {
            return 0
        }

        val peakPercent =
            targetLightChannelPercent(
                channel = LightChannelConfig(
                    key = TankLightChannelKey.INTENSITY,
                    label = "Intensity",
                    semantic = semantic,
                    colorInt = Color.parseColor("#8EB8FF")
                ),
                program = program
            )

        if (peakPercent <= 0) {
            return 0
        }

        return calculateCurvePercent(
            program = program,
            currentMinute = currentMinute,
            peakPercent = peakPercent
        )
    }

    private fun calculateCurrentOutputPercent(
        program: SavedLightProgram,
        currentMinute: Int
    ): Int {
        val peakPercent =
            maxOf(
                program.draft.channelValues.red,
                program.draft.channelValues.green,
                program.draft.channelValues.blue,
                program.draft.channelValues.white
            ).coerceIn(
                0,
                100
            )

        if (peakPercent <= 0) {
            return 0
        }

        return calculateCurvePercent(
            program = program,
            currentMinute = currentMinute,
            peakPercent = peakPercent
        )
    }

    private fun calculateCurvePercent(
        program: SavedLightProgram,
        currentMinute: Int,
        peakPercent: Int
    ): Int {
        if (
            !isProgramRunningAt(
                program = program,
                minute = currentMinute
            )
        ) {
            return 0
        }

        val points =
            LightCurveInterpolator.buildCurvePoints(
                startMinute = program.draft.start.totalMinutes,
                peakStartMinute = program.draft.peakStart.totalMinutes,
                peakEndMinute = program.draft.peakEnd.totalMinutes,
                endMinute = LightProgramTimeMath.endMinutes(
                    program.draft.end
                ),
                peakPercent = peakPercent,
                transitionMode = program.draft.transitionMode
            ).sortedBy { point ->
                point.x
            }

        if (points.isEmpty()) {
            return 0
        }

        val current =
            currentMinute.toDouble()

        val previous =
            points.lastOrNull { point ->
                point.x.toDouble() <= current
            }

        val next =
            points.firstOrNull { point ->
                point.x.toDouble() >= current
            }

        val value =
            when {
                previous == null -> {
                    points.first().y.toDouble()
                }

                next == null -> {
                    points.last().y.toDouble()
                }

                previous.x == next.x -> {
                    previous.y.toDouble()
                }

                else -> {
                    val previousX =
                        previous.x.toDouble()

                    val nextX =
                        next.x.toDouble()

                    val previousY =
                        previous.y.toDouble()

                    val nextY =
                        next.y.toDouble()

                    val progress =
                        (current - previousX) / (nextX - previousX)

                    previousY + ((nextY - previousY) * progress)
                }
            }

        return value
            .roundToInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun calculateTimelineProgressPercent(
        program: SavedLightProgram?,
        currentMinute: Int
    ): Int {
        if (program == null) {
            return 0
        }

        val start =
            program.draft.start.totalMinutes

        val end =
            LightProgramTimeMath.endMinutes(
                program.draft.end
            )

        if (end <= start) {
            return 0
        }

        return when {
            currentMinute <= start -> {
                0
            }

            currentMinute >= end -> {
                100
            }

            else -> {
                (((currentMinute - start).toDouble() / (end - start).toDouble()) * 100.0)
                    .roundToInt()
                    .coerceIn(
                        0,
                        100
                    )
            }
        }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.supportedLightChannels(): List<LightChannelConfig> {
        val rawText =
            lightSearchText()

        return when {
            rawText.contains("wrgb") -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.WHITE,
                        label = "White",
                        semantic = LightChannelSemantic.WHITE,
                        colorInt = Color.parseColor("#D8DDE4")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.RED,
                        label = "Red",
                        semantic = LightChannelSemantic.RED,
                        colorInt = Color.parseColor("#D16D6D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.GREEN,
                        label = "Green",
                        semantic = LightChannelSemantic.GREEN,
                        colorInt = Color.parseColor("#72B77D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.BLUE,
                        label = "Blue",
                        semantic = LightChannelSemantic.BLUE,
                        colorInt = Color.parseColor("#6D97D1")
                    )
                )
            }

            rawText.contains("rgb") -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.RED,
                        label = "Red",
                        semantic = LightChannelSemantic.RED,
                        colorInt = Color.parseColor("#D16D6D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.GREEN,
                        label = "Green",
                        semantic = LightChannelSemantic.GREEN,
                        colorInt = Color.parseColor("#72B77D")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.BLUE,
                        label = "Blue",
                        semantic = LightChannelSemantic.BLUE,
                        colorInt = Color.parseColor("#6D97D1")
                    )
                )
            }

            else -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.INTENSITY,
                        label = "Intensity",
                        semantic = LightChannelSemantic.UNKNOWN,
                        colorInt = Color.parseColor("#8EB8FF")
                    )
                )
            }
        }
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.isLightDevice(): Boolean {
        val rawText =
            lightSearchText()

        return rawText.contains(
            "light"
        ) || rawText.contains(
            "wrgb"
        ) || rawText.contains(
            "rgb"
        )
    }

    private fun DevicesDataStoreManager.DeviceInfoUi.lightSearchText(): String {
        val definition =
            AquaDeviceCatalog.findByType(
                type = deviceType
            )

        return listOf(
            deviceType.storageKey,
            definition?.displayName.orEmpty(),
            definition?.family?.displayName.orEmpty(),
            name,
            productModel,
            productFamily,
            aquaName
        )
            .joinToString(
                separator = " "
            )
            .lowercase()
    }

    private fun getDeviceTitle(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val definition =
            AquaDeviceCatalog.findByType(
                type = device.deviceType
            )

        return definition?.displayName
            ?: device.name.ifBlank {
                device.productModel.ifBlank {
                    "Device"
                }
            }
    }

    private fun getDeviceTypeText(
        device: DevicesDataStoreManager.DeviceInfoUi
    ): String {
        val definition =
            AquaDeviceCatalog.findByType(
                type = device.deviceType
            )

        return definition?.family?.displayName
            ?: device.productFamily.ifBlank {
                device.aquaName.ifBlank {
                    "Device"
                }
            }
    }

    private fun isDeviceOnline(
        device: DevicesDataStoreManager.DeviceInfoUi,
        statuses: Map<Long, DeviceStatusState>,
        now: Long
    ): Boolean {
        val statusState =
            statuses[device.id]

        return statusState?.isOnline ?: (
            device.lastSeenMillis > 0L &&
                now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS
            )
    }

    private fun isProgramRunningAt(
        program: SavedLightProgram,
        minute: Int
    ): Boolean {
        val start =
            program.draft.start.totalMinutes

        val end =
            LightProgramTimeMath.endMinutes(
                program.draft.end
            )

        return minute >= start && minute < end
    }

    private fun isScheduledToday(
        program: SavedLightProgram,
        weekDay: Int
    ): Boolean {
        val selectedDays =
            program.draft.selectedDays

        if (selectedDays.isEmpty()) {
            return true
        }

        return selectedDays.contains(
            appDayFromDeviceWeekDay(
                weekDay = weekDay
            )
        )
    }

    private fun appDayFromDeviceWeekDay(
        weekDay: Int
    ): Int {
        return when (weekDay) {
            in 1..7 -> {
                weekDay
            }

            else -> {
                todayAppDay()
            }
        }
    }

    private fun todayAppDay(): Int {
        val dayOfWeek =
            Calendar.getInstance()
                .get(Calendar.DAY_OF_WEEK)

        return if (dayOfWeek == Calendar.SUNDAY) {
            7
        } else {
            dayOfWeek - 1
        }
    }

    private fun currentPhoneMinute(): Int {
        val calendar =
            Calendar.getInstance()

        return calendar.get(Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(Calendar.MINUTE)
    }

    private fun buildModeContent(
        displayProgram: SavedLightProgram?,
        modeOverride: TankLightModeOverride?
    ): LightModeContent {
        when (modeOverride?.mode) {
            TankLightCardMode.MANUAL -> {
                return LightModeContent(
                    mode = TankLightCardMode.MANUAL,
                    label = "MANUAL MODE",
                    title = "Manual Control",
                    leftText = "Manual",
                    rightText = "Resume",
                    accentColorInt = Color.parseColor("#C8A86B"),
                    timelineProgressPercent = 100
                )
            }

            TankLightCardMode.SCENE -> {
                return LightModeContent(
                    mode = TankLightCardMode.SCENE,
                    label = "SCENE ACTIVE",
                    title = modeOverride.title.ifBlank {
                        "Scene Mode"
                    },
                    leftText = "Scene",
                    rightText = "Resume",
                    accentColorInt = Color.parseColor("#A37CFF"),
                    timelineProgressPercent = 100
                )
            }

            TankLightCardMode.MOONLIGHT -> {
                return LightModeContent(
                    mode = TankLightCardMode.MOONLIGHT,
                    label = "MOONLIGHT",
                    title = modeOverride.title.ifBlank {
                        "Moonlight Mode"
                    },
                    leftText = modeOverride.leftText ?: "--:--",
                    rightText = modeOverride.rightText ?: "--:--",
                    accentColorInt = Color.parseColor("#7FA7FF"),
                    timelineProgressPercent = modeOverride.timelineProgressPercent ?: 0
                )
            }

            TankLightCardMode.AUTO,
            TankLightCardMode.NO_PROGRAM,
            null -> {
                // Continue below.
            }
        }

        if (displayProgram == null) {
            return LightModeContent(
                mode = TankLightCardMode.NO_PROGRAM,
                label = "NO ACTIVE PROGRAM",
                title = "Program not set",
                leftText = "--:--",
                rightText = "--:--",
                accentColorInt = Color.parseColor("#90A1B5"),
                timelineProgressPercent = 0
            )
        }

        return LightModeContent(
            mode = TankLightCardMode.AUTO,
            label = "ACTIVE PROGRAM",
            title = displayProgram.name,
            leftText = displayProgram.draft.start.label,
            rightText = LightProgramTimeMath.endLabel(
                displayProgram.draft.end
            ),
            accentColorInt = Color.parseColor("#8EB8FF"),
            timelineProgressPercent = null
        )
    }

    private data class LightModeContent(
        val mode: TankLightCardMode,
        val label: String,
        val title: String,
        val leftText: String,
        val rightText: String,
        val accentColorInt: Int,
        val timelineProgressPercent: Int?
    )

    private data class LightChannelConfig(
        val key: TankLightChannelKey,
        val label: String,
        val semantic: LightChannelSemantic,
        val colorInt: Int
    )

    private companion object {
        private const val ONLINE_TIMEOUT_MS =
            90_000L

        private const val MINUTES_PER_DAY =
            24 * 60
    }
}