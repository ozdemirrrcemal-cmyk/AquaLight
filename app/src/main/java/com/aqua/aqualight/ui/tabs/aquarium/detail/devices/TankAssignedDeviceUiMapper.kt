package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.graphics.Color
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.card.DeviceCardStateMapper
import com.aqua.aqualight.data.devices.catalog.light.LightChannelColor
import com.aqua.aqualight.data.devices.catalog.light.LightProductCatalog
import com.aqua.aqualight.data.devices.light.runtime.LightActualDataPolicy
import com.aqua.aqualight.data.devices.light.runtime.LightChannelSemantic
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceLiveState
import com.aqua.aqualight.data.devices.light.runtime.LightProgramRuntimeEvaluator
import com.aqua.aqualight.data.devices.light.runtime.LightOutputMath
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper

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
                unknownTankText = TankAssignedDeviceText.UNKNOWN_AQUARIUM
            )

        val title =
            commonCardState.title

        val subtitle =
            commonCardState.productMetaText.ifBlank {
                commonCardState.familyName
            }

        val online =
            commonCardState.isOnline

        val iconRes =
            DeviceIconMapper.iconFor(
                commonCardState.category
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

        val runtimeEvaluation =
            LightProgramRuntimeEvaluator.evaluate(
                deviceId = device.id,
                programs = programs,
                deviceTime = liveState.deviceTime.takeIf {
                    online && liveState.hasDeviceTime
                }
            )

        val effectiveModeOverride =
            if (online) modeOverride else null

        val hasActualLiveData =
            LightActualDataPolicy.hasActualData(
                isOnline = online,
                liveState = liveState
            )

        val outputPercent =
            LightActualDataPolicy.actualOutputPercent(
                isOnline = online,
                liveState = liveState
            )

        val modeContent =
            buildModeContent(
                isOnline = online,
                hasDeviceTime = runtimeEvaluation.hasDeviceTime,
                hasActualLiveData = hasActualLiveData,
                displayProgram = runtimeEvaluation.displayProgram,
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
                ?: LightProgramRuntimeEvaluator.progressPercent(
                    program = runtimeEvaluation.displayProgram,
                    currentMinute = runtimeEvaluation.currentMinute
                ),
            accentColorInt = modeContent.accentColorInt,
            channels = buildLightChannels(
                device = device,
                isOnline = online,
                liveState = liveState,
                displayProgram = runtimeEvaluation.displayProgram
            )
        )
    }

    private fun buildLightChannels(
        device: DevicesDataStoreManager.DeviceInfo,
        isOnline: Boolean,
        liveState: LightDeviceLiveState,
        displayProgram: SavedLightProgram?
    ): List<TankLightChannelUi> {
        return device.supportedLightChannels().map { channel ->
            val currentPercent =
                currentLightChannelPercent(
                    channel = channel,
                    isOnline = isOnline,
                    liveState = liveState
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
        isOnline: Boolean,
        liveState: LightDeviceLiveState
    ): Int {
        return LightActualDataPolicy.actualChannelPercent(
            isOnline = isOnline,
            liveState = liveState,
            semantic = channel.semantic
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

    private fun DevicesDataStoreManager.DeviceInfo.supportedLightChannels(): List<LightChannelConfig> {
        val catalogDefinition =
            LightProductCatalog.findByProductKey(
                productKey = productKey
            ) ?: LightProductCatalog.findByProductId(
                productId = productId
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
            LightProductCatalog.findByProductKey(
                productKey = productKey
            ) ?: LightProductCatalog.findByProductId(
                productId = productId
            )

        return catalogDefinition != null || category == AquaDeviceCategory.LIGHT
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
            AquaDeviceCatalog.findDefinition(
                productId = productId,
                productKey = productKey,
                category = category
            )

        return listOf(
            productKey.storageKey,
            category.storageKey,
            definition?.displayName.orEmpty(),
            definition?.productFamily.orEmpty(),
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

    private fun buildModeContent(
    isOnline: Boolean,
    hasDeviceTime: Boolean,
    hasActualLiveData: Boolean,
    displayProgram: SavedLightProgram?,
    modeOverride: TankLightModeOverride?
): LightModeContent {
    if (!isOnline) {
        return LightModeContent(
            mode = TankLightCardMode.OFFLINE,
            label = TankAssignedDeviceText.OFFLINE_LABEL,
            title = TankAssignedDeviceText.NO_LIVE_DATA_TITLE,
            leftText = TankAssignedDeviceText.EMPTY_TIME_TEXT,
            rightText = TankAssignedDeviceText.EMPTY_TIME_TEXT,
            accentColorInt = Color.parseColor("#90A1B5"),
            timelineProgressPercent = 0
        )
    }

    when (modeOverride?.mode) {
        TankLightCardMode.MANUAL -> {
            return LightModeContent(
                mode = TankLightCardMode.MANUAL,
                label = TankAssignedDeviceText.MANUAL_LABEL,
                title = TankAssignedDeviceText.MANUAL_CONTROL_TITLE,
                leftText = TankAssignedDeviceText.MANUAL_LEFT_TEXT,
                rightText = TankAssignedDeviceText.RESUME_RIGHT_TEXT,
                accentColorInt = Color.parseColor("#C8A86B"),
                timelineProgressPercent = 100
            )
        }

        TankLightCardMode.SCENE -> {
            return LightModeContent(
                mode = TankLightCardMode.SCENE,
                label = TankAssignedDeviceText.SCENE_LABEL,
                title = modeOverride.title.ifBlank {
                    TankAssignedDeviceText.SCENE_MODE_TITLE
                },
                leftText = TankAssignedDeviceText.SCENE_LEFT_TEXT,
                rightText = TankAssignedDeviceText.RESUME_RIGHT_TEXT,
                accentColorInt = Color.parseColor("#A37CFF"),
                timelineProgressPercent = 100
            )
        }

        TankLightCardMode.MOONLIGHT -> {
            return LightModeContent(
                mode = TankLightCardMode.MOONLIGHT,
                label = TankAssignedDeviceText.MOONLIGHT_LABEL,
                title = modeOverride.title.ifBlank {
                    TankAssignedDeviceText.MOONLIGHT_MODE_TITLE
                },
                leftText = modeOverride.leftText ?: "--:--",
                rightText = modeOverride.rightText ?: "--:--",
                accentColorInt = Color.parseColor("#7FA7FF"),
                timelineProgressPercent = modeOverride.timelineProgressPercent ?: 0
            )
        }

        TankLightCardMode.AUTO,
        TankLightCardMode.NO_PROGRAM,
        TankLightCardMode.OFFLINE,
        TankLightCardMode.SYNCING,
        TankLightCardMode.WAITING,
        null -> {
            // Continue with automatic card state.
        }
    }

    if (!hasDeviceTime) {
        return LightModeContent(
            mode = TankLightCardMode.SYNCING,
            label = TankAssignedDeviceText.SYNCING_LABEL,
            title = TankAssignedDeviceText.WAITING_FOR_TIME_TITLE,
            leftText = TankAssignedDeviceText.EMPTY_TIME_TEXT,
            rightText = TankAssignedDeviceText.EMPTY_TIME_TEXT,
            accentColorInt = Color.parseColor("#90A1B5"),
            timelineProgressPercent = 0
        )
    }

    if (displayProgram == null) {
        return LightModeContent(
            mode = TankLightCardMode.NO_PROGRAM,
            label = TankAssignedDeviceText.NO_ACTIVE_PROGRAM_LABEL,
            title = TankAssignedDeviceText.PROGRAM_NOT_SET_TITLE,
            leftText = TankAssignedDeviceText.EMPTY_TIME_TEXT,
            rightText = TankAssignedDeviceText.EMPTY_TIME_TEXT,
            accentColorInt = Color.parseColor("#90A1B5"),
            timelineProgressPercent = 0
        )
    }

    return LightModeContent(
        mode = TankLightCardMode.AUTO,
        label = if (hasActualLiveData) {
            TankAssignedDeviceText.ACTIVE_PROGRAM_LABEL
        } else {
            TankAssignedDeviceText.SCHEDULED_LABEL
        },
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

}