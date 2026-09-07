package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONObject

internal object DeviceTimerMutationParser {
    private val CONFIG_RESULT_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "channelKey", "revision",
        "runtimeTransport", "command", "appliedDisplayName", "replacedSchedules", "channel"
    )
    private val CHANNEL_RESULT_KEYS = setOf(
        "operation", "changed", "persistentChanged", "temporaryOverrideCancelled", "saved",
        "saveRequested", "channelKey", "regime", "durationMs", "revision",
        "runtimeTransport", "command", "channel"
    )

    fun parseConfigApply(data: JSONObject): DeviceTimerConfigApplyResult {
        data.requireTimerKeys(CONFIG_RESULT_KEYS, "timer.config.apply result")
        return DeviceTimerConfigApplyResult(
            operation = data.requireTimerText("operation"),
            changed = data.requireTimerBoolean("changed"),
            saved = data.requireTimerBoolean("saved"),
            saveRequested = data.requireTimerBoolean("saveRequested"),
            channelKey = data.requireTimerText("channelKey"),
            revision = data.requireTimerLong(
                "revision",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            runtimeTransport = data.requireTimerText("runtimeTransport"),
            command = data.requireTimerText("command"),
            appliedDisplayName = data.requireTimerBoolean("appliedDisplayName"),
            replacedSchedules = data.requireTimerBoolean("replacedSchedules"),
            channel = DeviceTimerChannelParser.parse(data.requireTimerObject("channel"))
        ).also(::validateConfigResult)
    }

    fun parseChannelSet(data: JSONObject): DeviceTimerChannelSetResult {
        data.requireTimerKeys(CHANNEL_RESULT_KEYS, "timer.channel.set result")
        return DeviceTimerChannelSetResult(
            operation = data.requireTimerText("operation"),
            changed = data.requireTimerBoolean("changed"),
            persistentChanged = data.requireTimerBoolean("persistentChanged"),
            temporaryOverrideCancelled = data.requireTimerBoolean(
                "temporaryOverrideCancelled"
            ),
            saved = data.requireTimerBoolean("saved"),
            saveRequested = data.requireTimerBoolean("saveRequested"),
            channelKey = data.requireTimerText("channelKey"),
            regime = DeviceTimerRegimeParser.parse(data.requireTimerText("regime")),
            durationMs = data.requireTimerLong(
                "durationMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MAXIMUM_MS
            ),
            revision = data.requireTimerLong(
                "revision",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            runtimeTransport = data.requireTimerText("runtimeTransport"),
            command = data.requireTimerText("command"),
            channel = DeviceTimerChannelParser.parse(data.requireTimerObject("channel"))
        ).also(::validateChannelResult)
    }

    private fun validateConfigResult(result: DeviceTimerConfigApplyResult) {
        require(result.operation == DeviceTimerRuntimeContract.Literal.CONFIG_APPLY_OPERATION)
        require(result.saved == result.saveRequested)
        require(result.runtimeTransport == DeviceTimerRuntimeContract.Literal.RUNTIME_TRANSPORT)
        require(result.command == timerCommand(DeviceTimerRuntimeContract.Action.CONFIG_APPLY))
        require(result.channelKey == normalizeTimerChannelKey(result.channelKey))
        require(result.channelKey == result.channel.key)
    }

    private fun validateChannelResult(result: DeviceTimerChannelSetResult) {
        require(result.saved == result.saveRequested)
        require(result.runtimeTransport == DeviceTimerRuntimeContract.Literal.RUNTIME_TRANSPORT)
        require(result.command == timerCommand(DeviceTimerRuntimeContract.Action.CHANNEL_SET))
        require(result.channelKey == normalizeTimerChannelKey(result.channelKey))
        require(result.channelKey == result.channel.key)
        require(
            result.temporaryOverrideCancelled ==
                (result.durationMs == 0L && result.changed && !result.persistentChanged)
        )
        if (result.durationMs > 0L) {
            require(result.operation ==
                DeviceTimerRuntimeContract.Literal.TEMPORARY_OVERRIDE_OPERATION)
            require(result.durationMs >=
                DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MINIMUM_MS)
            require(!result.saveRequested)
            require(result.regime != DeviceTimerRegime.AUTO)
            require(result.changed)
            require(!result.persistentChanged)
            require(result.channel.temporaryOverrideActive)
        } else {
            require(result.operation == DeviceTimerRuntimeContract.Literal.CHANNEL_SET_OPERATION)
            require(result.changed ==
                (result.persistentChanged || result.temporaryOverrideCancelled))
            require(result.channel.regime == result.regime)
            require(!result.channel.temporaryOverrideActive)
        }
    }

    private fun timerCommand(action: String): String =
        "${DeviceTimerRuntimeContract.MODULE}.$action"
}
