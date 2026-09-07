package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceTimerRegimeParser {
    fun parse(value: String): DeviceTimerRegime = requireNotNull(
        DeviceTimerRegime.values().singleOrNull { regime -> regime.wireValue == value }
    ) { "Unknown firmware Timer regime: $value" }
}

internal object DeviceTimerOperatingStateParser {
    fun parse(value: String): DeviceTimerOperatingState = requireNotNull(
        DeviceTimerOperatingState.values().singleOrNull { state -> state.name == value }
    ) { "Unknown firmware Timer operatingState: $value" }
}

internal object DeviceTimerNextTransitionTypeParser {
    fun parse(value: String): DeviceTimerNextTransitionType = requireNotNull(
        DeviceTimerNextTransitionType.values().singleOrNull { type -> type.wireValue == value }
    ) { "Unknown firmware Timer nextTransitionType: $value" }
}

internal object DeviceTimerOutputHealthParser {
    fun parse(value: String): DeviceTimerOutputHealth = requireNotNull(
        DeviceTimerOutputHealth.values().singleOrNull { health -> health.wireValue == value }
    ) { "Unknown firmware Timer outputHealth: $value" }
}

internal object DeviceTimerRuntimeReasonParser {
    fun parse(value: String): DeviceTimerRuntimeReason = requireNotNull(
        DeviceTimerRuntimeReason.values().singleOrNull { reason -> reason.wireValue == value }
    ) { "Unknown firmware Timer runtimeReason: $value" }
}

internal object DeviceTimerRuntimeCapabilitiesParser {
    private val KEYS = setOf(
        "module",
        "readOnly",
        "supportsConfigApply",
        "supportsChannelSet",
        "supportsSchedules",
        "supportsChannels",
        "supportsSpansMidnight",
        "supportsTemporaryOverride",
        "supportsChannelScopedStatus",
        "supportsChannelScopedConfigApply",
        "configApplyScope",
        "event",
        "internalHeapMinimumFreeBytes",
        "internalHeapLargestFreeBlockBytes"
    )

    fun parse(data: JSONObject): DeviceTimerRuntimeCapabilities {
        data.requireTimerKeys(KEYS, "Timer runtime capabilities")
        return DeviceTimerRuntimeCapabilities(
            module = data.requireTimerText("module"),
            readOnly = data.requireTimerBoolean("readOnly"),
            supportsConfigApply = data.requireTimerBoolean("supportsConfigApply"),
            supportsChannelSet = data.requireTimerBoolean("supportsChannelSet"),
            supportsSchedules = data.requireTimerBoolean("supportsSchedules"),
            supportsChannels = data.requireTimerBoolean("supportsChannels"),
            supportsSpansMidnight = data.requireTimerBoolean("supportsSpansMidnight"),
            supportsTemporaryOverride = data.requireTimerBoolean("supportsTemporaryOverride"),
            supportsChannelScopedStatus = data.requireTimerBoolean(
                "supportsChannelScopedStatus"
            ),
            supportsChannelScopedConfigApply = data.requireTimerBoolean(
                "supportsChannelScopedConfigApply"
            ),
            configApplyScope = data.requireTimerText("configApplyScope"),
            event = data.requireTimerText("event"),
            internalHeapMinimumFreeBytes = data.requireTimerLong(
                "internalHeapMinimumFreeBytes",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            internalHeapLargestFreeBlockBytes = data.requireTimerLong(
                "internalHeapLargestFreeBlockBytes",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            )
        ).also(::validate)
    }

    private fun validate(runtime: DeviceTimerRuntimeCapabilities) {
        require(runtime.module == DeviceTimerRuntimeContract.MODULE)
        require(!runtime.readOnly)
        require(runtime.supportsConfigApply)
        require(runtime.supportsChannelSet)
        require(runtime.supportsSchedules)
        require(runtime.supportsChannels)
        require(runtime.supportsSpansMidnight)
        require(runtime.supportsTemporaryOverride)
        require(runtime.supportsChannelScopedStatus)
        require(runtime.supportsChannelScopedConfigApply)
        require(runtime.configApplyScope == DeviceTimerRuntimeContract.Literal.CONFIG_APPLY_SCOPE)
        require(runtime.event == DeviceTimerRuntimeContract.STATUS_EVENT)
    }
}

internal object DeviceTimerChannelParser {
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "hardwareCalibration")
    private val KEYS = setOf(
        "index", "listIndex", "key", "name", "displayName", "profileManaged", "regime",
        "channelKind", "gpio", "ledcChannel", "group", "valueNow", "valueAuto",
        "valueManual", "manualTimeoutMs", "invert", "pwmResolutionBits",
        "pwmFrequencyHz", "outputHealth", "physicalFeedbackAvailable", "scheduleCount",
        "operatingState", "activeSlotId", "activeSlotName", "nextTransitionType",
        "nextTransitionAt", "runtimeReason", "clockReady", "temporaryOverrideActive",
        "temporaryOverrideRemainingMs", "editable"
    )
    private val CHANNEL_KINDS = setOf(
        DeviceTimerRuntimeContract.Literal.CHANNEL_KIND_GPIO,
        DeviceTimerRuntimeContract.Literal.CHANNEL_KIND_DIGITAL,
        DeviceTimerRuntimeContract.Literal.CHANNEL_KIND_NONE
    )

