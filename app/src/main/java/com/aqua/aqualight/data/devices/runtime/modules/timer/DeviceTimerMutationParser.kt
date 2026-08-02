package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceTimerMutationParser {
    private val CONFIG_RESULT_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "runtimeTransport", "command",
        "event", "appliedChannels", "appliedSchedules", "config"
    )
    private val CHANNEL_RESULT_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "channelKey", "regime",
        "runtimeTransport", "command", "event", "channel"
    )
    private val CONFIG_KEYS = setOf("channels", "schedules")

    fun parseConfigApply(data: JSONObject): DeviceTimerConfigApplyResult {
        data.requireTimerKeys(CONFIG_RESULT_KEYS, "timer.config.apply result")
        return DeviceTimerConfigApplyResult(
            operation = data.requireTimerText("operation"),
            changed = data.requireTimerBoolean("changed"),
            saved = data.requireTimerBoolean("saved"),
            saveRequested = data.requireTimerBoolean("saveRequested"),
            runtimeTransport = data.requireTimerText("runtimeTransport"),
            command = data.requireTimerText("command"),
            event = data.requireTimerText("event"),
            appliedChannels = data.requireTimerBoolean("appliedChannels"),
            appliedSchedules = data.requireTimerBoolean("appliedSchedules"),
            config = parseConfig(data.requireTimerObject("config"))
        ).also(::validateConfigResult)
    }

    fun parseChannelSet(data: JSONObject): DeviceTimerChannelSetResult {
        data.requireTimerKeys(CHANNEL_RESULT_KEYS, "timer.channel.set result")
        return DeviceTimerChannelSetResult(
            operation = data.requireTimerText("operation"),
            changed = data.requireTimerBoolean("changed"),
            saved = data.requireTimerBoolean("saved"),
            saveRequested = data.requireTimerBoolean("saveRequested"),
            channelKey = data.requireTimerText("channelKey"),
            regime = DeviceTimerRegimeParser.parse(data.requireTimerText("regime")),
            runtimeTransport = data.requireTimerText("runtimeTransport"),
            command = data.requireTimerText("command"),
            event = data.requireTimerText("event"),
            channel = DeviceTimerChannelParser.parseMutation(
                data.requireTimerObject("channel")
            )
        ).also(::validateChannelResult)
    }

    private fun parseConfig(data: JSONObject): DeviceTimerConfigSnapshot {
        data.requireTimerKeys(CONFIG_KEYS, "Timer config snapshot")
        val channels = parseConfigChannels(data.requireTimerArray("channels"))
        val schedules = parseConfigSchedules(data.requireTimerArray("schedules"))
        return DeviceTimerConfigSnapshot(channels, schedules).also(::validateConfig)
    }

    private fun parseConfigChannels(data: JSONArray): List<DeviceTimerChannelConfigSnapshot> {
        require(data.length() <= DeviceTimerRuntimeContract.Limit.MAX_CHANNELS)
        return List(data.length()) { index ->
            DeviceTimerConfigChannelParser.parse(data.requireTimerObject(index), index)
        }
    }

    private fun parseConfigSchedules(data: JSONArray): List<DeviceTimerScheduleConfigSnapshot> {
        require(data.length() <= DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES)
        return List(data.length()) { index ->
            DeviceTimerConfigScheduleParser.parse(data.requireTimerObject(index), index)
        }
    }

    private fun validateConfigResult(result: DeviceTimerConfigApplyResult) {
        require(result.operation == DeviceTimerRuntimeContract.Literal.CONFIG_APPLY_OPERATION)
        require(result.changed)
        require(result.saved == result.saveRequested) {
            "Firmware Timer persistence echo differs from saveRequested."
        }
        require(result.runtimeTransport == DeviceTimerRuntimeContract.Literal.RUNTIME_TRANSPORT)
        require(
            result.command ==
                "${DeviceTimerRuntimeContract.MODULE}." +
                DeviceTimerRuntimeContract.Action.CONFIG_APPLY
        )
        require(result.event == DeviceTimerRuntimeContract.STATUS_EVENT)
    }

    private fun validateChannelResult(result: DeviceTimerChannelSetResult) {
        require(result.operation == DeviceTimerRuntimeContract.Literal.CHANNEL_SET_OPERATION)
        require(result.saved == result.saveRequested) {
            "Firmware Timer persistence echo differs from saveRequested."
        }
        require(result.runtimeTransport == DeviceTimerRuntimeContract.Literal.RUNTIME_TRANSPORT)
        require(
            result.command ==
                "${DeviceTimerRuntimeContract.MODULE}." +
                DeviceTimerRuntimeContract.Action.CHANNEL_SET
        )
        require(result.event == DeviceTimerRuntimeContract.STATUS_EVENT)
        require(result.channelKey == result.channel.channel.key)
        require(result.regime == result.channel.channel.regime)
        require(result.channel.listIndex == result.channel.channel.index)
    }

    private fun validateConfig(config: DeviceTimerConfigSnapshot) {
        require(config.channels.isNotEmpty())
        require(config.channels.map(DeviceTimerChannelConfigSnapshot::listIndex) ==
            config.channels.indices.toList())
        require(config.schedules.map(DeviceTimerScheduleConfigSnapshot::listIndex) ==
            config.schedules.indices.toList())
        require(config.channels.map(DeviceTimerChannelConfigSnapshot::channelKey).distinct().size ==
            config.channels.size)
        val channelKeys = config.channels
            .mapTo(linkedSetOf(), DeviceTimerChannelConfigSnapshot::channelKey)
        require(config.schedules.all { schedule -> schedule.channelKey in channelKeys })
    }
}
