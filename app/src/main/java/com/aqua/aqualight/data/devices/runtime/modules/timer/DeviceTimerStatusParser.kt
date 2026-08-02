package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

object DeviceTimerStatusParser {
    private val KEYS = setOf(
        "supported", "channelCount", "scheduleCount", "lockLoop", "schema", "rootName",
        "uptimeMs", "channels", "schedules", "runtime"
    )

    fun parse(data: JSONObject): DeviceTimerStatus {
        data.requireTimerKeys(KEYS, "Timer status")
        val channels = parseChannels(data.requireTimerArray("channels"))
        val schedules = parseSchedules(data.requireTimerArray("schedules"))
        return DeviceTimerStatus(
            supported = data.requireTimerBoolean("supported"),
            channelCount = data.requireTimerInt(
                "channelCount",
                TIMER_MIN_COUNT,
                DeviceTimerRuntimeContract.Limit.MAX_CHANNELS
            ),
            scheduleCount = data.requireTimerInt(
                "scheduleCount",
                TIMER_MIN_COUNT,
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES
            ),
            lockLoop = data.requireTimerBoolean("lockLoop"),
            schema = data.requireTimerText("schema"),
            rootName = data.requireTimerText("rootName"),
            uptimeMs = data.requireTimerLong(
                "uptimeMs",
                TIMER_NON_NEGATIVE_LONG,
                TIMER_DEVICE_UPTIME_MAX_MS
            ),
            channels = channels,
            schedules = schedules,
            runtime = DeviceTimerRuntimeCapabilitiesParser.parse(
                data.requireTimerObject("runtime")
            )
        ).also(::validate)
    }

    private fun parseChannels(data: JSONArray): List<DeviceTimerChannelStatus> =
        List(data.length()) { index ->
            DeviceTimerChannelParser.parseStatus(data.requireTimerObject(index))
        }

    private fun parseSchedules(data: JSONArray): List<DeviceTimerScheduleStatus> =
        List(data.length()) { index ->
            DeviceTimerScheduleParser.parse(data.requireTimerObject(index))
        }

    private fun validate(status: DeviceTimerStatus) {
        require(status.supported)
        require(status.schema == DeviceTimerRuntimeContract.Literal.STATUS_SCHEMA)
        require(status.rootName == DeviceTimerRuntimeContract.Literal.STATUS_ROOT)
        require(status.channelCount == status.channels.size)
        require(status.scheduleCount == status.schedules.size)
        require(status.channelCount > 0)
        require(status.channels.map(DeviceTimerChannelStatus::index) == status.channels.indices.toList())
        require(status.schedules.map(DeviceTimerScheduleStatus::index) == status.schedules.indices.toList())
        require(status.channels.map(DeviceTimerChannelStatus::key).distinct().size == status.channels.size)
        require(status.channels.all { channel -> channel.editable.displayName })

        val channelKeys = status.channels.mapTo(linkedSetOf(), DeviceTimerChannelStatus::key)
        require(status.schedules.all { schedule ->
            schedule.bound && schedule.channelKey in channelKeys
        })
    }
}
