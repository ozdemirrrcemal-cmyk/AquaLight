package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.api.light.LightScheduleChannelState
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.DeviceLightDashboardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.LightDashboardMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline.LightDashboardTimelineMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline.LightDashboardTimelineRenderResult
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline.LightDashboardTimelineSegment
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maps the common LightRuntimeSnapshot into the dashboard UI contract.
 *
 * This mapper is the only place where live Light runtime fields are formatted
 * for the dashboard. The Fragment never reads ESP32/V1 objects directly.
 */
object LightDashboardRuntimeUiMapper {

    fun map(
        context: Context,
        snapshot: LightRuntimeSnapshot
    ): DeviceLightDashboardUiState {
        val hasLedRuntime = snapshot.ledPwmChannels.isNotEmpty() ||
            snapshot.localOverride != null ||
            snapshot.channels.maxPercent > 0
        val timeline = buildTimeline(
            context = context,
            snapshot = snapshot
        )
        val mode = snapshot.mode.toDashboardMode()
        val primaryTemperature = snapshot.temperatureSensors
            .mapNotNull { sensor -> sensor.temperatureCelsius }
            .maxOrNull()
        val fanPercent = snapshot.fanOutputPercent
        val titleAndStatus = modeTitleAndStatus(
            snapshot = snapshot,
            hasTimeline = timeline.graphState.segments.isNotEmpty()
        )

        return DeviceLightDashboardUiState(
            activeProgramName = titleAndStatus.first,
            runStatus = titleAndStatus.second,
            liveMode = mode,
            currentWattText = formatWatt(snapshot.currentWatt),
            outputPercentText = if (hasLedRuntime) {
                formatPercent(snapshot.outputPercent)
            } else {
                context.getString(R.string.light_dashboard_output_empty)
            },
            redChannelText = if (hasLedRuntime) {
                formatPercent(snapshot.channels.red)
            } else {
                context.getString(R.string.light_dashboard_channel_red_empty)
            },
            greenChannelText = if (hasLedRuntime) {
                formatPercent(snapshot.channels.green)
            } else {
                context.getString(R.string.light_dashboard_channel_green_empty)
            },
            blueChannelText = if (hasLedRuntime) {
                formatPercent(snapshot.channels.blue)
            } else {
                context.getString(R.string.light_dashboard_channel_blue_empty)
            },
            whiteChannelText = if (hasLedRuntime) {
                formatPercent(snapshot.channels.white)
            } else {
                context.getString(R.string.light_dashboard_channel_white_empty)
            },
            deviceTimeText = snapshot.deviceTime.currentText.ifBlank {
                context.getString(R.string.light_dashboard_time_empty)
            },
            nextEventText = timeline.nextEventText,
            healthTemperatureText = formatTemperature(
                primaryTemperature,
                context.getString(R.string.light_dashboard_temperature_empty)
            ),
            healthTemperatureStatusText = temperatureStatusText(
                temperatureCelsius = primaryTemperature,
                limitCelsius = snapshot.thermalProtection.limitCelsius,
                reductionPercent = snapshot.thermalProtection.reductionPercent,
                unavailableText = context.getString(R.string.light_dashboard_unavailable)
            ),
            healthFanText = fanPercent?.let(::formatPercent)
                ?: context.getString(R.string.light_dashboard_unavailable),
            healthFanStatusText = fanStatusText(
                fanPercent = fanPercent,
                unavailableText = context.getString(R.string.light_dashboard_unavailable)
            ),
            timelineStatusText = timeline.statusText,
            todayPlanGraphState = timeline.graphState,
            isDeviceOnline = true,
            controlsEnabled = true,
            connectionStatusText = "Live data synced"
        )
    }

    private fun buildTimeline(
        context: Context,
        snapshot: LightRuntimeSnapshot
    ): LightDashboardTimelineRenderResult {
        val currentMinute = snapshot.deviceTime.currentMinuteOfDay
        val mainSegments = buildMainProgramSegments(snapshot.scheduleChannels)
        val nextEvent = snapshot.nextEvent?.label
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.light_dashboard_no_runtime_source)

