package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceTimerRegime(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
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

data class DeviceTimerChannelStatusSnapshot(
    val listIndex: Int,
    val channel: DeviceTimerChannelStatus
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

data class DeviceTimerChannelConfigSnapshot(
    val listIndex: Int,
    val channelKey: String,
    val displayNameOverride: String?,
    val regime: DeviceTimerRegime
)

data class DeviceTimerScheduleConfigSnapshot(
    val listIndex: Int,
    val enabled: Boolean,
    val name: String,
    val channelKey: String,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val intervalOnMs: Long,
    val intervalOffMs: Long,
    val repeatCount: Int
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
    val channel: DeviceTimerChannelStatusSnapshot
)

data class DeviceTimerChannelConfig(
    val channelKey: String,
    val displayName: String? = null,
    val regime: DeviceTimerRegime? = null
) {
    val normalizedChannelKey: String = channelKey.trim().lowercase()
    val normalizedDisplayName: String? = displayName?.trim()

    init {
        require(normalizedChannelKey.isNotEmpty()) { "channelKey must not be blank." }
        require(normalizedChannelKey.none(Char::isISOControl)) {
            "channelKey must not contain control characters."
        }
        require(displayName != null || regime != null) {
            "A Timer channel config must change displayName and/or regime."
        }
        require(normalizedDisplayName?.none(Char::isISOControl) != false) {
            "displayName must not contain control characters."
        }
        require(
            normalizedDisplayName
                ?.toByteArray(Charsets.UTF_8)
                ?.size
                ?.let { size ->
                    size <= DeviceTimerRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES
                } ?: true
        ) {
            "displayName must be at most " +
                "${DeviceTimerRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES} UTF-8 bytes."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .also { data ->
            normalizedDisplayName?.let { name ->
                data.put(DeviceTimerRuntimeContract.Field.DISPLAY_NAME, name)
            }
            regime?.let { selected ->
                data.put(DeviceTimerRuntimeContract.Field.REGIME, selected.wireValue)
            }
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
    val normalizedName: String = name.trim()
    val normalizedChannelKey: String = channelKey.trim().lowercase()

    init {
        require(normalizedName.isNotEmpty()) { "Timer schedule name must not be blank." }
        require(normalizedName.none(Char::isISOControl)) {
            "Timer schedule name must not contain control characters."
        }
        require(
            normalizedName.toByteArray(Charsets.UTF_8).size <=
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES
        ) {
            "Timer schedule name must be at most " +
                "${DeviceTimerRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES} UTF-8 bytes."
        }
        require(normalizedChannelKey.isNotEmpty()) { "channelKey must not be blank." }
        require(normalizedChannelKey.none(Char::isISOControl)) {
            "channelKey must not contain control characters."
        }
        require(weekdays.size == TIMER_WEEKDAY_COUNT) {
            "Timer weekdays must contain exactly $TIMER_WEEKDAY_COUNT values."
        }
        require(startTimeMs in 0L..DeviceTimerRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY) {
            "startTimeMs must be inside one day."
        }
        require(intervalOnMs in TIMER_NON_NEGATIVE_LONG..TIMER_DEVICE_UPTIME_MAX_MS) {
            "intervalOnMs is outside the firmware unsigned-long range."
        }
        require(intervalOffMs in TIMER_NON_NEGATIVE_LONG..TIMER_DEVICE_UPTIME_MAX_MS) {
            "intervalOffMs is outside the firmware unsigned-long range."
        }
        require(intervalOnMs <= TIMER_DEVICE_UPTIME_MAX_MS - intervalOffMs) {
            "Timer interval sum exceeds the firmware unsigned-long range."
        }
        require(repeatCount >= 0) { "repeatCount must be zero or greater." }
        if (enabled) {
            require(weekdays.any { selected -> selected }) {
                "An enabled Timer schedule requires at least one weekday."
            }
            require(intervalOnMs > 0L) {
                "An enabled Timer schedule requires a positive on interval."
            }
            require(repeatCount > 0) {
                "An enabled Timer schedule requires a positive repeatCount."
            }
        }
        if (intervalOnMs + intervalOffMs > 0L) {
            val maximumRepeatCount =
                (TIMER_MILLISECONDS_PER_DAY + intervalOffMs) /
                    (intervalOnMs + intervalOffMs)
            require(repeatCount.toLong() <= maximumRepeatCount) {
                "repeatCount exceeds the firmware day-bound schedule capacity."
            }
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.ENABLED, enabled)
        .put(DeviceTimerRuntimeContract.Field.NAME, normalizedName)
        .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceTimerRuntimeContract.Field.WEEKDAYS, JSONArray(weekdays))
        .put(DeviceTimerRuntimeContract.Field.START_TIME_MS, startTimeMs)
        .put(DeviceTimerRuntimeContract.Field.INTERVAL_ON_MS, intervalOnMs)
        .put(DeviceTimerRuntimeContract.Field.INTERVAL_OFF_MS, intervalOffMs)
        .put(DeviceTimerRuntimeContract.Field.REPEAT_COUNT, repeatCount)
}

data class DeviceTimerConfigApplyPayload(
    val channels: List<DeviceTimerChannelConfig>? = null,
    val schedules: List<DeviceTimerScheduleConfig>? = null,
    val save: Boolean = true
) {
    init {
        require(channels != null || schedules != null) {
            "timer.config.apply requires channels and/or schedules."
        }
        require((schedules?.size ?: 0) <= DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES) {
            "Timer supports at most ${DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES} schedules."
        }
        require((channels?.size ?: 0) <= DeviceTimerRuntimeContract.Limit.MAX_CHANNELS) {
            "Timer channel request exceeds the WebSocket safety limit."
        }
        require(
            channels
                ?.map(DeviceTimerChannelConfig::normalizedChannelKey)
                ?.let { keys -> keys.distinct().size == keys.size } ?: true
        ) { "channelKey must be unique in the request." }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.SAVE, save)
        .also { data ->
            channels?.let { items ->
                data.put(
                    DeviceTimerRuntimeContract.Field.CHANNELS,
                    JSONArray().also { array -> items.forEach { item -> array.put(item.toJson()) } }
                )
            }
            schedules?.let { items ->
                data.put(
                    DeviceTimerRuntimeContract.Field.SCHEDULES,
                    JSONArray().also { array -> items.forEach { item -> array.put(item.toJson()) } }
                )
            }
        }
}

data class DeviceTimerChannelSetPayload(
    val channelKey: String,
    val regime: DeviceTimerRegime,
    val save: Boolean = true
) {
    val normalizedChannelKey: String = channelKey.trim().lowercase()

    init {
        require(normalizedChannelKey.isNotEmpty()) { "channelKey must not be blank." }
        require(normalizedChannelKey.none(Char::isISOControl)) {
            "channelKey must not contain control characters."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceTimerRuntimeContract.Field.REGIME, regime.wireValue)
        .put(DeviceTimerRuntimeContract.Field.SAVE, save)
}

internal fun DeviceTimerScheduleConfigSnapshot.toPayload(): DeviceTimerScheduleConfig =
    DeviceTimerScheduleConfig(
        enabled = enabled,
        name = name,
        channelKey = channelKey,
        weekdays = weekdays,
        startTimeMs = startTimeMs,
        intervalOnMs = intervalOnMs,
        intervalOffMs = intervalOffMs,
        repeatCount = repeatCount
    )
