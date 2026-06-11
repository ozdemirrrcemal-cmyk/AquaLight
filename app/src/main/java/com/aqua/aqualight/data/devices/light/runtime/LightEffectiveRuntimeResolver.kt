package com.aqua.aqualight.data.devices.light.runtime

import com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightChannel
import kotlin.math.roundToInt

/**
 * Single runtime decision point for light screens.
 *
 * Priority contract:
 * - Manual/preset runtime store controls the visible mode label.
 * - ESP32 live telemetry controls actual channel/output/watt values whenever fresh.
 * - Local runtime/automation values are fallback values and labels only.
 */
object LightEffectiveRuntimeResolver {

    fun syncing(deviceId: Long): LightEffectiveRuntimeState {
        return LightEffectiveRuntimeState(
            deviceId = deviceId,
            mode = LightEffectiveRuntimeMode.SYNCING,
            title = "Syncing",
            outputPercent = null,
            red = null,
            green = null,
            blue = null,
            white = null
        )
    }

    private const val MINUTES_PER_DAY = 24 * 60

    fun resolve(
        deviceId: Long,
        manualRuntime: LightManualRuntimeState,
        automationSettings: LightAutomationSettings?,
        currentMinute: Int
    ): LightEffectiveRuntimeState {
        return manualOverride(
            runtime = manualRuntime
        ) ?: automationOverride(
            deviceId = deviceId,
            settings = automationSettings,
            currentMinute = currentMinute
        ) ?: LightEffectiveRuntimeState(
            deviceId = deviceId,
            mode = LightEffectiveRuntimeMode.AUTO,
            title = "",
            outputPercent = null,
            red = null,
            green = null,
            blue = null,
            white = null
        )
    }

    fun actualOutputPercent(
        liveState: LightDeviceLiveState,
        runtimeState: LightEffectiveRuntimeState?
    ): Int {
        return when {
            liveState.hasLiveChannels -> {
                LightActualDataPolicy.actualOutputPercent(liveState)
            }

            runtimeState?.outputPercent != null -> {
                runtimeState.outputPercent.coerceIn(0, 100)
            }

            else -> 0
        }
    }

    fun actualChannelPercent(
        liveState: LightDeviceLiveState,
        semantic: LightChannelSemantic,
        runtimeState: LightEffectiveRuntimeState?
    ): Int {
        if (liveState.hasLiveChannels) {
            return when (semantic) {
                LightChannelSemantic.RED,
                LightChannelSemantic.GREEN,
                LightChannelSemantic.BLUE,
                LightChannelSemantic.WHITE -> {
                    LightActualDataPolicy.actualChannelPercent(
                        liveState = liveState,
                        semantic = semantic
                    )
                }

                LightChannelSemantic.UNKNOWN -> {
                    LightActualDataPolicy.actualOutputPercent(liveState)
                }
            }.coerceIn(0, 100)
        }

        return fallbackChannelPercent(
            runtimeState = runtimeState,
            semantic = semantic
        ) ?: 0
    }

    private fun manualOverride(
        runtime: LightManualRuntimeState
    ): LightEffectiveRuntimeState? {
        val isManualActive =
            runtime.isManualMode || runtime.isManualScene

        if (!isManualActive) {
            return null
        }

        val outputPercent =
            if (runtime.isPowerOn) {
                LightOutputMath.outputPercent(
                    red = runtime.red,
                    green = runtime.green,
                    blue = runtime.blue,
                    white = runtime.white
                )
            } else {
                0
            }

        val red = manualChannelPercent(
            isPowerOn = runtime.isPowerOn,
            value = runtime.red
        )
        val green = manualChannelPercent(
            isPowerOn = runtime.isPowerOn,
            value = runtime.green
        )
        val blue = manualChannelPercent(
            isPowerOn = runtime.isPowerOn,
            value = runtime.blue
        )
        val white = manualChannelPercent(
            isPowerOn = runtime.isPowerOn,
            value = runtime.white
        )

        return if (runtime.isManualScene) {
            LightEffectiveRuntimeState(
                deviceId = runtime.deviceId,
                mode = LightEffectiveRuntimeMode.SCENE,
                title = runtime.activeSceneName.orEmpty().ifBlank {
                    "Scene Mode"
                },
                outputPercent = outputPercent,
                red = red,
                green = green,
                blue = blue,
                white = white
            )
        } else {
            LightEffectiveRuntimeState(
                deviceId = runtime.deviceId,
                mode = LightEffectiveRuntimeMode.MANUAL,
                title = "Manual Control",
                outputPercent = outputPercent,
                red = red,
                green = green,
                blue = blue,
                white = white
            )
        }
    }

