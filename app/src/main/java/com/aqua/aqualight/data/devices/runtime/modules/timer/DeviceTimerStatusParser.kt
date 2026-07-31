package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.runtime.parsing.requireExactKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requiredArray
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBooleans
import com.aqua.aqualight.data.devices.runtime.parsing.requiredFiniteDouble
import com.aqua.aqualight.data.devices.runtime.parsing.requiredInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requiredObject
import com.aqua.aqualight.data.devices.runtime.parsing.requiredString
import org.json.JSONArray
import org.json.JSONObject

object DeviceTimerStatusParser {

    fun parse(data: JSONObject): DeviceTimerStatus {
        val status = data.optJSONObject("status") ?: data
        status.requireExactKeys(STATUS_KEYS, "timer.status.get.data")
        val channels = parseChannels(status.requiredArray("channels"))
        val schedules = parseSchedules(status.requiredArray("schedules"))
        val channelCount = status.requiredNonNegativeInt("channelCount")
        val scheduleCount = status.requiredNonNegativeInt("scheduleCount")
        require(channelCount == channels.size) {
            "timer channelCount differs from channels array size."
        }
        require(scheduleCount == schedules.size) {
            "timer scheduleCount differs from schedules array size."
        }
        require(scheduleCount <= DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES) {
            "timer scheduleCount exceeds the firmware limit."
        }
        validateUniqueChannels(channels)
        validateSchedules(schedules, channels)

        return DeviceTimerStatus(
            supported = status.requiredBoolean("supported"),
            channelCount = channelCount,
            scheduleCount = scheduleCount,
            lockLoop = status.requiredBoolean("lockLoop"),
            schema = status.requiredString("schema"),
            rootName = status.requiredString("rootName"),
            uptimeMs = status.requiredNonNegativeLong("uptimeMs"),
            channels = channels,
            schedules = schedules,
            runtime = parseRuntime(status.requiredObject("runtime"))
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceTimerRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "timer.status.get.data.runtime")
        return DeviceTimerRuntimeCapabilities(
            module = runtime.requiredString("module").also {
                require(it == DeviceTimerRuntimeContract.MODULE)
            },
            readOnly = runtime.requiredBoolean("readOnly").also { require(!it) },
            supportsConfigApply = runtime.requiredBoolean("supportsConfigApply").also {
                require(it)
            },
            supportsChannelSet = runtime.requiredBoolean("supportsChannelSet").also {
                require(it)
            },
            supportsSchedules = runtime.requiredBoolean("supportsSchedules").also {
                require(it)
            },
            supportsChannels = runtime.requiredBoolean("supportsChannels").also {
                require(it)
            },
            event = runtime.requiredString("event").also {
                require(it == TIMER_STATUS_CHANGED_EVENT)
            }
        )
    }

    private fun parseChannels(channels: JSONArray): List<DeviceTimerChannelStatus> =
        List(channels.length()) { index ->
            parseChannel(channels.requiredObject(index))
        }

    private fun parseChannel(item: JSONObject): DeviceTimerChannelStatus {
        item.requireExactKeys(CHANNEL_KEYS, "timer channel")
        val editable = item.requiredObject("editable")
        editable.requireExactKeys(EDITABLE_KEYS, "timer channel editable")

        return DeviceTimerChannelStatus(
            index = item.requiredNonNegativeInt("index"),
            key = item.requiredString("key"),
            name = item.requiredString("name"),
            displayName = item.requiredString("displayName"),
            profileManaged = item.requiredBoolean("profileManaged"),
            regime = parseRegime(item.requiredString("regime")),
            channelKind = item.requiredString("channelKind").also {
                require(it in CHANNEL_KINDS)
            },
            gpio = item.requiredInt("gpio").also { require(it >= UNBOUND_INDEX) },
            ledcChannel = item.requiredInt("ledcChannel").also {
                require(it >= UNBOUND_INDEX)
            },
            group = item.requiredInt("group").also { require(it >= UNBOUND_INDEX) },
            valueNow = item.requiredFiniteDouble("valueNow"),
            valueAuto = item.requiredFiniteDouble("valueAuto"),
            valueManual = item.requiredFiniteDouble("valueManual"),
            manualTimeoutMs = item.requiredNonNegativeLong("manualTimeoutMs"),
            invert = item.requiredBoolean("invert"),
            pwmResolutionBits = item.requiredNonNegativeInt("pwmResolutionBits"),
            pwmFrequencyHz = item.requiredNonNegativeInt("pwmFrequencyHz"),
            editable = DeviceTimerChannelEditable(
                hardware = editable.requiredBoolean("hardware"),
                displayName = editable.requiredBoolean("displayName"),
                hardwareCalibration = editable.requiredBoolean("hardwareCalibration")
            )
        )
    }

