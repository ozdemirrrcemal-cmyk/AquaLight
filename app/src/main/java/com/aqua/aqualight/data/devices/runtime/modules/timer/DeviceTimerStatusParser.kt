package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

object DeviceTimerStatusParser {

    fun parse(
        data: JSONObject
    ): DeviceTimerStatus {
        val status = data.optJSONObject("status") ?: data

        return DeviceTimerStatus(
            supported = status.optBoolean("supported", false),
            channelCount = status.optInt("channelCount", 0),
            scheduleCount = status.optInt("scheduleCount", 0),
            lockLoop = status.optBoolean("lockLoop", false),
            schema = status.optString("schema", ""),
            rootName = status.optString("rootName", DeviceTimerRuntimeContract.MODULE),
            uptimeMs = status.optLong("uptimeMs", 0L),
            channels = parseChannels(status.optJSONArray("channels")),
            schedules = parseSchedules(status.optJSONArray("schedules")),
            runtime = parseRuntime(status.optJSONObject("runtime"))
        )
    }

    private fun parseRuntime(
        runtime: JSONObject?
    ): DeviceTimerRuntimeCapabilities {
        return DeviceTimerRuntimeCapabilities(
            module = runtime?.optString("module", DeviceTimerRuntimeContract.MODULE)
                ?: DeviceTimerRuntimeContract.MODULE,
            readOnly = runtime?.optBoolean("readOnly", false) ?: false,
            supportsConfigApply = runtime?.optBoolean("supportsConfigApply", false) ?: false,
            supportsChannelSet = runtime?.optBoolean("supportsChannelSet", false) ?: false,
            supportsSchedules = runtime?.optBoolean("supportsSchedules", false) ?: false,
            supportsChannels = runtime?.optBoolean("supportsChannels", false) ?: false,
            event = runtime?.optString("event", "") ?: ""
        )
    }

    private fun parseChannels(
        channels: JSONArray?
    ): List<DeviceTimerChannelStatus> {
        if (channels == null) return emptyList()

        return buildList {
            for (index in 0 until channels.length()) {
                val item = channels.optJSONObject(index) ?: continue
                add(parseChannel(item))
            }
        }
    }

    private fun parseChannel(
        item: JSONObject
    ): DeviceTimerChannelStatus {
        val editable = item.optJSONObject("editable")

        return DeviceTimerChannelStatus(
            index = item.optInt("index", -1),
            key = item.optString("key", ""),
            name = item.optString("name", ""),
            displayName = item.optString("displayName", item.optString("name", "")),
            profileManaged = item.optBoolean("profileManaged", false),
            regime = DeviceTimerRegime.fromWire(item.optString("regime", DeviceTimerRegime.OFF.wireValue)),
            channelKind = item.optString("channelKind", ""),
            gpio = item.optInt("gpio", -1),
            ledcChannel = item.optInt("ledcChannel", -1),
            group = item.optInt("group", -1),
            valueNow = item.optDouble("valueNow", 0.0),
            valueAuto = item.optDouble("valueAuto", 0.0),
            valueManual = item.optDouble("valueManual", -1.0),
            manualTimeoutMs = item.optLong("manualTimeoutMs", 0L),
            invert = item.optBoolean("invert", false),
            pwmResolutionBits = item.optInt("pwmResolutionBits", 0),
            pwmFrequencyHz = item.optInt("pwmFrequencyHz", 0),
            editable = DeviceTimerChannelEditable(
                hardware = editable?.optBoolean("hardware", false) ?: false,
                displayName = editable?.optBoolean("displayName", false) ?: false,
                hardwareCalibration = editable?.optBoolean("hardwareCalibration", false) ?: false
            )
        )
    }

    private fun parseSchedules(
        schedules: JSONArray?
    ): List<DeviceTimerScheduleStatus> {
        if (schedules == null) return emptyList()

        return buildList {
            for (index in 0 until schedules.length()) {
                val item = schedules.optJSONObject(index) ?: continue
                add(parseSchedule(item))
            }
        }
    }

    private fun parseSchedule(
        item: JSONObject
    ): DeviceTimerScheduleStatus {
        return DeviceTimerScheduleStatus(
            index = item.optInt("index", -1),
            enabled = item.optBoolean("enabled", false),
            runtimeEnabled = item.optBoolean("runtimeEnabled", false),
            name = item.optString("name", ""),
            channelKey = item.optString("channelKey", ""),
            bound = item.optBoolean("bound", false),
            group = item.optInt("group", -1),
            weekdays = parseWeekdays(item.optJSONArray("weekdays")),
            startTimeMs = item.optLong("startTimeMs", 0L),
            startTime = item.optString("startTime", ""),
            intervalOnMs = item.optLong("intervalOnMs", 0L),
            intervalOn = item.optString("intervalOn", ""),
            intervalOffMs = item.optLong("intervalOffMs", 0L),
            intervalOff = item.optString("intervalOff", ""),
            repeatCount = item.optInt("repeatCount", 0),
            pulseCountRuntime = item.optInt("pulseCountRuntime", 0),
            pulseOffPending = item.optBoolean("pulseOffPending", false),
            pulseRemainingMs = item.optLong("pulseRemainingMs", 0L)
        )
    }

    private fun parseWeekdays(
        weekdays: JSONArray?
    ): List<Boolean> {
        if (weekdays == null) return List(7) { false }

        return List(7) { index ->
            weekdays.optBoolean(index, false)
        }
    }
}
