package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceTimerRegimeParser {
    fun parse(value: String): DeviceTimerRegime = requireNotNull(
        DeviceTimerRegime.values().singleOrNull { regime -> regime.wireValue == value }
    ) { "Unknown firmware Timer regime: $value" }
}

internal object DeviceTimerRuntimeCapabilitiesParser {
    private val KEYS = setOf(
        "module",
        "readOnly",
        "supportsConfigApply",
        "supportsChannelSet",
        "supportsSchedules",
        "supportsChannels",
        "event"
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
            event = data.requireTimerText("event")
        ).also { runtime ->
            require(runtime.module == DeviceTimerRuntimeContract.MODULE)
            require(!runtime.readOnly)
            require(runtime.supportsConfigApply)
            require(runtime.supportsChannelSet)
            require(runtime.supportsSchedules)
            require(runtime.supportsChannels)
            require(runtime.event == DeviceTimerRuntimeContract.STATUS_EVENT)
        }
    }
}

internal object DeviceTimerChannelParser {
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "hardwareCalibration")
    private val STATUS_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime",
        "channelKind", "gpio", "ledcChannel", "group", "valueNow", "valueAuto",
        "valueManual", "manualTimeoutMs", "invert", "pwmResolutionBits",
        "pwmFrequencyHz", "editable"
    )
    private val MUTATION_KEYS = STATUS_KEYS + "listIndex"
    private val CHANNEL_KINDS = setOf(
        DeviceTimerRuntimeContract.Literal.CHANNEL_KIND_GPIO,
        DeviceTimerRuntimeContract.Literal.CHANNEL_KIND_DIGITAL,
        DeviceTimerRuntimeContract.Literal.CHANNEL_KIND_NONE
    )

    fun parseStatus(data: JSONObject): DeviceTimerChannelStatus =
        parse(data, STATUS_KEYS, "Timer status channel")

    fun parseMutation(data: JSONObject): DeviceTimerChannelStatusSnapshot {
        data.requireTimerKeys(MUTATION_KEYS, "Timer channel mutation snapshot")
        return DeviceTimerChannelStatusSnapshot(
            listIndex = data.requireTimerInt(
                "listIndex",
                TIMER_MIN_INDEX,
                DeviceTimerRuntimeContract.Limit.MAX_CHANNELS - 1
            ),
            channel = parse(data, MUTATION_KEYS, "Timer channel mutation snapshot")
        )
    }

    private fun parse(
        data: JSONObject,
        expectedKeys: Set<String>,
        label: String
    ): DeviceTimerChannelStatus {
        data.requireTimerKeys(expectedKeys, label)
        return DeviceTimerChannelStatus(
            index = data.requireTimerInt(
                "index",
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
                TIMER_DEVICE_UPTIME_MAX_MS
            ),
            invert = data.requireTimerBoolean("invert"),
            pwmResolutionBits = data.requireTimerInt("pwmResolutionBits", minimum = 0),
            pwmFrequencyHz = data.requireTimerInt("pwmFrequencyHz", minimum = 0),
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
        require(channel.channelKind in CHANNEL_KINDS)
        if (channel.valueManual < TIMER_NORMALIZED_MIN) {
            require(channel.manualTimeoutMs == TIMER_NON_NEGATIVE_LONG)
        }
    }
}

internal object DeviceTimerScheduleParser {
    private val KEYS = setOf(
        "index", "enabled", "runtimeEnabled", "name", "channelKey", "bound", "group",
        "weekdays", "startTimeMs", "startTime", "intervalOnMs", "intervalOn",
        "intervalOffMs", "intervalOff", "repeatCount", "pulseCountRuntime",
        "pulseOffPending", "pulseRemainingMs"
    )

    fun parse(data: JSONObject): DeviceTimerScheduleStatus {
        data.requireTimerKeys(KEYS, "Timer status schedule")
        return DeviceTimerScheduleStatus(
            index = data.requireTimerInt(
                "index",
                TIMER_MIN_INDEX,
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES - 1
            ),
            enabled = data.requireTimerBoolean("enabled"),
            runtimeEnabled = data.requireTimerBoolean("runtimeEnabled"),
            name = data.requireTimerText("name"),
            channelKey = data.requireTimerText("channelKey"),
            bound = data.requireTimerBoolean("bound"),
            group = data.requireTimerInt("group", Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()),
            weekdays = parseWeekdays(data.requireTimerArray("weekdays")),
            startTimeMs = data.requireTimerLong(
                "startTimeMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY
            ),
            startTime = data.requireTimerText("startTime"),
            intervalOnMs = data.requireTimerLong(
                "intervalOnMs",
                TIMER_NON_NEGATIVE_LONG,
                TIMER_DEVICE_UPTIME_MAX_MS
            ),
            intervalOn = data.requireTimerText("intervalOn"),
            intervalOffMs = data.requireTimerLong(
                "intervalOffMs",
                TIMER_NON_NEGATIVE_LONG,
                TIMER_DEVICE_UPTIME_MAX_MS
            ),
            intervalOff = data.requireTimerText("intervalOff"),
            repeatCount = data.requireTimerInt("repeatCount", minimum = TIMER_MIN_COUNT),
            pulseCountRuntime = data.requireTimerInt(
                "pulseCountRuntime",
                minimum = TIMER_UNAVAILABLE_INDEX
            ),
            pulseOffPending = data.requireTimerBoolean("pulseOffPending"),
            pulseRemainingMs = data.requireTimerLong(
                "pulseRemainingMs",
                TIMER_NON_NEGATIVE_LONG,
                TIMER_DEVICE_UPTIME_MAX_MS
            )
        ).also(::validate)
    }