    private fun parseSchedules(schedules: JSONArray): List<DeviceTimerScheduleStatus> =
        List(schedules.length()) { index ->
            parseSchedule(schedules.requiredObject(index))
        }

    private fun parseSchedule(item: JSONObject): DeviceTimerScheduleStatus {
        item.requireExactKeys(SCHEDULE_KEYS, "timer schedule")
        return DeviceTimerScheduleStatus(
            index = item.requiredNonNegativeInt("index"),
            enabled = item.requiredBoolean("enabled"),
            runtimeEnabled = item.requiredBoolean("runtimeEnabled"),
            name = item.requiredString("name"),
            channelKey = item.requiredString("channelKey"),
            bound = item.requiredBoolean("bound"),
            group = item.requiredInt("group").also { require(it >= UNBOUND_INDEX) },
            weekdays = item.requiredArray("weekdays").requiredBooleans(
                expectedSize = WEEKDAY_COUNT,
                label = "timer schedule weekdays"
            ),
            startTimeMs = item.requiredNonNegativeLong("startTimeMs").also {
                require(it < MILLIS_PER_DAY)
            },
            startTime = item.requiredString("startTime"),
            intervalOnMs = item.requiredNonNegativeLong("intervalOnMs"),
            intervalOn = item.requiredString("intervalOn"),
            intervalOffMs = item.requiredNonNegativeLong("intervalOffMs"),
            intervalOff = item.requiredString("intervalOff"),
            repeatCount = item.requiredNonNegativeInt("repeatCount"),
            pulseCountRuntime = item.requiredNonNegativeInt("pulseCountRuntime"),
            pulseOffPending = item.requiredBoolean("pulseOffPending"),
            pulseRemainingMs = item.requiredNonNegativeLong("pulseRemainingMs")
        )
    }

    private fun parseRegime(value: String): DeviceTimerRegime = when (value) {
        DeviceTimerRegime.AUTO.wireValue -> DeviceTimerRegime.AUTO
        DeviceTimerRegime.ON.wireValue -> DeviceTimerRegime.ON
        DeviceTimerRegime.OFF.wireValue -> DeviceTimerRegime.OFF
        else -> error("Unknown timer regime: $value")
    }

    private fun validateUniqueChannels(channels: List<DeviceTimerChannelStatus>) {
        require(channels.map(DeviceTimerChannelStatus::index).distinct().size == channels.size) {
            "timer channel indexes must be unique."
        }
        require(channels.map(DeviceTimerChannelStatus::key).distinct().size == channels.size) {
            "timer channel keys must be unique."
        }
    }

    private fun validateSchedules(
        schedules: List<DeviceTimerScheduleStatus>,
        channels: List<DeviceTimerChannelStatus>
    ) {
        require(schedules.map(DeviceTimerScheduleStatus::index).distinct().size == schedules.size) {
            "timer schedule indexes must be unique."
        }
        val channelKeys = channels.mapTo(hashSetOf(), DeviceTimerChannelStatus::key)
        schedules.filter(DeviceTimerScheduleStatus::bound).forEach { schedule ->
            require(schedule.channelKey in channelKeys) {
                "bound timer schedule references an unknown channelKey."
            }
        }
    }

    private val STATUS_KEYS = setOf(
        "supported", "channelCount", "scheduleCount", "lockLoop", "schema", "rootName",
        "uptimeMs", "channels", "schedules", "runtime"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsChannelSet",
        "supportsSchedules", "supportsChannels", "event"
    )
    private val CHANNEL_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime", "channelKind",
        "gpio", "ledcChannel", "group", "valueNow", "valueAuto", "valueManual",
        "manualTimeoutMs", "invert", "pwmResolutionBits", "pwmFrequencyHz", "editable"
    )
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "hardwareCalibration")
    private val SCHEDULE_KEYS = setOf(
        "index", "enabled", "runtimeEnabled", "name", "channelKey", "bound", "group",
        "weekdays", "startTimeMs", "startTime", "intervalOnMs", "intervalOn",
        "intervalOffMs", "intervalOff", "repeatCount", "pulseCountRuntime",
        "pulseOffPending", "pulseRemainingMs"
    )
    private val CHANNEL_KINDS = setOf("gpio", "digital", "none")

    private const val TIMER_STATUS_CHANGED_EVENT = "timer.status.changed"
    private const val WEEKDAY_COUNT = 7
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val UNBOUND_INDEX = -1
}