        return when (snapshot.mode) {
            LightMode.MANUAL -> {
                val overrideSegments = mainSegments.ifEmpty {
                    buildOverrideOutputSegments(
                        snapshot = snapshot,
                        name = "Manual"
                    )
                }

                LightDashboardTimelineMapper.manualOverride(
                    currentTimeMinute = currentMinute,
                    mainSegments = overrideSegments,
                    statusText = "Manual override",
                    nextEventText = "Auto paused"
                )
            }

            LightMode.SCENE -> {
                val sceneName = snapshot.activeSceneName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val overrideSegments = mainSegments.ifEmpty {
                    buildOverrideOutputSegments(
                        snapshot = snapshot,
                        name = sceneName ?: "Scene"
                    )
                }

                LightDashboardTimelineMapper.sceneOverride(
                    currentTimeMinute = currentMinute,
                    sceneName = sceneName,
                    mainSegments = overrideSegments,
                    statusText = "Scene override",
                    nextEventText = "Auto paused"
                )
            }

            LightMode.MOONLIGHT -> {
                val moonlightSegments = buildOverrideOutputSegments(
                    snapshot = snapshot,
                    name = "Moonlight"
                )

                LightDashboardTimelineMapper.moonlightActive(
                    currentTimeMinute = currentMinute,
                    mainSegments = mainSegments.ifEmpty { moonlightSegments },
                    moonlightSegments = if (mainSegments.isEmpty()) {
                        emptyList()
                    } else {
                        moonlightSegments
                    },
                    statusText = "Moonlight active",
                    nextEventText = nextEvent
                )
            }

            LightMode.AUTO -> {
                if (mainSegments.isEmpty()) {
                    LightDashboardTimelineMapper.noActivePlan(
                        currentTimeMinute = currentMinute,
                        statusText = context.getString(R.string.light_dashboard_timeline_empty),
                        nextEventText = context.getString(R.string.light_dashboard_no_runtime_source),
                        emptyMessage = context.getString(R.string.light_dashboard_timeline_empty)
                    )
                } else {
                    LightDashboardTimelineMapper.activeAuto(
                        currentTimeMinute = currentMinute,
                        mainSegments = mainSegments,
                        statusText = "Auto schedule",
                        nextEventText = nextEvent
                    )
                }
            }

            LightMode.IDLE -> {
                if (mainSegments.isEmpty()) {
                    LightDashboardTimelineMapper.noActivePlan(
                        currentTimeMinute = currentMinute,
                        statusText = context.getString(R.string.light_dashboard_timeline_empty),
                        nextEventText = context.getString(R.string.light_dashboard_no_runtime_source),
                        emptyMessage = context.getString(R.string.light_dashboard_timeline_empty)
                    )
                } else {
                    LightDashboardTimelineMapper.activeAuto(
                        currentTimeMinute = currentMinute,
                        mainSegments = mainSegments,
                        statusText = "Idle · schedule available",
                        nextEventText = nextEvent
                    )
                }
            }

            LightMode.UNKNOWN -> {
                if (mainSegments.isEmpty()) {
                    LightDashboardTimelineMapper.noActivePlan(
                        currentTimeMinute = currentMinute,
                        statusText = context.getString(R.string.light_dashboard_timeline_empty),
                        nextEventText = context.getString(R.string.light_dashboard_no_runtime_source),
                        emptyMessage = context.getString(R.string.light_dashboard_timeline_empty)
                    )
                } else {
                    LightDashboardTimelineMapper.activeAuto(
                        currentTimeMinute = currentMinute,
                        mainSegments = mainSegments,
                        statusText = "Runtime synced",
                        nextEventText = nextEvent
                    )
                }
            }
        }
    }

    private fun buildMainProgramSegments(
        scheduleChannels: List<LightScheduleChannelState>
    ): List<LightDashboardTimelineSegment> {
        val pointsByMinute = linkedMapOf<Int, Int>()

        scheduleChannels.forEach { channel ->
            channel.points.forEach { point ->
                val minute = point.minuteOfDay.coerceIn(0, MINUTES_PER_DAY)
                val current = pointsByMinute[minute] ?: 0
                pointsByMinute[minute] = maxOf(
                    current,
                    point.percent.coerceIn(0, 100)
                )
            }
        }

        val activePoints = pointsByMinute.entries
            .sortedBy { entry -> entry.key }
            .filter { entry -> entry.value > 0 }

        if (activePoints.isEmpty()) {
            return emptyList()
        }

        val startMinute = activePoints.first().key
        val endMinute = activePoints.last().key
        if (endMinute <= startMinute) {
            return emptyList()
        }

        val maxOutput = activePoints.maxOf { entry -> entry.value }
        val peakMinutes = activePoints
            .filter { entry -> entry.value == maxOutput }
            .map { entry -> entry.key }
        val peakStart = peakMinutes.firstOrNull() ?: startMinute
        val peakEnd = peakMinutes.lastOrNull() ?: peakStart

        return listOf(
            LightDashboardTimelineSegment(
                id = "device-main-schedule",
                name = "Auto",
                startMinute = startMinute,
                peakStartMinute = peakStart,
                peakEndMinute = peakEnd,
                endMinute = endMinute,
                outputPercent = maxOutput
            )
        )
    }

    private fun buildOverrideOutputSegments(
        snapshot: LightRuntimeSnapshot,
        name: String
    ): List<LightDashboardTimelineSegment> {
        val outputPercent = maxOf(
            snapshot.outputPercent,
            snapshot.channels.maxPercent
        ).coerceIn(0, 100)

        return listOf(
            LightDashboardTimelineSegment(
                id = "device-runtime-override",
                name = name,
                startMinute = 0,
                peakStartMinute = snapshot.deviceTime.currentMinuteOfDay
                    ?.coerceIn(0, MINUTES_PER_DAY)
                    ?: 0,
                peakEndMinute = snapshot.deviceTime.currentMinuteOfDay
                    ?.coerceIn(0, MINUTES_PER_DAY)
                    ?: MINUTES_PER_DAY,
                endMinute = MINUTES_PER_DAY,
                outputPercent = maxOf(1, outputPercent)
            )
        )
    }

    private fun modeTitleAndStatus(
        snapshot: LightRuntimeSnapshot,
        hasTimeline: Boolean
    ): Pair<String, String> {
        return when (snapshot.mode) {
            LightMode.AUTO -> {
                "Auto Schedule" to if (hasTimeline) {
                    "Running from controller schedule"
                } else {
                    "No active plan today"
                }
            }

            LightMode.MANUAL -> "Manual Override" to "Auto schedule is paused"
            LightMode.SCENE -> {
                val sceneName = snapshot.activeSceneName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                "Scene Override" to if (sceneName == null) {
                    "Scene output is active"
                } else {
                    "$sceneName is active"
                }
            }
            LightMode.MOONLIGHT -> "Moonlight" to "Night output is active"
            LightMode.IDLE -> "Idle" to "Output is currently off"
            LightMode.UNKNOWN -> "Runtime Synced" to "Controller state received"
        }
    }

    private fun LightMode.toDashboardMode(): LightDashboardMode {
        return when (this) {
            LightMode.AUTO -> LightDashboardMode.AUTO
            LightMode.MANUAL -> LightDashboardMode.MANUAL
            LightMode.SCENE -> LightDashboardMode.SCENE
            LightMode.MOONLIGHT -> LightDashboardMode.MOON
            LightMode.IDLE -> LightDashboardMode.IDLE
            LightMode.UNKNOWN -> LightDashboardMode.SYNC
        }
    }

    private fun formatPercent(
        value: Int
    ): String {
        return "${value.coerceIn(0, 100)}%"
    }

    private fun formatTemperature(
        value: Double?,
        emptyText: String
    ): String {
        val safeValue = value?.takeIf { it.isFinite() } ?: return emptyText
        val rounded = (safeValue * 10.0).roundToInt() / 10.0
        val roundedInt = rounded.roundToInt()

        return if (abs(rounded - roundedInt) < 0.05) {
            "$roundedInt °C"
        } else {
            String.format(
                Locale.US,
                "%.1f °C",
                rounded
            )
        }
    }

    private fun formatWatt(
        value: Double?
    ): String {
        val safeValue = value?.takeIf { it >= 0.0 && it.isFinite() } ?: return "-- W"
        val rounded = (safeValue * 10.0).roundToInt() / 10.0
        val roundedInt = rounded.roundToInt()

        return if (abs(rounded - roundedInt) < 0.05) {
            "$roundedInt W"
        } else {
            String.format(
                Locale.US,
                "%.1f W",
                rounded
            )
        }
    }

    private fun temperatureStatusText(
        temperatureCelsius: Double?,
        limitCelsius: Double?,
        reductionPercent: Int?,
        unavailableText: String
    ): String {
        val temperature = temperatureCelsius ?: return unavailableText
        val limit = limitCelsius
        val reduction = reductionPercent

        return when {
            limit != null && temperature >= limit -> "Limit"
            reduction != null && reduction < 100 -> "Protected"
            else -> "Normal"
        }
    }

    private fun fanStatusText(
        fanPercent: Int?,
        unavailableText: String
    ): String {
        val value = fanPercent ?: return unavailableText
        return if (value > 0) {
            "Running"
        } else {
            "Idle"
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