    private fun automationOverride(
        deviceId: Long,
        settings: LightAutomationSettings?,
        currentMinute: Int
    ): LightEffectiveRuntimeState? {
        val moonlight = settings?.moonlight ?: return null

        if (!moonlight.enabled) {
            return null
        }

        val startMinute = moonlight.startTime.totalMinutes
        val endMinute = moonlight.endTime.totalMinutes

        if (!isMinuteInRange(
                currentMinute = currentMinute,
                startMinute = startMinute,
                endMinute = endMinute
            )
        ) {
            return null
        }

        val intensity = moonlight.intensityPercent.coerceIn(1, 15)
        val softWhite = (intensity / 2).coerceAtLeast(1)
        val red = 0
        val green = 0
        val blue = when (moonlight.channel) {
            MoonlightChannel.BLUE,
            MoonlightChannel.BLUE_WHITE -> intensity
            MoonlightChannel.WHITE -> 0
        }
        val white = when (moonlight.channel) {
            MoonlightChannel.WHITE -> intensity
            MoonlightChannel.BLUE_WHITE -> softWhite
            MoonlightChannel.BLUE -> 0
        }

        return LightEffectiveRuntimeState(
            deviceId = deviceId,
            mode = LightEffectiveRuntimeMode.MOONLIGHT,
            title = "Moonlight Mode",
            outputPercent = LightOutputMath.outputPercent(
                red = red,
                green = green,
                blue = blue,
                white = white
            ),
            red = red,
            green = green,
            blue = blue,
            white = white,
            leftText = labelForMinute(startMinute),
            rightText = labelForMinute(endMinute),
            timelineProgressPercent = moonlightProgressPercent(
                currentMinute = currentMinute,
                startMinute = startMinute,
                endMinute = endMinute
            )
        )
    }

    private fun fallbackChannelPercent(
        runtimeState: LightEffectiveRuntimeState?,
        semantic: LightChannelSemantic
    ): Int? {
        return when (semantic) {
            LightChannelSemantic.RED -> runtimeState?.red
            LightChannelSemantic.GREEN -> runtimeState?.green
            LightChannelSemantic.BLUE -> runtimeState?.blue
            LightChannelSemantic.WHITE -> runtimeState?.white
            LightChannelSemantic.UNKNOWN -> runtimeState?.outputPercent
        }?.coerceIn(0, 100)
    }

    private fun manualChannelPercent(
        isPowerOn: Boolean,
        value: Int
    ): Int {
        if (!isPowerOn) {
            return 0
        }

        return value.coerceIn(0, 100)
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

        val duration = if (endMinute > startMinute) {
            endMinute - startMinute
        } else {
            (MINUTES_PER_DAY - startMinute) + endMinute
        }

        if (duration <= 0) {
            return 0
        }

        val elapsed = if (currentMinute >= startMinute) {
            currentMinute - startMinute
        } else {
            (MINUTES_PER_DAY - startMinute) + currentMinute
        }

        return ((elapsed.toDouble() / duration.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun labelForMinute(
        minute: Int
    ): String {
        val normalized = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val hour = normalized / 60
        val min = normalized % 60
        return "%02d:%02d".format(hour, min)
    }
}