    @Suppress("LongMethod")
    fun parse(data: JSONObject): DeviceTimerChannelStatus {
        data.requireTimerKeys(KEYS, "Timer channel status")
        return DeviceTimerChannelStatus(
            index = data.requireTimerInt("index", TIMER_MIN_INDEX),
            listIndex = data.requireTimerInt(
                "listIndex",
                TIMER_MIN_INDEX,
                DeviceTimerRuntimeContract.Limit.MAX_CHANNELS - 1
            ),
            key = data.requireTimerText("key"),
            name = data.requireTimerText("name"),
            displayName = data.requireTimerText("displayName"),
            profileManaged = data.requireTimerBoolean("profileManaged"),
            regime = DeviceTimerRegimeParser.parse(data.requireTimerText("regime")),
            channelKind = data.requireTimerText("channelKind"),
            gpio = data.requireTimerInt("gpio", TIMER_UNAVAILABLE_INDEX, Byte.MAX_VALUE.toInt()),
            ledcChannel = data.requireTimerInt(
                "ledcChannel",
                TIMER_UNAVAILABLE_INDEX,
                Byte.MAX_VALUE.toInt()
            ),
            group = data.requireTimerInt("group", Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()),
            valueNow = data.requireTimerDouble(
                "valueNow",
                TIMER_INACTIVE_VALUE,
                TIMER_NORMALIZED_MAX
            ),
            valueAuto = data.requireTimerDouble(
                "valueAuto",
                TIMER_NORMALIZED_MIN,
                TIMER_NORMALIZED_MAX
            ),
            valueManual = data.requireTimerDouble(
                "valueManual",
                TIMER_INACTIVE_VALUE,
                TIMER_NORMALIZED_MAX
            ),
            manualTimeoutMs = data.requireTimerLong(
                "manualTimeoutMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            invert = data.requireTimerBoolean("invert"),
            pwmResolutionBits = data.requireTimerInt("pwmResolutionBits", minimum = 0),
            pwmFrequencyHz = data.requireTimerInt("pwmFrequencyHz", minimum = 0),
            outputHealth = DeviceTimerOutputHealthParser.parse(
                data.requireTimerText("outputHealth")
            ),
            physicalFeedbackAvailable = data.requireTimerBoolean("physicalFeedbackAvailable"),
            scheduleCount = data.requireTimerInt(
                "scheduleCount",
                TIMER_MIN_COUNT,
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES_PER_CHANNEL
            ),
            operatingState = DeviceTimerOperatingStateParser.parse(
                data.requireTimerText("operatingState")
            ),
            activeSlotId = data.requireNullableTimerInt(
                "activeSlotId",
                DeviceTimerRuntimeContract.Limit.SLOT_ID_MINIMUM,
                DeviceTimerRuntimeContract.Limit.SLOT_ID_MAXIMUM
            ),
            activeSlotName = data.requireNullableTimerText("activeSlotName"),
            nextTransitionType = DeviceTimerNextTransitionTypeParser.parse(
                data.requireTimerText("nextTransitionType")
            ),
            nextTransitionAt = data.requireNullableTimerLong(
                "nextTransitionAt",
                minimum = TIMER_NON_NEGATIVE_LONG
            ),
            runtimeReason = DeviceTimerRuntimeReasonParser.parse(
                data.requireTimerText("runtimeReason")
            ),
            clockReady = data.requireTimerBoolean("clockReady"),
            temporaryOverrideActive = data.requireTimerBoolean("temporaryOverrideActive"),
            temporaryOverrideRemainingMs = data.requireTimerLong(
                "temporaryOverrideRemainingMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MAXIMUM_MS
            ),
            editable = parseEditable(data.requireTimerObject("editable"))
        ).also(::validate)
    }

    private fun parseEditable(data: JSONObject): DeviceTimerChannelEditable {
        data.requireTimerKeys(EDITABLE_KEYS, "Timer channel editable")
        return DeviceTimerChannelEditable(
            hardware = data.requireTimerBoolean("hardware"),
            displayName = data.requireTimerBoolean("displayName"),
            hardwareCalibration = data.requireTimerBoolean("hardwareCalibration")
        ).also { editable ->
            require(!editable.hardware)
            require(!editable.hardwareCalibration)
        }
    }

    private fun validate(channel: DeviceTimerChannelStatus) {
        require(channel.profileManaged)
        require(channel.key == normalizeTimerChannelKey(channel.key))
        require(channel.name.toByteArray(Charsets.UTF_8).size <=
            DeviceTimerRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES)
        require(channel.displayName.toByteArray(Charsets.UTF_8).size <=
            DeviceTimerRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES)
        require(channel.channelKind in CHANNEL_KINDS)
        require(!channel.physicalFeedbackAvailable)
        require((channel.activeSlotId == null) == (channel.activeSlotName == null))
        require((channel.nextTransitionType == DeviceTimerNextTransitionType.NONE) ==
            (channel.nextTransitionAt == null))
        require(channel.temporaryOverrideActive ==
            (channel.temporaryOverrideRemainingMs > 0L))
    }
}

internal object DeviceTimerScheduleParser {
    private val KEYS = setOf(
        "index", "slotId", "enabled", "name", "channelKey", "bound", "weekdays",
        "startTimeMs", "startTime", "endTimeMs", "endTime", "spansMidnight"
    )

