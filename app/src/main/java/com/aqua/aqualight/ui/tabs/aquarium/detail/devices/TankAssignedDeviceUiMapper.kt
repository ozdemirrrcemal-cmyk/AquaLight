package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import com.aqua.aqualight.data.aquarium.devices.TankAssignedDeviceCardSnapshot
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeChannelKind
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeChannelSnapshot
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeSnapshot
import com.aqua.aqualight.data.aquarium.devices.TankLightRuntimeMode
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.ui.common.devicecard.DeviceCardIconMapper

class TankAssignedDeviceUiMapper {

    fun map(
        snapshot: TankAssignedDeviceCardSnapshot
    ): TankAssignedDeviceUi {
        val commonCard =
            snapshot.commonCard

        val title =
            commonCard.title

        val subtitle =
            commonCard.productMetaText.ifBlank {
                commonCard.familyName
            }

        val iconRes =
            DeviceCardIconMapper.iconFor(
                commonCard.category
            )

        return when (val runtime = snapshot.runtime) {
            is TankDeviceRuntimeSnapshot.Light -> {
                mapLightDevice(
                    runtime = runtime,
                    title = title,
                    subtitle = subtitle,
                    iconRes = iconRes,
                    isOnline = commonCard.isOnline
                )
            }

            null -> {
                if (commonCard.category == AquaDeviceCategory.LIGHT) {
                    mapLightShell(
                        deviceId = commonCard.deviceId,
                        title = title,
                        subtitle = subtitle,
                        iconRes = iconRes,
                        isOnline = commonCard.isOnline
                    )
                } else {
                    mapGenericDevice(
                        deviceId = commonCard.deviceId,
                        title = title,
                        subtitle = subtitle,
                        iconRes = iconRes,
                        isOnline = commonCard.isOnline
                    )
                }
            }
        }
    }

    private fun mapGenericDevice(
        deviceId: Long,
        title: String,
        subtitle: String,
        iconRes: Int,
        isOnline: Boolean
    ): TankAssignedDeviceUi.Generic {
        return TankAssignedDeviceUi.Generic(
            deviceId = deviceId,
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            isOnline = isOnline
        )
    }

    private fun mapLightShell(
        deviceId: Long,
        title: String,
        subtitle: String,
        iconRes: Int,
        isOnline: Boolean
    ): TankAssignedDeviceUi.LightShell {
        return TankAssignedDeviceUi.LightShell(
            deviceId = deviceId,
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            isOnline = isOnline
        )
    }

    private fun mapLightDevice(
        runtime: TankDeviceRuntimeSnapshot.Light,
        title: String,
        subtitle: String,
        iconRes: Int,
        isOnline: Boolean
    ): TankAssignedDeviceUi.Light {
        return TankAssignedDeviceUi.Light(
            deviceId = runtime.deviceId,
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            isOnline = isOnline,
            mode = runtime.mode.toUiMode(),
            modeLabel = runtime.modeLabel,
            programName = runtime.programName,
            startTimeText = runtime.startTimeText,
            endTimeText = runtime.endTimeText,
            outputPercent = runtime.outputPercent.coerceIn(
                0,
                100
            ),
            timelineProgressPercent = runtime.timelineProgressPercent.coerceIn(
                0,
                100
            ),
            accentColorInt = runtime.accentColorInt,
            channels = runtime.channels.map { channel ->
                channel.toUiChannel()
            }
        )
    }

    private fun TankDeviceRuntimeChannelSnapshot.toUiChannel(): TankLightChannelUi {
        return TankLightChannelUi(
            key = key.toUiKey(),
            label = label,
            currentPercent = currentPercent,
            targetPercent = targetPercent,
            colorInt = colorInt
        )
    }

    private fun TankLightRuntimeMode.toUiMode(): TankLightCardMode {
        return when (this) {
            TankLightRuntimeMode.AUTO -> TankLightCardMode.AUTO
            TankLightRuntimeMode.MANUAL -> TankLightCardMode.MANUAL
            TankLightRuntimeMode.SCENE -> TankLightCardMode.SCENE
            TankLightRuntimeMode.MOONLIGHT -> TankLightCardMode.MOONLIGHT
            TankLightRuntimeMode.NO_PROGRAM -> TankLightCardMode.NO_PROGRAM
            TankLightRuntimeMode.OFFLINE -> TankLightCardMode.OFFLINE
            TankLightRuntimeMode.SYNCING -> TankLightCardMode.SYNCING
            TankLightRuntimeMode.WAITING -> TankLightCardMode.WAITING
        }
    }

    private fun TankDeviceRuntimeChannelKind.toUiKey(): TankLightChannelKey {
        return when (this) {
            TankDeviceRuntimeChannelKind.WHITE -> TankLightChannelKey.WHITE
            TankDeviceRuntimeChannelKind.RED -> TankLightChannelKey.RED
            TankDeviceRuntimeChannelKind.GREEN -> TankLightChannelKey.GREEN
            TankDeviceRuntimeChannelKind.BLUE -> TankLightChannelKey.BLUE
            TankDeviceRuntimeChannelKind.INTENSITY -> TankLightChannelKey.INTENSITY
            TankDeviceRuntimeChannelKind.UV -> TankLightChannelKey.UV
        }
    }
}
