package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

object DeviceTimerStatusParser {
    private val REQUIRED_KEYS = setOf(
        "supported", "channelCount", "scheduleCount", "maxSchedulesPerChannel",
        "maxScheduleCount", "revision", "lockLoop", "schema", "schemaVersion", "rootName",
        "uptimeMs", "channelScoped", "schedulesIncluded", "channels", "schedules",
        "returnedScheduleCount", "runtime"
    )
    private val OPTIONAL_KEYS = setOf("selectedChannelKey")

    fun parse(data: JSONObject): DeviceTimerStatus {
        data.requireTimerKeys(REQUIRED_KEYS, OPTIONAL_KEYS, "Timer status")
        val channels = parseChannels(data.requireTimerArray("channels"))
        val schedules = parseSchedules(data.requireTimerArray("schedules"))
        return DeviceTimerStatus(
            supported = data.requireTimerBoolean("supported"),
            channelCount = data.requireTimerInt(
                "channelCount",
                minimum = 1,
                maximum = DeviceTimerRuntimeContract.Limit.MAX_CHANNELS
            ),
            scheduleCount = data.requireTimerInt("scheduleCount", minimum = TIMER_MIN_COUNT),
            maxSchedulesPerChannel = data.requireTimerInt(
                "maxSchedulesPerChannel",
                minimum = DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES_PER_CHANNEL,
                maximum = DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES_PER_CHANNEL
            ),
            maxScheduleCount = data.requireTimerInt("maxScheduleCount", minimum = TIMER_MIN_COUNT),
            revision = data.requireTimerLong(
                "revision",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            lockLoop = data.requireTimerBoolean("lockLoop"),
            schema = data.requireTimerText("schema"),
            schemaVersion = data.requireTimerInt(
                "schemaVersion",
                DeviceTimerRuntimeContract.SCHEMA_VERSION,
                DeviceTimerRuntimeContract.SCHEMA_VERSION
            ),
            rootName = data.requireTimerText("rootName"),
            uptimeMs = data.requireTimerLong(
                "uptimeMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            channelScoped = data.requireTimerBoolean("channelScoped"),
            schedulesIncluded = data.requireTimerBoolean("schedulesIncluded"),
            selectedChannelKey = data.optionalTimerText("selectedChannelKey"),
            channels = channels,
            schedules = schedules,
            returnedScheduleCount = data.requireTimerInt(
                "returnedScheduleCount",
                TIMER_MIN_COUNT,
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES_PER_CHANNEL
            ),
            runtime = DeviceTimerRuntimeCapabilitiesParser.parse(
                data.requireTimerObject("runtime")
            )
        ).also(::validate)
    }

    private fun parseChannels(data: JSONArray): List<DeviceTimerChannelStatus> =
        List(data.length()) { index ->
            DeviceTimerChannelParser.parse(data.requireTimerObject(index))
        }

    private fun parseSchedules(data: JSONArray): List<DeviceTimerScheduleStatus> =
        List(data.length()) { index ->
            DeviceTimerScheduleParser.parse(data.requireTimerObject(index))
        }

    private fun validate(status: DeviceTimerStatus) {
        require(status.supported)
        require(status.schema == DeviceTimerRuntimeContract.SCHEMA)
        require(status.schemaVersion == DeviceTimerRuntimeContract.SCHEMA_VERSION)
        require(status.rootName == DeviceTimerRuntimeContract.Literal.STATUS_ROOT)
        require(status.maxSchedulesPerChannel ==
            DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES_PER_CHANNEL)
        require(status.maxScheduleCount == status.channelCount * status.maxSchedulesPerChannel)
        require(status.scheduleCount in TIMER_MIN_COUNT..status.maxScheduleCount)
        require(status.channels.map(DeviceTimerChannelStatus::key).distinct().size ==
            status.channels.size)
        require(status.channels.map(DeviceTimerChannelStatus::index).distinct().size ==
            status.channels.size)

        if (status.channelScoped) {
            val selectedChannelKey = requireNotNull(status.selectedChannelKey)
            require(status.schedulesIncluded)
            require(status.channels.size == 1)
            require(status.channels.single().key == selectedChannelKey)
            require(status.channels.single().scheduleCount == status.schedules.size)
            require(status.schedules.size <= status.scheduleCount)
            require(status.returnedScheduleCount == status.schedules.size)
            require(status.schedules.all { schedule ->
                schedule.channelKey == selectedChannelKey
            })
            require(status.schedules.map(DeviceTimerScheduleStatus::slotId).distinct().size ==
                status.schedules.size)
            require(status.schedules.map(DeviceTimerScheduleStatus::index).distinct().size ==
                status.schedules.size)
        } else {
            require(status.selectedChannelKey == null)
            require(!status.schedulesIncluded)
            require(status.channels.size == status.channelCount)
            require(status.channels.map(DeviceTimerChannelStatus::listIndex) ==
                status.channels.indices.toList())
            require(status.channels.sumOf { channel -> channel.scheduleCount } ==
                status.scheduleCount)
            require(status.schedules.isEmpty())
            require(status.returnedScheduleCount == 0)
        }
    }
}
