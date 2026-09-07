@file:Suppress("LongParameterList", "MagicNumber")

package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceTimerRegime(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

enum class DeviceTimerOperatingState {
    ON,
    OFF
}

enum class DeviceTimerNextTransitionType(val wireValue: String) {
    NONE("NONE"),
    ON("ON"),
    OFF("OFF")
}

enum class DeviceTimerOutputHealth(val wireValue: String) {
    UNVERIFIED("UNVERIFIED"),
    HARDWARE_FAULT("HARDWARE_FAULT")
}

enum class DeviceTimerRuntimeReason(val wireValue: String) {
    NOT_EVALUATED("notEvaluated"),
    HARDWARE_FAULT("hardwareFault"),
    TEMPORARY_OVERRIDE_ON("temporaryOverrideOn"),
    TEMPORARY_OVERRIDE_OFF("temporaryOverrideOff"),
    MANUAL_ON("manualOn"),
    MANUAL_OFF("manualOff"),
    CLOCK_UNAVAILABLE("clockUnavailable"),
    NO_ENABLED_SCHEDULES("noEnabledSchedules"),
    SCHEDULE_ACTIVE("scheduleActive"),
    OUTSIDE_SCHEDULE("outsideSchedule")
}

data class DeviceTimerRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsConfigApply: Boolean,
    val supportsChannelSet: Boolean,
    val supportsSchedules: Boolean,
    val supportsChannels: Boolean,
    val supportsSpansMidnight: Boolean,
    val supportsTemporaryOverride: Boolean,
    val supportsChannelScopedStatus: Boolean,
    val supportsChannelScopedConfigApply: Boolean,
    val configApplyScope: String,
    val event: String,
    val internalHeapMinimumFreeBytes: Long,
    val internalHeapLargestFreeBlockBytes: Long
)

data class DeviceTimerChannelEditable(
    val hardware: Boolean,
    val displayName: Boolean,
    val hardwareCalibration: Boolean
)

@Suppress("LongParameterList")
data class DeviceTimerChannelStatus(
    val index: Int,
    val listIndex: Int,
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
    val outputHealth: DeviceTimerOutputHealth,
    val physicalFeedbackAvailable: Boolean,
    val scheduleCount: Int,
    val operatingState: DeviceTimerOperatingState,
    val activeSlotId: Int?,
    val activeSlotName: String?,
    val nextTransitionType: DeviceTimerNextTransitionType,
    val nextTransitionAt: Long?,
    val runtimeReason: DeviceTimerRuntimeReason,
    val clockReady: Boolean,
    val temporaryOverrideActive: Boolean,
    val temporaryOverrideRemainingMs: Long,
    val editable: DeviceTimerChannelEditable
)

data class DeviceTimerScheduleStatus(
    val index: Int,
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val channelKey: String,
    val bound: Boolean,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val startTime: String,
    val endTimeMs: Long,
    val endTime: String,
    val spansMidnight: Boolean
)

@Suppress("LongParameterList")
data class DeviceTimerStatus(
    val supported: Boolean,
    val channelCount: Int,
    val scheduleCount: Int,
    val maxSchedulesPerChannel: Int,
    val maxScheduleCount: Int,
    val revision: Long,
    val lockLoop: Boolean,
    val schema: String,
    val schemaVersion: Int,
    val rootName: String,
    val uptimeMs: Long,
    val channelScoped: Boolean,
    val schedulesIncluded: Boolean,
    val selectedChannelKey: String?,
    val channels: List<DeviceTimerChannelStatus>,
    val schedules: List<DeviceTimerScheduleStatus>,
    val returnedScheduleCount: Int,
    val runtime: DeviceTimerRuntimeCapabilities
)

data class DeviceTimerStatusGetPayload(
    val channelKey: String? = null
) {
    val normalizedChannelKey: String? = channelKey?.let(::normalizeTimerChannelKey)

    internal fun toJson(): JSONObject = JSONObject().also { data ->
        normalizedChannelKey?.let { data.put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, it) }
    }
}

sealed interface DeviceTimerDisplayNameUpdate {
    data object Omitted : DeviceTimerDisplayNameUpdate
    data object Clear : DeviceTimerDisplayNameUpdate

    data class Value(val displayName: String) : DeviceTimerDisplayNameUpdate {
        val normalizedDisplayName: String = displayName.trimTimerAsciiWhitespace()

        init {
            require(normalizedDisplayName.isNotEmpty()) {
                "Use DeviceTimerDisplayNameUpdate.Clear for an empty display name."
            }
            require(!normalizedDisplayName.hasTimerForbiddenControlBytes()) {
                "displayName must not contain control characters."
            }
            require(
                normalizedDisplayName.toByteArray(Charsets.UTF_8).size <=
                    DeviceTimerRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES
            ) { "displayName exceeds the firmware byte limit." }
        }
    }
}

