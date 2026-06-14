package com.aqua.aqualight.data.aquarium.devices.light

import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeChannelKind
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeChannelSnapshot
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeSnapshot
import com.aqua.aqualight.data.aquarium.devices.TankLightRuntimeMode
import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.api.light.LightScheduleChannelState
import com.aqua.aqualight.data.devices.light.math.LightRgbwPreviewColorMath
import android.graphics.Color

/**
 * Maps the shared LightRuntimeSnapshot into the generic tank-device card model.
 *
 * Tank detail must not calculate light output, watt or RGBW preview values by
 * itself. It receives a LightRuntimeSnapshot from the light data layer and this
 * mapper only translates the common runtime contract into the tank-card UI
 * contract.
 */
object LightTankRuntimeMapper {

    fun map(
        deviceId: Long,
        snapshot: com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSnapshot
    ): TankDeviceRuntimeSnapshot.Light {
        val startEnd = resolveScheduleRange(snapshot.scheduleChannels)

        return TankDeviceRuntimeSnapshot.Light(
            deviceId = deviceId,
            mode = snapshot.mode.toTankMode(
                hasSchedule = snapshot.scheduleChannels.any { it.points.isNotEmpty() },
                isPowerOn = snapshot.isPowerOn
            ),
            modeLabel = snapshot.mode.toModeLabel(snapshot.isPowerOn),
            programName = if (snapshot.scheduleChannels.any { it.points.isNotEmpty() }) {
                LEGACY_PROGRAM_NAME
            } else {
                NO_PROGRAM_NAME
            },
            startTimeText = startEnd?.first ?: TIME_EMPTY,
            endTimeText = startEnd?.second ?: TIME_EMPTY,
            outputPercent = snapshot.outputPercent.coerceIn(0, 100),
            timelineProgressPercent = calculateTimelineProgressPercent(
                scheduleChannels = snapshot.scheduleChannels,
                currentMinuteOfDay = snapshot.deviceTime.currentMinuteOfDay
            ),
            accentColorInt = LightRgbwPreviewColorMath.previewColor(
                red = snapshot.channels.red,
                green = snapshot.channels.green,
                blue = snapshot.channels.blue,
                white = snapshot.channels.white
            ),
            channels = snapshot.channels.toTankChannels(),
            currentWatt = snapshot.currentWatt,
            maxWatt = snapshot.maxWatt,
            powerLoadPercent = snapshot.powerLoadPercent
        )
    }

    private fun LightChannelValues.toTankChannels(): List<TankDeviceRuntimeChannelSnapshot> {
        return listOf(
            channel(
                key = TankDeviceRuntimeChannelKind.WHITE,
                label = "W",
                percent = white,
                colorInt = Color.rgb(232, 238, 246)
            ),
            channel(
                key = TankDeviceRuntimeChannelKind.RED,
                label = "R",
                percent = red,
                colorInt = Color.rgb(255, 76, 92)
            ),
            channel(
                key = TankDeviceRuntimeChannelKind.GREEN,
                label = "G",
                percent = green,
                colorInt = Color.rgb(79, 229, 142)
            ),
            channel(
                key = TankDeviceRuntimeChannelKind.BLUE,
                label = "B",
                percent = blue,
                colorInt = Color.rgb(88, 150, 255)
            )
        )
    }

    private fun channel(
        key: TankDeviceRuntimeChannelKind,
        label: String,
        percent: Int,
        colorInt: Int
    ): TankDeviceRuntimeChannelSnapshot {
        val safePercent = percent.coerceIn(0, 100)
        return TankDeviceRuntimeChannelSnapshot(
            key = key,
            label = label,
            currentPercent = safePercent,
            targetPercent = safePercent,
            colorInt = colorInt
        )
    }

    private fun LightMode.toTankMode(
        hasSchedule: Boolean,
        isPowerOn: Boolean
    ): TankLightRuntimeMode {
        return when (this) {
            LightMode.AUTO -> TankLightRuntimeMode.AUTO
            LightMode.MANUAL -> TankLightRuntimeMode.MANUAL
            LightMode.SCENE -> TankLightRuntimeMode.SCENE
            LightMode.MOONLIGHT -> TankLightRuntimeMode.MOONLIGHT
            LightMode.IDLE -> if (hasSchedule || isPowerOn) {
                TankLightRuntimeMode.WAITING
            } else {
                TankLightRuntimeMode.NO_PROGRAM
            }
            LightMode.UNKNOWN -> TankLightRuntimeMode.SYNCING
        }
    }

    private fun LightMode.toModeLabel(
        isPowerOn: Boolean
    ): String {
        return when (this) {
            LightMode.AUTO -> "Auto"
            LightMode.MANUAL -> "Manual"
            LightMode.SCENE -> "Scene"
            LightMode.MOONLIGHT -> "Moonlight"
            LightMode.IDLE -> if (isPowerOn) "Idle" else "Off"
            LightMode.UNKNOWN -> "Syncing"
        }
    }

    private fun resolveScheduleRange(
        scheduleChannels: List<LightScheduleChannelState>
    ): Pair<String, String>? {
        val points = scheduleChannels.flatMap { channel -> channel.points }
        if (points.isEmpty()) {
            return null
        }

        val start = points.minByOrNull { point -> point.minuteOfDay }
        val end = points.maxByOrNull { point -> point.minuteOfDay }

        if (start == null || end == null) {
            return null
        }

        return start.timeText to end.timeText
    }

    private fun calculateTimelineProgressPercent(
        scheduleChannels: List<LightScheduleChannelState>,
        currentMinuteOfDay: Int?
    ): Int {
        val current = currentMinuteOfDay ?: return 0
        val points = scheduleChannels.flatMap { channel -> channel.points }
        if (points.isEmpty()) {
            return 0
        }

        val start = points.minOf { point -> point.minuteOfDay }
        val end = points.maxOf { point -> point.minuteOfDay }
        if (end <= start) {
            return 0
        }

        return (((current - start).coerceIn(0, end - start).toDouble() / (end - start)) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }

    private const val LEGACY_PROGRAM_NAME = "Light schedule"
    private const val NO_PROGRAM_NAME = "No active program"
    private const val TIME_EMPTY = "--:--"
}
