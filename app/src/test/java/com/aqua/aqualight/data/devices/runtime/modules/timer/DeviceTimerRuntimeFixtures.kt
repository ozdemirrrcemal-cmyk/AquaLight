package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceTimerRuntimeFixtures {
    fun status(
        uptimeMs: Long = 12_000L,
        channelOneRegime: String = "Auto",
        channelOneDisplayName: String = "Filter",
        schedules: JSONArray = JSONArray().put(statusSchedule())
    ): JSONObject = JSONObject()
        .put("supported", true)
        .put("channelCount", 2)
        .put("scheduleCount", schedules.length())
        .put("lockLoop", false)
        .put("schema", "aqualight.timer.v1")
        .put("rootName", "timer")
        .put("uptimeMs", uptimeMs)
        .put(
            "channels",
            JSONArray()
                .put(channel(0, "channel1", "Channel 1", channelOneDisplayName, channelOneRegime))
                .put(channel(1, "channel2", "Channel 2", "Channel 2", "Auto"))
        )
        .put("schedules", schedules)
        .put(
            "runtime",
            JSONObject()
                .put("module", "timer")
                .put("readOnly", false)
                .put("supportsConfigApply", true)
                .put("supportsChannelSet", true)
                .put("supportsSchedules", true)
                .put("supportsChannels", true)
                .put("event", "timer.status.changed")
        )

    @Suppress("LongParameterList")
    fun configApply(
        save: Boolean = true,
        appliedChannels: Boolean = true,
        appliedSchedules: Boolean = true,
        channelOneDisplayNameOverride: String? = "Return Pump",
        channelOneRegime: String = "Auto",
        schedules: JSONArray = JSONArray().put(configSchedule())
    ): JSONObject = JSONObject()
        .put("operation", "configApply")
        .put("changed", true)
        .put("saved", save)
        .put("saveRequested", save)
        .put("runtimeTransport", "websocket")
        .put("command", "timer.config.apply")
        .put("event", "timer.status.changed")
        .put("appliedChannels", appliedChannels)
        .put("appliedSchedules", appliedSchedules)
        .put(
            "config",
            JSONObject()
                .put(
                    "channels",
                    JSONArray()
                        .put(
                            configChannel(
                                channelKey = "channel1",
                                displayNameOverride = channelOneDisplayNameOverride,
                                regime = channelOneRegime
                            )
                        )
                        .put(
                            configChannel(
                                channelKey = "channel2",
                                displayNameOverride = null,
                                regime = "Auto"
                            )
                        )
                )
                .put("schedules", schedules)
        )

    fun channelSet(
        regime: String = "On",
        save: Boolean = true,
        changed: Boolean = true,
        displayName: String = "Filter"
    ): JSONObject = JSONObject()
        .put("operation", "channelSet")
        .put("changed", changed)
        .put("saved", save)
        .put("saveRequested", save)
        .put("channelKey", "channel1")
        .put("regime", regime)
        .put("runtimeTransport", "websocket")
        .put("command", "timer.channel.set")
        .put("event", "timer.status.changed")
        .put(
            "channel",
            channel(0, "channel1", "Channel 1", displayName, regime)
                .put("listIndex", 0)
        )

    fun schedulePayload(
        name: String = "Day Filter",
        channelKey: String = "channel1",
        startTimeMs: Long = 28_800_000L,
        enabled: Boolean = true
    ): DeviceTimerScheduleConfig = DeviceTimerScheduleConfig(
        enabled = enabled,
        name = name,
        channelKey = channelKey,
        weekdays = listOf(true, true, true, true, true, true, true),
        startTimeMs = startTimeMs,
        intervalOnMs = 3_600_000L,
        intervalOffMs = 0L,
        repeatCount = 1
    )

    fun configSchedule(
        name: String = "Day Filter",
        channelKey: String = "channel1",
        startTimeMs: Long = 28_800_000L,
        enabled: Boolean = true
    ): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("name", name)
        .put("channelKey", channelKey)
        .put("weekdays", JSONArray(listOf(true, true, true, true, true, true, true)))
        .put("startTimeMs", startTimeMs)
        .put("intervalOnMs", 3_600_000L)
        .put("intervalOffMs", 0L)
        .put("repeatCount", 1)
        .put("amountMl", -1.0)

    fun statusSchedule(
        name: String = "Day Filter",
        channelKey: String = "channel1",
        startTimeMs: Long = 28_800_000L,
        enabled: Boolean = true
    ): JSONObject = JSONObject()
        .put("index", 0)
        .put("enabled", enabled)
        .put("runtimeEnabled", enabled)
        .put("name", name)
        .put("channelKey", channelKey)
        .put("bound", true)
        .put("group", -1)
        .put("weekdays", JSONArray(listOf(true, true, true, true, true, true, true)))
        .put("startTimeMs", startTimeMs)
        .put("startTime", timerTimeText(startTimeMs))
        .put("intervalOnMs", 3_600_000L)
        .put("intervalOn", "01:00")
        .put("intervalOffMs", 0L)
        .put("intervalOff", "00:00")
        .put("repeatCount", 1)
        .put("pulseCountRuntime", -1)
        .put("pulseOffPending", false)
        .put("pulseRemainingMs", 0L)

    private fun channel(
        index: Int,
        key: String,
        name: String,
        displayName: String,
        regime: String
    ): JSONObject = JSONObject()
        .put("index", index)
        .put("key", key)
        .put("name", name)
        .put("displayName", displayName)
        .put("profileManaged", true)
        .put("regime", regime)
        .put("channelKind", "gpio")
        .put("gpio", 4 + index)
        .put("ledcChannel", index)
        .put("group", -1)
        .put("valueNow", if (regime == "On") 1.0 else 0.0)
        .put("valueAuto", if (regime == "On") 1.0 else 0.0)
        .put("valueManual", -1.0)
        .put("manualTimeoutMs", 0L)
        .put("invert", false)
        .put("pwmResolutionBits", 10)
        .put("pwmFrequencyHz", 5_000)
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", true)
                .put("hardwareCalibration", false)
        )

    private fun configChannel(
        channelKey: String,
        displayNameOverride: String?,
        regime: String
    ): JSONObject = JSONObject()
        .put("channelKey", channelKey)
        .put("regime", regime)
        .also { channel ->
            displayNameOverride?.let { displayName ->
                channel.put("displayName", displayName)
            }
        }
}