data class DeviceTimerScheduleConfig(
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val spansMidnight: Boolean = endTimeMs < startTimeMs
) {
    val normalizedName: String = name.trimTimerAsciiWhitespace()

    init {
        require(slotId in DeviceTimerRuntimeContract.Limit.SLOT_ID_MINIMUM..
            DeviceTimerRuntimeContract.Limit.SLOT_ID_MAXIMUM) {
            "slotId must be inside the firmware Timer slot range."
        }
        require(normalizedName.isNotEmpty()) { "Timer schedule name must not be blank." }
        require(!normalizedName.hasTimerForbiddenControlBytes()) {
            "Timer schedule name must not contain control characters."
        }
        require(
            normalizedName.toByteArray(Charsets.UTF_8).size <=
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES
        ) { "Timer schedule name exceeds the firmware byte limit." }
        require(weekdays.size == TIMER_WEEKDAY_COUNT) {
            "Timer weekdays must contain exactly $TIMER_WEEKDAY_COUNT values."
        }
        require(!enabled || weekdays.any { selected -> selected }) {
            "An enabled Timer schedule requires at least one weekday."
        }
        require(isTimerScheduleBoundary(startTimeMs)) {
            "startTimeMs must be milliseconds from local midnight on a whole-minute boundary."
        }
        require(isTimerScheduleBoundary(endTimeMs)) {
            "endTimeMs must be milliseconds from local midnight on a whole-minute boundary."
        }
        require(startTimeMs != endTimeMs) { "Timer schedule boundaries must differ." }
        require(spansMidnight == (endTimeMs < startTimeMs)) {
            "spansMidnight must equal endTimeMs < startTimeMs."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.SLOT_ID, slotId)
        .put(DeviceTimerRuntimeContract.Field.ENABLED, enabled)
        .put(DeviceTimerRuntimeContract.Field.NAME, normalizedName)
        .put(DeviceTimerRuntimeContract.Field.WEEKDAYS, JSONArray(weekdays))
        .put(DeviceTimerRuntimeContract.Field.START_TIME_MS, startTimeMs)
        .put(DeviceTimerRuntimeContract.Field.END_TIME_MS, endTimeMs)
        .put(DeviceTimerRuntimeContract.Field.SPANS_MIDNIGHT, spansMidnight)
}

data class DeviceTimerConfigApplyPayload(
    val channelKey: String,
    val expectedRevision: Long,
    val displayName: DeviceTimerDisplayNameUpdate = DeviceTimerDisplayNameUpdate.Omitted,
    val schedules: List<DeviceTimerScheduleConfig>? = null,
    val save: Boolean = true
) {
    val normalizedChannelKey: String = normalizeTimerChannelKey(channelKey)

    init {
        require(expectedRevision in TIMER_NON_NEGATIVE_LONG..
            DeviceTimerRuntimeContract.Limit.UINT32_MAX) {
            "expectedRevision is outside the firmware unsigned integer range."
        }
        require(displayName != DeviceTimerDisplayNameUpdate.Omitted || schedules != null) {
            "timer.config.apply requires displayName and/or schedules."
        }
        require((schedules?.size ?: 0) <=
            DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES_PER_CHANNEL) {
            "Timer supports at most 8 schedules per channel."
        }
        schedules?.validateTimerScheduleReplacement()
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceTimerRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceTimerRuntimeContract.Field.SAVE, save)
        .also { data ->
            when (val update = displayName) {
                DeviceTimerDisplayNameUpdate.Omitted -> Unit
                DeviceTimerDisplayNameUpdate.Clear -> data.put(
                    DeviceTimerRuntimeContract.Field.DISPLAY_NAME,
                    JSONObject.NULL
                )
                is DeviceTimerDisplayNameUpdate.Value -> data.put(
                    DeviceTimerRuntimeContract.Field.DISPLAY_NAME,
                    update.normalizedDisplayName
                )
            }
            schedules?.let { items ->
                data.put(
                    DeviceTimerRuntimeContract.Field.SCHEDULES,
                    JSONArray().also { array -> items.forEach { array.put(it.toJson()) } }
                )
            }
        }
        .also(::requireTimerMutationBudget)
}

data class DeviceTimerChannelSetPayload(
    val channelKey: String,
    val expectedRevision: Long,
    val regime: DeviceTimerRegime,
    val durationMs: Long? = null,
    val save: Boolean = true
) {
    val normalizedChannelKey: String = normalizeTimerChannelKey(channelKey)

    init {
        require(expectedRevision in TIMER_NON_NEGATIVE_LONG..
            DeviceTimerRuntimeContract.Limit.UINT32_MAX) {
            "expectedRevision is outside the firmware unsigned integer range."
        }
        durationMs?.let { duration ->
            require(duration in DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MINIMUM_MS..
                DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MAXIMUM_MS) {
                "Temporary Timer override duration is outside 1..86400000 ms."
            }
            require(regime != DeviceTimerRegime.AUTO) {
                "Temporary Timer overrides support only On or Off."
            }
            require(!save) { "Temporary Timer overrides require save=false." }
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceTimerRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceTimerRuntimeContract.Field.REGIME, regime.wireValue)
        .put(DeviceTimerRuntimeContract.Field.SAVE, save)
        .also { data ->
            durationMs?.let { data.put(DeviceTimerRuntimeContract.Field.DURATION_MS, it) }
        }
        .also(::requireTimerMutationBudget)
}

data class DeviceTimerConfigApplyResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val channelKey: String,
    val revision: Long,
    val runtimeTransport: String,
    val command: String,
    val appliedDisplayName: Boolean,
    val replacedSchedules: Boolean,
    val channel: DeviceTimerChannelStatus
)

data class DeviceTimerChannelSetResult(
    val operation: String,
    val changed: Boolean,
    val persistentChanged: Boolean,
    val temporaryOverrideCancelled: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val channelKey: String,
    val regime: DeviceTimerRegime,
    val durationMs: Long,
    val revision: Long,
    val runtimeTransport: String,
    val command: String,
    val channel: DeviceTimerChannelStatus
)

data class DeviceTimerStatusChange(
    val sequence: Long,
    val occurredAtMs: Long,
    val operatingState: DeviceTimerOperatingState,
    val activeSlotId: Int?,
    val activeSlotName: String?,
    val nextTransitionType: DeviceTimerNextTransitionType,
    val nextTransitionAt: Long?,
    val runtimeReason: DeviceTimerRuntimeReason,
    val clockReady: Boolean,
    val temporaryOverrideActive: Boolean,
    val temporaryOverrideRemainingMs: Long
)

data class DeviceTimerStatusChangedEvent(
    val schema: String,
    val schemaVersion: Int,
    val channelKey: String,
    val revision: Long,
    val publishedAtMs: Long,
    val change: DeviceTimerStatusChange
)

private fun List<DeviceTimerScheduleConfig>.validateTimerScheduleReplacement() {
    require(map(DeviceTimerScheduleConfig::slotId).distinct().size == size) {
        "Timer slotId must be unique inside one channel replacement."
    }
    for (firstIndex in indices) {
        for (secondIndex in firstIndex + 1 until size) {
            require(!this[firstIndex].overlaps(this[secondIndex])) {
                "Timer schedules must not overlap across the local week."
            }
        }
    }
}

@Suppress("NestedBlockDepth")
private fun DeviceTimerScheduleConfig.overlaps(other: DeviceTimerScheduleConfig): Boolean {
    val firstDuration = if (endTimeMs > startTimeMs) {
        endTimeMs - startTimeMs
    } else {
        DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS - startTimeMs + endTimeMs
    }
    val secondDuration = if (other.endTimeMs > other.startTimeMs) {
        other.endTimeMs - other.startTimeMs
    } else {
        DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS - other.startTimeMs + other.endTimeMs
    }
    for (firstDay in weekdays.indices) {
        if (!weekdays[firstDay]) continue
        val firstStart = firstDay * DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS + startTimeMs
        val firstEnd = firstStart + firstDuration
        for (secondDay in other.weekdays.indices) {
            if (!other.weekdays[secondDay]) continue
            val secondBase =
                secondDay * DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS + other.startTimeMs
            for (weekOffset in -1..1) {
                val secondStart = secondBase +
                    weekOffset * TIMER_WEEK_MILLISECONDS
                val secondEnd = secondStart + secondDuration
                if (firstStart < secondEnd && secondStart < firstEnd) return true
            }
        }
    }
    return false
}

private fun requireTimerMutationBudget(data: JSONObject) {
    val decodedBytes = data.toString().toByteArray(Charsets.UTF_8).size
    require(decodedBytes <= DeviceTimerRuntimeContract.Limit.DECODED_DATA_MAXIMUM_BYTES) {
        "Timer mutation exceeds the decoded WebSocket data budget."
    }
    val encodedCharacters = 4 * ((decodedBytes + 2) / 3)
    require(encodedCharacters <=
        DeviceTimerRuntimeContract.Limit.ENCODED_DATA_MAXIMUM_CHARACTERS) {
        "Timer mutation exceeds the encoded WebSocket data budget."
    }
    require(timerJsonKeyCount(data) <= DeviceTimerRuntimeContract.Limit.JSON_KEY_MAXIMUM) {
        "Timer mutation exceeds the WebSocket JSON key budget."
    }
}

private fun timerJsonKeyCount(value: Any): Int = when (value) {
    is JSONObject -> value.keys().asSequence().sumOf { key ->
        1 + timerJsonKeyCount(value.get(key))
    }
    is JSONArray -> (0 until value.length()).sumOf { index -> timerJsonKeyCount(value.get(index)) }
    else -> 0
}

private const val TIMER_WEEK_MILLISECONDS = 7L * 86_400_000L
