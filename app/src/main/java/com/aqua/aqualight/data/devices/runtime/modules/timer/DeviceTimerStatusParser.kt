package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import org.json.JSONArray
import org.json.JSONObject

object DeviceTimerStatusParser {

    fun parse(data: JSONObject): DeviceTimerStatus {
        data.requireExactKeys(STATUS_KEYS, "timer.status.get.data")

        val channels = parseStatusChannels(data.requiredArray("channels"))
        val schedules = parseStatusSchedules(data.requiredArray("schedules"))
        val channelCount = data.requiredNonNegativeInt("channelCount")
        val scheduleCount = data.requiredNonNegativeInt("scheduleCount")

        require(channelCount == channels.size) {
            "timer status channelCount differs from channels size."
        }
        require(scheduleCount == schedules.size) {
            "timer status scheduleCount differs from schedules size."
        }
        require(scheduleCount <= DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES)

        requireUnique(channels.map(DeviceTimerChannelStatus::index), "timer channel index")
        requireUnique(channels.map(DeviceTimerChannelStatus::key), "timer channel key")
        requireUnique(schedules.map(DeviceTimerScheduleStatus::index), "timer schedule index")

        val runtime = parseRuntime(data.requiredObject("runtime"))

        return DeviceTimerStatus(
            supported = data.requiredBoolean("supported"),
            channelCount = channelCount,
            scheduleCount = scheduleCount,
            lockLoop = data.requiredBoolean("lockLoop"),
            schema = data.requiredNonBlankString("schema").also {
                require(it == TIMER_SCHEMA)
            },
            rootName = data.requiredNonBlankString("rootName").also {
                require(it == DeviceTimerRuntimeContract.MODULE)
            },
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            channels = channels,
            schedules = schedules,
            runtime = runtime
        )
    }

    fun parseConfigApply(data: JSONObject): DeviceTimerConfigApplyResult {
        data.requireExactKeys(CONFIG_APPLY_KEYS, "timer.config.apply.data")

        val saveRequested = data.requiredBoolean("saveRequested")
        val saved = data.requiredBoolean("saved")
        require(saved == saveRequested)

        val appliedChannels = data.requiredBoolean("appliedChannels")
        val appliedSchedules = data.requiredBoolean("appliedSchedules")
        require(appliedChannels || appliedSchedules)

        return DeviceTimerConfigApplyResult(
            operation = data.requiredNonBlankString("operation").also {
                require(it == "configApply")
            },
            changed = data.requiredBoolean("changed"),
            saved = saved,
            saveRequested = saveRequested,
            runtimeTransport = data.requiredNonBlankString("runtimeTransport").also {
                require(it == RUNTIME_TRANSPORT)
            },
            command = data.requiredNonBlankString("command").also {
                require(it == TIMER_CONFIG_APPLY_COMMAND)
            },
            event = data.requiredNonBlankString("event").also(::requireStatusEvent),
            appliedChannels = appliedChannels,
            appliedSchedules = appliedSchedules,
            config = parseConfigSnapshot(data.requiredObject("config"))
        )
    }

    fun parseChannelSet(data: JSONObject): DeviceTimerChannelSetResult {
        data.requireExactKeys(CHANNEL_SET_KEYS, "timer.channel.set.data")

        val saveRequested = data.requiredBoolean("saveRequested")
        val saved = data.requiredBoolean("saved")
        require(saved == saveRequested)

        val channelKey = data.requiredNonBlankString("channelKey")
        val regime = DeviceTimerRegime.fromWire(data.requiredNonBlankString("regime"))
        val channelJson = data.requiredObject("channel")
        val channel = parseChannel(
            item = channelJson,
            label = "timer.channel.set.data.channel",
            expectedKeys = CHANNEL_SET_CHANNEL_KEYS
        )
        val listIndex = channelJson.requiredNonNegativeInt("listIndex")

        require(channel.key == channelKey)
        require(channel.regime == regime)

        return DeviceTimerChannelSetResult(
            operation = data.requiredNonBlankString("operation").also {
                require(it == "channelSet")
            },
            changed = data.requiredBoolean("changed"),
            saved = saved,
            saveRequested = saveRequested,
            channelKey = channelKey,
            regime = regime,
            runtimeTransport = data.requiredNonBlankString("runtimeTransport").also {
                require(it == RUNTIME_TRANSPORT)
            },
            command = data.requiredNonBlankString("command").also {
                require(it == TIMER_CHANNEL_SET_COMMAND)
            },
            event = data.requiredNonBlankString("event").also(::requireStatusEvent),
            channel = DeviceTimerChannelSetSnapshot(
                listIndex = listIndex,
                channel = channel
            )
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceTimerRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "timer status runtime")

        return DeviceTimerRuntimeCapabilities(
            module = runtime.requiredNonBlankString("module").also {
                require(it == DeviceTimerRuntimeContract.MODULE)
            },
            readOnly = runtime.requiredBoolean("readOnly").also { require(!it) },
            supportsConfigApply = runtime.requiredBoolean("supportsConfigApply")
                .also(::requireTrue),
            supportsChannelSet = runtime.requiredBoolean("supportsChannelSet")
                .also(::requireTrue),
            supportsSchedules = runtime.requiredBoolean("supportsSchedules")
                .also(::requireTrue),
            supportsChannels = runtime.requiredBoolean("supportsChannels")
                .also(::requireTrue),
            event = runtime.requiredNonBlankString("event").also(::requireStatusEvent)
        )
    }