    private fun parseWeekdays(data: JSONArray): List<Boolean> {
        require(data.length() == TIMER_WEEKDAY_COUNT) {
            "Timer weekdays must contain exactly $TIMER_WEEKDAY_COUNT booleans."
        }
        return List(data.length()) { index -> data.requireTimerBoolean(index) }
    }

    private fun validate(schedule: DeviceTimerScheduleStatus) {
        require(schedule.startTime == timerTimeText(schedule.startTimeMs))
        require(schedule.intervalOn == timerTimeText(schedule.intervalOnMs))
        require(schedule.intervalOff == timerTimeText(schedule.intervalOffMs))
        require(schedule.pulseOffPending == (schedule.pulseRemainingMs > 0L))
        val expectedRuntimeEnabled = schedule.enabled &&
            schedule.bound &&
            schedule.weekdays.any { selected -> selected } &&
            schedule.intervalOnMs > 0L &&
            schedule.repeatCount > 0
        require(schedule.runtimeEnabled == expectedRuntimeEnabled)
    }
}

internal object DeviceTimerConfigChannelParser {
    private val REQUIRED_KEYS = setOf("channelKey", "regime")
    private val OPTIONAL_KEYS = setOf("displayName")

    fun parse(data: JSONObject, listIndex: Int): DeviceTimerChannelConfigSnapshot {
        data.requireTimerKeys(REQUIRED_KEYS, OPTIONAL_KEYS, "Timer config channel")
        return DeviceTimerChannelConfigSnapshot(
            listIndex = listIndex,
            channelKey = data.requireTimerText("channelKey"),
            displayNameOverride = data.optionalTimerText("displayName"),
            regime = DeviceTimerRegimeParser.parse(data.requireTimerText("regime"))
        )
    }
}

internal object DeviceTimerConfigScheduleParser {
    private val KEYS = setOf(
        "enabled", "name", "channelKey", "weekdays", "startTimeMs", "intervalOnMs",
        "intervalOffMs", "repeatCount", "amountMl"
    )

    fun parse(data: JSONObject, listIndex: Int): DeviceTimerScheduleConfigSnapshot {
        data.requireTimerKeys(KEYS, "Timer config schedule")
        val config = DeviceTimerScheduleConfig(
            enabled = data.requireTimerBoolean("enabled"),
            name = data.requireTimerText("name"),
            channelKey = data.requireTimerText("channelKey"),
            weekdays = parseWeekdays(data.requireTimerArray("weekdays")),
            startTimeMs = data.requireTimerLong(
                "startTimeMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY
            ),
            intervalOnMs = data.requireTimerLong(
                "intervalOnMs",
                TIMER_NON_NEGATIVE_LONG,
                TIMER_DEVICE_UPTIME_MAX_MS
            ),
            intervalOffMs = data.requireTimerLong(
                "intervalOffMs",
                TIMER_NON_NEGATIVE_LONG,
                TIMER_DEVICE_UPTIME_MAX_MS
            ),
            repeatCount = data.requireTimerInt("repeatCount", minimum = TIMER_MIN_COUNT)
        )
        val amountMl = data.requireTimerDouble("amountMl")
        require(timerValuesEquivalent(amountMl, TIMER_STANDALONE_AMOUNT_ML)) {
            "Standalone Timer config must not expose a dosing amount."
        }
        return DeviceTimerScheduleConfigSnapshot(
            listIndex = listIndex,
            enabled = config.enabled,
            name = config.normalizedName,
            channelKey = config.normalizedChannelKey,
            weekdays = config.weekdays.toList(),
            startTimeMs = config.startTimeMs,
            intervalOnMs = config.intervalOnMs,
            intervalOffMs = config.intervalOffMs,
            repeatCount = config.repeatCount
        )
    }

    private fun parseWeekdays(data: JSONArray): List<Boolean> {
        require(data.length() == TIMER_WEEKDAY_COUNT) {
            "Timer weekdays must contain exactly $TIMER_WEEKDAY_COUNT booleans."
        }
        return List(data.length()) { index -> data.requireTimerBoolean(index) }
    }
}