    fun parse(data: JSONObject): DeviceTimerScheduleStatus {
        data.requireTimerKeys(KEYS, "Timer schedule status")
        return DeviceTimerScheduleStatus(
            index = data.requireTimerInt("index", TIMER_MIN_INDEX),
            slotId = data.requireTimerInt(
                "slotId",
                DeviceTimerRuntimeContract.Limit.SLOT_ID_MINIMUM,
                DeviceTimerRuntimeContract.Limit.SLOT_ID_MAXIMUM
            ),
            enabled = data.requireTimerBoolean("enabled"),
            name = data.requireTimerText("name"),
            channelKey = data.requireTimerText("channelKey"),
            bound = data.requireTimerBoolean("bound"),
            weekdays = parseWeekdays(data.requireTimerArray("weekdays")),
            startTimeMs = data.requireTimerLong(
                "startTimeMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY
            ),
            startTime = data.requireTimerText("startTime"),
            endTimeMs = data.requireTimerLong(
                "endTimeMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY
            ),
            endTime = data.requireTimerText("endTime"),
            spansMidnight = data.requireTimerBoolean("spansMidnight")
        ).also(::validate)
    }

    private fun parseWeekdays(data: JSONArray): List<Boolean> {
        require(data.length() == TIMER_WEEKDAY_COUNT) {
            "Timer weekdays must contain exactly $TIMER_WEEKDAY_COUNT booleans."
        }
        return List(data.length()) { index -> data.requireTimerBoolean(index) }
    }

    private fun validate(schedule: DeviceTimerScheduleStatus) {
        require(schedule.channelKey == normalizeTimerChannelKey(schedule.channelKey))
        require(schedule.bound)
        require(schedule.name.toByteArray(Charsets.UTF_8).size <=
            DeviceTimerRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES)
        require(!schedule.enabled || schedule.weekdays.any { selected -> selected })
        require(isTimerScheduleBoundary(schedule.startTimeMs))
        require(isTimerScheduleBoundary(schedule.endTimeMs))
        require(schedule.startTimeMs != schedule.endTimeMs)
        require(schedule.startTime == timerTimeText(schedule.startTimeMs))
        require(schedule.endTime == timerTimeText(schedule.endTimeMs))
        require(schedule.spansMidnight == (schedule.endTimeMs < schedule.startTimeMs))
    }
}