    private fun parseStatusChannels(channels: JSONArray): List<DeviceTimerChannelStatus> =
        List(channels.length()) { index ->
            parseChannel(
                item = channels.requiredObject(index, "timer status channels"),
                label = "timer status channels[$index]",
                expectedKeys = CHANNEL_KEYS
            )
        }

    private fun parseChannel(
        item: JSONObject,
        label: String,
        expectedKeys: Set<String>
    ): DeviceTimerChannelStatus {
        item.requireExactKeys(expectedKeys, label)
        val editable = item.requiredObject("editable")
        editable.requireExactKeys(EDITABLE_KEYS, "$label.editable")

        return DeviceTimerChannelStatus(
            index = item.requiredNonNegativeInt("index"),
            key = item.requiredNonBlankString("key"),
            name = item.requiredStringAllowEmpty("name"),
            displayName = item.requiredStringAllowEmpty("displayName"),
            profileManaged = item.requiredBoolean("profileManaged"),
            regime = DeviceTimerRegime.fromWire(item.requiredNonBlankString("regime")),
            channelKind = item.requiredNonBlankString("channelKind").also {
                require(it in CHANNEL_KINDS)
            },
            gpio = item.requiredInt("gpio"),
            ledcChannel = item.requiredInt("ledcChannel"),
            group = item.requiredInt("group"),
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

    private fun parseStatusSchedules(
        schedules: JSONArray
    ): List<DeviceTimerScheduleStatus> = List(schedules.length()) { index ->
        val item = schedules.requiredObject(index, "timer status schedules")
        item.requireExactKeys(SCHEDULE_KEYS, "timer status schedules[$index]")

        DeviceTimerScheduleStatus(
            index = item.requiredNonNegativeInt("index"),
            enabled = item.requiredBoolean("enabled"),
            runtimeEnabled = item.requiredBoolean("runtimeEnabled"),
            name = item.requiredStringAllowEmpty("name"),
            channelKey = item.requiredStringAllowEmpty("channelKey"),
            bound = item.requiredBoolean("bound"),
            group = item.requiredInt("group"),
            weekdays = item.requiredWeekdays("weekdays"),
            startTimeMs = item.requiredLongInDay("startTimeMs"),
            startTime = item.requiredNonBlankString("startTime"),
            intervalOnMs = item.requiredNonNegativeLong("intervalOnMs"),
            intervalOn = item.requiredNonBlankString("intervalOn"),
            intervalOffMs = item.requiredNonNegativeLong("intervalOffMs"),
            intervalOff = item.requiredNonBlankString("intervalOff"),
            repeatCount = item.requiredNonNegativeInt("repeatCount"),
            pulseCountRuntime = item.requiredInt("pulseCountRuntime").also {
                require(it >= -1)
            },
            pulseOffPending = item.requiredBoolean("pulseOffPending"),
            pulseRemainingMs = item.requiredNonNegativeLong("pulseRemainingMs")
        )
    }

    private fun parseConfigSnapshot(data: JSONObject): DeviceTimerConfigSnapshot {
        data.requireExactKeys(CONFIG_SNAPSHOT_KEYS, "timer.config.apply.data.config")

        val channelsJson = data.requiredArray("channels")
        val schedulesJson = data.requiredArray("schedules")
        val channels = List(channelsJson.length()) { index ->
            parseConfigChannel(
                channelsJson.requiredObject(index, "timer config channels"),
                "timer config channels[$index]"
            )
        }
        val schedules = List(schedulesJson.length()) { index ->
            parseConfigSchedule(
                schedulesJson.requiredObject(index, "timer config schedules"),
                "timer config schedules[$index]"
            )
        }

        require(schedules.size <= DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES)
        requireUnique(
            channels.map(DeviceTimerChannelConfigSnapshot::channelKey),
            "timer config channel key"
        )

        return DeviceTimerConfigSnapshot(
            channels = channels,
            schedules = schedules
        )
    }

    private fun parseConfigChannel(
        item: JSONObject,
        label: String
    ): DeviceTimerChannelConfigSnapshot {
        item.requireRequiredAndAllowedKeys(
            required = CONFIG_CHANNEL_REQUIRED_KEYS,
            allowed = CONFIG_CHANNEL_ALLOWED_KEYS,
            label = label
        )

        return DeviceTimerChannelConfigSnapshot(
            channelKey = item.requiredNonBlankString("channelKey"),
            displayName = item.optionalCanonicalString("displayName"),
            regime = DeviceTimerRegime.fromWire(item.requiredNonBlankString("regime")),
            dosing = if (item.has("dosing")) {
                parseConfigDosing(item.requiredObject("dosing"), "$label.dosing")
            } else {
                null
            }
        )
    }

    private fun parseConfigDosing(
        data: JSONObject,
        label: String
    ): DeviceTimerChannelDosingConfigSnapshot {
        val actual = data.keys().asSequence().toSet()
        require(actual.isNotEmpty())
        require(actual.all { it in DOSING_ALLOWED_KEYS }) {
            "$label contains fields outside the firmware contract."
        }

        val hasCalibration = actual.any { it in DOSING_CALIBRATION_KEYS }
        val hasReservoir = actual.any { it in DOSING_RESERVOIR_KEYS }
        if (hasCalibration) {
            require(actual.containsAll(DOSING_CALIBRATION_KEYS))
        }
        if (hasReservoir) {
            require(actual.containsAll(DOSING_RESERVOIR_KEYS))
        }
        require(hasCalibration || hasReservoir)

        return DeviceTimerChannelDosingConfigSnapshot(
            doseMsPerMl = if (hasCalibration) data.requiredLong("doseMsPerMl") else null,
            lastCalibratedAt = if (hasCalibration) {
                data.requiredNonNegativeLong("lastCalibratedAt")
            } else {
                null
            },
            reservoirTrackingEnabled = if (hasReservoir) {
                data.requiredBoolean("reservoirTrackingEnabled")
            } else {
                null
            },
            reservoirCapacityMl = if (hasReservoir) {
                data.requiredFiniteDouble("reservoirCapacityMl")
            } else {
                null
            }
        )
    }

    private fun parseConfigSchedule(
        item: JSONObject,
        label: String
    ): DeviceTimerScheduleConfigSnapshot {
        item.requireExactKeys(CONFIG_SCHEDULE_KEYS, label)

        return DeviceTimerScheduleConfigSnapshot(
            enabled = item.requiredBoolean("enabled"),
            name = item.requiredStringAllowEmpty("name"),
            channelKey = item.requiredStringAllowEmpty("channelKey"),
            weekdays = item.requiredWeekdays("weekdays"),
            startTimeMs = item.requiredLongInDay("startTimeMs"),
            intervalOnMs = item.requiredNonNegativeLong("intervalOnMs"),
            intervalOffMs = item.requiredNonNegativeLong("intervalOffMs"),
            repeatCount = item.requiredNonNegativeInt("repeatCount"),
            amountMl = item.requiredFiniteDouble("amountMl")
        )
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "$label keys differ from firmware; expected=$expected actual=$actual"
        }
    }

    private fun JSONObject.requireRequiredAndAllowedKeys(
        required: Set<String>,
        allowed: Set<String>,
        label: String
    ) {
        val actual = keys().asSequence().toSet()
        require(actual.containsAll(required) && actual.all { it in allowed }) {
            "$label keys differ from firmware; required=$required allowed=$allowed actual=$actual"
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject =
        get(key) as? JSONObject ?: error("$key must be a JSON object.")

    private fun JSONObject.requiredArray(key: String): JSONArray =
        get(key) as? JSONArray ?: error("$key must be a JSON array.")

    private fun JSONArray.requiredObject(index: Int, label: String): JSONObject =
        get(index) as? JSONObject ?: error("$label[$index] must be a JSON object.")

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredInt(key: String): Int {
        val asLong = requiredLong(key)
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return asLong.toInt()
    }

    private fun JSONObject.requiredNonNegativeInt(key: String): Int =
        requiredInt(key).also { require(it >= 0) }

    private fun JSONObject.requiredLong(key: String): Long {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        return asLong
    }

    private fun JSONObject.requiredNonNegativeLong(key: String): Long =
        requiredLong(key).also { require(it >= 0L) }

    private fun JSONObject.requiredLongInDay(key: String): Long =
        requiredLong(key).also { require(it in 0L..86_399_999L) }

    private fun JSONObject.requiredFiniteDouble(key: String): Double {
        val number = get(key) as? Number ?: error("$key must be a number.")
        return number.toDouble().also { require(it.isFinite()) }
    }

    private fun JSONObject.requiredNonBlankString(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isNotEmpty())
        requireCanonicalString(value, key)
        return value
    }

    private fun JSONObject.requiredStringAllowEmpty(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        requireCanonicalString(value, key)
        return value
    }

    private fun JSONObject.optionalCanonicalString(key: String): String? =
        if (has(key)) requiredStringAllowEmpty(key) else null

    private fun JSONObject.requiredWeekdays(key: String): List<Boolean> {
        val weekdays = requiredArray(key)
        require(weekdays.length() == 7) {
            "$key must contain exactly 7 booleans."
        }
        return List(7) { index ->
            weekdays.get(index) as? Boolean
                ?: error("$key[$index] must be a boolean.")
        }
    }

    private fun requireCanonicalString(value: String, key: String) {
        require(value == value.trim()) {
            "$key must not contain surrounding whitespace."
        }
        require(value.none(Char::isISOControl)) {
            "$key must not contain control characters."
        }
    }

    private fun <T> requireUnique(values: List<T>, label: String) {
        require(values.toSet().size == values.size) {
            "$label values must be unique."
        }
    }

    private fun requireTrue(value: Boolean) {
        require(value)
    }

    private fun requireStatusEvent(value: String) {
        require(value == AqlWsContract.Event.STATUS_CHANGED)
    }

    private const val TIMER_SCHEMA = "aqualight.timer.v1"
    private const val RUNTIME_TRANSPORT = "websocket"
    private const val TIMER_CONFIG_APPLY_COMMAND = "timer.config.apply"
    private const val TIMER_CHANNEL_SET_COMMAND = "timer.channel.set"

    private val CHANNEL_KINDS = setOf("gpio", "digital", "none")

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
    private val CHANNEL_SET_CHANNEL_KEYS = CHANNEL_KEYS + "listIndex"
    private val EDITABLE_KEYS = setOf(
        "hardware", "displayName", "hardwareCalibration"
    )
    private val SCHEDULE_KEYS = setOf(
        "index", "enabled", "runtimeEnabled", "name", "channelKey", "bound", "group",
        "weekdays", "startTimeMs", "startTime", "intervalOnMs", "intervalOn",
        "intervalOffMs", "intervalOff", "repeatCount", "pulseCountRuntime",
        "pulseOffPending", "pulseRemainingMs"
    )

    private val CONFIG_APPLY_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "runtimeTransport", "command",
        "event", "appliedChannels", "appliedSchedules", "config"
    )
    private val CHANNEL_SET_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "channelKey", "regime",
        "runtimeTransport", "command", "event", "channel"
    )
    private val CONFIG_SNAPSHOT_KEYS = setOf("channels", "schedules")
    private val CONFIG_CHANNEL_REQUIRED_KEYS = setOf("channelKey", "regime")
    private val CONFIG_CHANNEL_ALLOWED_KEYS =
        CONFIG_CHANNEL_REQUIRED_KEYS + setOf("displayName", "dosing")
    private val DOSING_CALIBRATION_KEYS = setOf("doseMsPerMl", "lastCalibratedAt")
    private val DOSING_RESERVOIR_KEYS =
        setOf("reservoirTrackingEnabled", "reservoirCapacityMl")
    private val DOSING_ALLOWED_KEYS = DOSING_CALIBRATION_KEYS + DOSING_RESERVOIR_KEYS
    private val CONFIG_SCHEDULE_KEYS = setOf(
        "enabled", "name", "channelKey", "weekdays", "startTimeMs", "intervalOnMs",
        "intervalOffMs", "repeatCount", "amountMl"
    )
}
