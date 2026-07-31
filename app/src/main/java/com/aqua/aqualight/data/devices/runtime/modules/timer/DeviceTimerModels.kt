package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceTimerRegime(
    val wireValue: String
) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off");

    companion object {
        fun fromWire(value: String): DeviceTimerRegime {
            return values().singleOrNull { it.wireValue == value }
                ?: error("Unknown firmware timer regime: $value")
        }
    }
}

data class DeviceTimerRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsConfigApply: Boolean,
    val supportsChannelSet: Boolean,
    val supportsSchedules: Boolean,
    val supportsChannels: Boolean,
    val event: String
)

data class DeviceTimerChannelEditable(
    val hardware: Boolean,
    val displayName: Boolean,
    val hardwareCalibration: Boolean
)

data class DeviceTimerChannelStatus(
    val index: Int,
    val key: String,
    val name: String,
    val displayName: String,
    val profileManaged: Boolean,
    val regime: DeviceTimerRegime,
    val channelKind: String,
    val gpio: Int,
    val ledcChannel: Int,
    val group: Int,
    val valueNow: Double,
    val valueAuto: Double,
    val valueManual: Double,
    val manualTimeoutMs: Long,
    val invert: Boolean,
    val pwmResolutionBits: Int,
    val pwmFrequencyHz: Int,
    val editable: DeviceTimerChannelEditable
)

data class DeviceTimerScheduleStatus(
    val index: Int,
    val enabled: Boolean,
    val runtimeEnabled: Boolean,
    val name: String,
    val channelKey: String,
    val bound: Boolean,
    val group: Int,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val startTime: String,
    val intervalOnMs: Long,
    val intervalOn: String,
    val intervalOffMs: Long,
    val intervalOff: String,
    val repeatCount: Int,
    val pulseCountRuntime: Int,
    val pulseOffPending: Boolean,
    val pulseRemainingMs: Long
)

data class DeviceTimerStatus(
    val supported: Boolean,
    val channelCount: Int,
    val scheduleCount: Int,
    val lockLoop: Boolean,
    val schema: String,
    val rootName: String,
    val uptimeMs: Long,
    val channels: List<DeviceTimerChannelStatus>,
    val schedules: List<DeviceTimerScheduleStatus>,
    val runtime: DeviceTimerRuntimeCapabilities
)

data class DeviceTimerChannelConfig(
    val channelKey: String,
    val displayName: String? = null,
    val regime: DeviceTimerRegime? = null
) {
    init {
        requireTimerChannelKey(channelKey)
        displayName?.let {
            requireCanonicalTimerText(it, "displayName", allowEmpty = true)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, channelKey)

        if (displayName != null) {
            json.put(DeviceTimerRuntimeContract.Field.DISPLAY_NAME, displayName)
        }

        if (regime != null) {
            json.put(DeviceTimerRuntimeContract.Field.REGIME, regime.wireValue)
        }

        return json
    }
}

data class DeviceTimerScheduleConfig(
    val enabled: Boolean,
    val name: String,
    val channelKey: String,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val intervalOnMs: Long,
    val intervalOffMs: Long,
    val repeatCount: Int
) {
    init {
        requireCanonicalTimerText(name, "name", allowEmpty = true)
        requireCanonicalTimerText(channelKey, "channelKey", allowEmpty = true)
        require(weekdays.size == 7) { "Timer weekdays must contain exactly 7 values." }
        require(startTimeMs in 0L..86_399_999L) { "startTimeMs must be inside one day." }
        require(intervalOnMs >= 0L) { "intervalOnMs must be zero or greater." }
        require(intervalOffMs >= 0L) { "intervalOffMs must be zero or greater." }
        require(repeatCount >= 0) { "repeatCount must be zero or greater." }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceTimerRuntimeContract.Field.ENABLED, enabled)
            .put(DeviceTimerRuntimeContract.Field.NAME, name)
            .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceTimerRuntimeContract.Field.WEEKDAYS, JSONArray(weekdays))
            .put(DeviceTimerRuntimeContract.Field.START_TIME_MS, startTimeMs)
            .put(DeviceTimerRuntimeContract.Field.INTERVAL_ON_MS, intervalOnMs)
            .put(DeviceTimerRuntimeContract.Field.INTERVAL_OFF_MS, intervalOffMs)
            .put(DeviceTimerRuntimeContract.Field.REPEAT_COUNT, repeatCount)
    }
}

data class DeviceTimerConfigApplyPayload(
    val channels: List<DeviceTimerChannelConfig> = emptyList(),
    val schedules: List<DeviceTimerScheduleConfig> = emptyList(),
    val save: Boolean = true
) {
    init {
        require(channels.isNotEmpty() || schedules.isNotEmpty()) {
            "timer.config.apply requires channels and/or schedules."
        }
        require(schedules.size <= DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES) {
            "Timer supports at most ${DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES} schedules."
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceTimerRuntimeContract.Field.SAVE, save)

        if (channels.isNotEmpty()) {
            json.put(
                DeviceTimerRuntimeContract.Field.CHANNELS,
                JSONArray(channels.map { it.toJson() })
            )
        }

        if (schedules.isNotEmpty()) {
            json.put(
                DeviceTimerRuntimeContract.Field.SCHEDULES,
                JSONArray(schedules.map { it.toJson() })
            )
        }

        return json
    }
}

data class DeviceTimerChannelSetPayload(
    val channelKey: String,
    val regime: DeviceTimerRegime,
    val save: Boolean = true
) {
    init {
        requireTimerChannelKey(channelKey)
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceTimerRuntimeContract.Field.REGIME, regime.wireValue)
            .put(DeviceTimerRuntimeContract.Field.SAVE, save)
    }
}

data class DeviceTimerChannelDosingConfigSnapshot(
    val doseMsPerMl: Long?,
    val lastCalibratedAt: Long?,
    val reservoirTrackingEnabled: Boolean?,
    val reservoirCapacityMl: Double?
)

data class DeviceTimerChannelConfigSnapshot(
    val channelKey: String,
    val displayName: String?,
    val regime: DeviceTimerRegime,
    val dosing: DeviceTimerChannelDosingConfigSnapshot?
)

data class DeviceTimerScheduleConfigSnapshot(
    val enabled: Boolean,
    val name: String,
    val channelKey: String,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val intervalOnMs: Long,
    val intervalOffMs: Long,
    val repeatCount: Int,
    val amountMl: Double
)

data class DeviceTimerConfigSnapshot(
    val channels: List<DeviceTimerChannelConfigSnapshot>,
    val schedules: List<DeviceTimerScheduleConfigSnapshot>
)

data class DeviceTimerConfigApplyResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val appliedChannels: Boolean,
    val appliedSchedules: Boolean,
    val config: DeviceTimerConfigSnapshot
)

data class DeviceTimerChannelSetSnapshot(
    val listIndex: Int,
    val channel: DeviceTimerChannelStatus
)

data class DeviceTimerChannelSetResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val channelKey: String,
    val regime: DeviceTimerRegime,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceTimerChannelSetSnapshot
)

private fun requireTimerChannelKey(value: String) {
    requireCanonicalTimerText(value, "channelKey", allowEmpty = false)
    require(value != "-" && !value.equals("none", ignoreCase = true)) {
        "channelKey must target a configured timer channel."
    }
}

private fun requireCanonicalTimerText(
    value: String,
    field: String,
    allowEmpty: Boolean
) {
    require(allowEmpty || value.isNotEmpty()) { "$field must not be empty." }
    require(value == value.trim()) { "$field must not contain surrounding whitespace." }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
}
