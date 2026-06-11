package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.graphics.Color
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.card.DeviceCardStateMapper
import com.aqua.aqualight.data.devices.catalog.light.LightChannelColor
import com.aqua.aqualight.data.devices.catalog.light.LightProductCatalog
import com.aqua.aqualight.data.devices.light.runtime.LightActualDataPolicy
import com.aqua.aqualight.data.devices.light.runtime.LightChannelSemantic
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightOutputMath
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import java.util.Calendar
import kotlin.math.roundToInt

class TankAssignedDeviceUiMapper {

    private val deviceCardStateMapper =
        DeviceCardStateMapper()

    fun isLightDeviceForObserver(
        device: DevicesDataStoreManager.DeviceInfo
    ): Boolean {
        return device.isLightDevice()
    }

    fun map(
        device: DevicesDataStoreManager.DeviceInfo,
        statuses: Map<Long, DeviceStatusState>,
        programs: List<SavedLightProgram>,
        lightState: LightDeviceLiveState?,
        now: Long,
        modeOverride: TankLightModeOverride? = null
    ): TankAssignedDeviceUi {
        val commonCardState =
            deviceCardStateMapper.map(
                device = device,
                statuses = statuses,
                nowMillis = now,
                unknownTankText = "Unknown aquarium"
            )

        val title =
            commonCardState.title

        val subtitle =
            commonCardState.familyName

        val online =
            commonCardState.isOnline ||
                (
                    device.isLightDevice() &&
                        lightState?.let { state ->
                            state.hasFreshLiveData || state.hasDeviceTime
                        } == true
                    )

        val iconRes =
            DeviceIconMapper.iconFor(
                commonCardState.deviceType
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
        device: DevicesDataStoreManager.DeviceInfo,
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

        val waitingForFirstRuntimeEmission =
            lightState == null
        val shouldSuppressStoredRuntime =
            waitingForFirstRuntimeEmission || !liveState.hasLiveChannels

        val displayProgramForCard = if (shouldSuppressStoredRuntime) {
            null
        } else {
            displayProgram
        }

        val effectiveModeOverride =
            if (online && !shouldSuppressStoredRuntime) modeOverride else null

        val outputPercent =
            when {
                shouldSuppressStoredRuntime -> {
                    0
                }

                online && effectiveModeOverride?.outputPercent != null -> {
                    effectiveModeOverride.outputPercent
                }

                liveState.hasLiveChannels -> {
                    LightActualDataPolicy.actualOutputPercent(liveState)
                }

                else -> {
                    0
                }
            }

        val modeContent =
            if (shouldSuppressStoredRuntime) {
                buildLiveSyncingContent(
                    online = online || waitingForFirstRuntimeEmission
                )
            } else {
                buildModeContent(
                    displayProgram = displayProgramForCard,
                    modeOverride = effectiveModeOverride
                )
            }

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
                displayProgram = displayProgramForCard,
                currentMinute = currentMinute,
                modeOverride = effectiveModeOverride
            )
        )
    }

    private fun buildLightChannels(
        device: DevicesDataStoreManager.DeviceInfo,
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
        if (liveState.hasLiveChannels) {
            overrideChannelPercent(
                semantic = channel.semantic,
                modeOverride = modeOverride
            )?.let { percent ->
                return percent
            }

            return when (channel.semantic) {
                LightChannelSemantic.RED,
                LightChannelSemantic.GREEN,
                LightChannelSemantic.BLUE,
                LightChannelSemantic.WHITE -> {
                    LightActualDataPolicy.actualChannelPercent(
                        liveState = liveState,
                        semantic = channel.semantic
                    )
                }

                LightChannelSemantic.UNKNOWN -> {
                    LightActualDataPolicy.actualOutputPercent(liveState).coerceIn(
                        0,
                        100
                    )
                }
            }
        }

        return 0
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
                LightOutputMath.outputPercent(
                    red = values.red,
                    green = values.green,
                    blue = values.blue,
                    white = values.white
                )
            }
        }.coerceIn(
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

    private fun DevicesDataStoreManager.DeviceInfo.supportedLightChannels(): List<LightChannelConfig> {
        val catalogDefinition =
            LightProductCatalog.findByType(
                type = deviceType
            ) ?: LightProductCatalog.findByProductId(
                productId = productId
            ) ?: LightProductCatalog.findByLegacyIdentity(
                aquaName = aquaName,
                name = name
            )

        val catalogChannels =
            catalogDefinition
                ?.channels
                .orEmpty()
                .sortedBy { channel ->
                    channel.order
                }
                .mapNotNull { channel ->
                    channel.color.toTankLightChannelConfig(
                        displayName = channel.displayName
                    )
                }

        if (catalogChannels.isNotEmpty()) {
            return catalogChannels
        }

        return fallbackLightChannelsByChannelCount()
    }

    private fun DevicesDataStoreManager.DeviceInfo.isLightDevice(): Boolean {
        val catalogDefinition =
            LightProductCatalog.findByType(
                type = deviceType
            ) ?: LightProductCatalog.findByProductId(
                productId = productId
            ) ?: LightProductCatalog.findByLegacyIdentity(
                aquaName = aquaName,
                name = name
            )

        if (catalogDefinition != null) {
            return true
        }

        if (tabLight) {
            return true
        }

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

    private fun LightChannelColor.toTankLightChannelConfig(
        displayName: String
    ): LightChannelConfig? {
        return when (this) {
            LightChannelColor.WHITE -> {
                LightChannelConfig(
                    key = TankLightChannelKey.WHITE,
                    label = displayName.ifBlank {
                        "White"
                    },
                    semantic = LightChannelSemantic.WHITE,
                    colorInt = Color.parseColor("#DDE2E8")
                )
            }

            LightChannelColor.RED -> {
                LightChannelConfig(
                    key = TankLightChannelKey.RED,
                    label = displayName.ifBlank {
                        "Red"
                    },
                    semantic = LightChannelSemantic.RED,
                    colorInt = Color.parseColor("#D86E72")
                )
            }

            LightChannelColor.GREEN -> {
                LightChannelConfig(
                    key = TankLightChannelKey.GREEN,
                    label = displayName.ifBlank {
                        "Green"
                    },
                    semantic = LightChannelSemantic.GREEN,
                    colorInt = Color.parseColor("#72C37F")
                )
            }

            LightChannelColor.BLUE -> {
                LightChannelConfig(
                    key = TankLightChannelKey.BLUE,
                    label = displayName.ifBlank {
                        "Blue"
                    },
                    semantic = LightChannelSemantic.BLUE,
                    colorInt = Color.parseColor("#6FA0E0")
                )
            }

            LightChannelColor.WARM_WHITE -> {
                LightChannelConfig(
                    key = TankLightChannelKey.WHITE,
                    label = displayName.ifBlank {
                        "Warm White"
                    },
                    semantic = LightChannelSemantic.WHITE,
                    colorInt = Color.parseColor("#E2D0AA")
                )
            }

            LightChannelColor.COOL_WHITE -> {
                LightChannelConfig(
                    key = TankLightChannelKey.WHITE,
                    label = displayName.ifBlank {
                        "Cool White"
                    },
                    semantic = LightChannelSemantic.WHITE,
                    colorInt = Color.parseColor("#DDE2E8")
                )
            }

            LightChannelColor.UV -> {
                LightChannelConfig(
                    key = TankLightChannelKey.UV,
                    label = displayName.ifBlank {
                        "UV"
                    },
                    semantic = LightChannelSemantic.UNKNOWN,
                    colorInt = Color.parseColor("#A37CFF")
                )
            }

            LightChannelColor.CUSTOM -> {
                LightChannelConfig(
                    key = TankLightChannelKey.INTENSITY,
                    label = displayName.ifBlank {
                        "Intensity"
                    },
                    semantic = LightChannelSemantic.UNKNOWN,
                    colorInt = Color.parseColor("#8EB8FF")
                )
            }
        }
    }

    private fun DevicesDataStoreManager.DeviceInfo.fallbackLightChannelsByChannelCount(): List<LightChannelConfig> {
        return when (channelCount) {
            4 -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.WHITE,
                        label = "White",
                        semantic = LightChannelSemantic.WHITE,
                        colorInt = Color.parseColor("#DDE2E8")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.RED,
                        label = "Red",
                        semantic = LightChannelSemantic.RED,
                        colorInt = Color.parseColor("#D86E72")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.GREEN,
                        label = "Green",
                        semantic = LightChannelSemantic.GREEN,
                        colorInt = Color.parseColor("#72C37F")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.BLUE,
                        label = "Blue",
                        semantic = LightChannelSemantic.BLUE,
                        colorInt = Color.parseColor("#6FA0E0")
                    )
                )
            }

            3 -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.RED,
                        label = "Red",
                        semantic = LightChannelSemantic.RED,
                        colorInt = Color.parseColor("#D86E72")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.GREEN,
                        label = "Green",
                        semantic = LightChannelSemantic.GREEN,
                        colorInt = Color.parseColor("#72C37F")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.BLUE,
                        label = "Blue",
                        semantic = LightChannelSemantic.BLUE,
                        colorInt = Color.parseColor("#6FA0E0")
                    )
                )
            }

            2 -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.WHITE,
                        label = "White",
                        semantic = LightChannelSemantic.WHITE,
                        colorInt = Color.parseColor("#DDE2E8")
                    ),
                    LightChannelConfig(
                        key = TankLightChannelKey.BLUE,
                        label = "Blue",
                        semantic = LightChannelSemantic.BLUE,
                        colorInt = Color.parseColor("#6FA0E0")
                    )
                )
            }

            1 -> {
                listOf(
                    LightChannelConfig(
                        key = TankLightChannelKey.INTENSITY,
                        label = "Intensity",
                        semantic = LightChannelSemantic.UNKNOWN,
                        colorInt = Color.parseColor("#8EB8FF")
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

    private fun DevicesDataStoreManager.DeviceInfo.lightSearchText(): String {
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

    private fun buildLiveSyncingContent(
        online: Boolean
    ): LightModeContent {
        return LightModeContent(
            mode = TankLightCardMode.NO_PROGRAM,
            label = if (online) {
                "SYNCING LIVE DATA"
            } else {
                "DEVICE OFFLINE"
            },
            title = if (online) {
                "Reading device"
            } else {
                "Waiting for connection"
            },
            leftText = if (online) {
                "Live"
            } else {
                "Offline"
            },
            rightText = "--",
            accentColorInt = Color.parseColor("#90A1B5"),
            timelineProgressPercent = 0
        )
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
        private const val MINUTES_PER_DAY =
            24 * 60
    }
}