package com.aqua.aqualight.data.devices.runtime.modules.light

import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

object DeviceLightStatusParser {

    fun parse(data: JSONObject): DeviceLightStatus {
        data.requireExactKeys(STATUS_KEYS, "light.status.get.data")
        val channels = parseStatusChannels(data.requiredArray("channels"))
        val programs = parseStatusPrograms(data.requiredArray("programs"), channels)
        val runtime = parseRuntime(data.requiredObject("runtime"))
        val channelCount = data.requiredNonNegativeInt("channelCount")
        val programCount = data.requiredNonNegativeInt("programCount")
        require(channelCount == channels.size)
        require(programCount == programs.size)
        require(channels.map(DeviceLightChannelStatus::index).toSet().size == channels.size)
        require(channels.map(DeviceLightChannelStatus::key).toSet().size == channels.size)
        require(programs.map(DeviceLightProgramStatus::index).toSet().size == programs.size)

        val supported = data.requiredBoolean("supported")
        val manualSupported = data.requiredBoolean("manualSupported")
        val programSupported = data.requiredBoolean("programSupported")
        val liveEditEnabled = data.requiredBoolean("liveEditEnabled")
        require(supported)
        require(runtime.supportsManualSet == manualSupported)
        require(runtime.supportsProgramApply == programSupported)
        require(runtime.supportsProgramDelete == programSupported)
        require(runtime.supportsLiveEdit == liveEditEnabled)

        return DeviceLightStatus(
            supported = supported,
            manualSupported = manualSupported,
            programSupported = programSupported,
            presetsSupported = data.requiredBoolean("presetsSupported"),
            simulationSupported = data.requiredBoolean("simulationSupported"),
            channelCount = channelCount,
            programCount = programCount,
            liveEditEnabled = liveEditEnabled,
            channelEdit = data.requiredNonBlankString("channelEdit"),
            powerLimitW = data.requiredFiniteDouble("powerLimitW").also { require(it >= 0.0) },
            lockLoop = data.requiredBoolean("lockLoop"),
            temperatureDownStepPercent = data.requiredFiniteDouble(
                "temperatureDownStepPercent"
            ).also { require(it in 0.0..100.0) },
            temperatureRecoveryMs = data.requiredUnsigned32("temperatureRecoveryMs"),
            lightCorrectionFactor = data.requiredFiniteDouble("lightCorrectionFactor")
                .also { require(it >= 0.0) },
            uptimeMs = data.requiredUnsigned32("uptimeMs"),
            channels = channels,
            programs = programs,
            runtime = runtime
        )
    }

    fun parseManualSetResult(data: JSONObject): DeviceLightManualSetResult {
        data.requireExactKeys(MANUAL_RESULT_KEYS, "light.manual.set.data")
        val operation = data.requiredNonBlankString("operation")
        require(operation == "clearManual" || operation == "manualState")
        val manualActive = data.requiredBoolean("manualActive")
        require(manualActive == (operation == "manualState"))
        val durationMs = data.requiredUnsigned32("durationMs")
        if (manualActive) {
            require(durationMs in DeviceLightRuntimeContract.Limit.MIN_MANUAL_DURATION_MS..
                DeviceLightRuntimeContract.Limit.MAX_MANUAL_DURATION_MS)
        } else {
            require(durationMs == 0L)
        }
        val channels = parseChannelSnapshots(data.requiredArray("channels"))
        val affectedCount = data.requiredNonNegativeInt("affectedChannelCount")
        require(affectedCount > 0 && affectedCount == channels.size)
        require(channels.map(DeviceLightChannelSnapshot::listIndex).toSet().size == channels.size)
        require(channels.map { it.channel.key }.toSet().size == channels.size)
        require(!data.requiredBoolean("saved"))
        requireWireRoute(data, "light.manual.set")

        return DeviceLightManualSetResult(
            operation = operation,
            manualActive = manualActive,
            durationMs = durationMs,
            runtimeTransport = data.requiredNonBlankString("runtimeTransport"),
            command = data.requiredNonBlankString("command"),
            event = data.requiredNonBlankString("event"),
            channels = channels,
            affectedChannelCount = affectedCount,
            saved = false
        )
    }

    fun parseChannelRegimeSetResult(data: JSONObject): DeviceLightChannelRegimeSetResult {
        data.requireExactKeys(CHANNEL_REGIME_RESULT_KEYS, "light.channel.regime.set.data")
        require(data.requiredNonBlankString("operation") == "channelRegimeSet")
        val saved = data.requiredBoolean("saved")
        val saveRequested = data.requiredBoolean("saveRequested")
        require(saved == saveRequested)
        val channelKey = canonicalLightChannelKey(data.requiredNonBlankString("channelKey"))
        val regime = requireNotNull(
            DeviceLightRegime.fromWireExact(data.requiredNonBlankString("regime"))
        )
        val channel = parseChannelSnapshot(data.requiredObject("channel"))
        require(channel.channel.key == channelKey)
        require(channel.channel.regime == regime)
        requireWireRoute(data, "light.channel.regime.set")

        return DeviceLightChannelRegimeSetResult(
            operation = "channelRegimeSet",
            changed = data.requiredBoolean("changed"),
            saved = saved,
            saveRequested = saveRequested,
            channelKey = channelKey,
            regime = regime,
            runtimeTransport = data.requiredNonBlankString("runtimeTransport"),
            command = data.requiredNonBlankString("command"),
            event = data.requiredNonBlankString("event"),
            channel = channel
        )
    }

    fun parseProgramApplyResult(
        data: JSONObject,
        statusCode: Int
    ): DeviceLightProgramApplyResult {
        data.requireExactKeys(PROGRAM_APPLY_RESULT_KEYS, "light.program.apply.data")
        require(data.requiredNonBlankString("operation") == "programApply")
        val created = data.requiredBoolean("created")
        require(statusCode == if (created) 201 else 200)
        require(data.requiredBoolean("changed"))
        val saved = data.requiredBoolean("saved")
        val saveRequested = data.requiredBoolean("saveRequested")
        require(saved == saveRequested)
        val programIndex = data.requiredNonNegativeInt("programIndex")
        val channelKey = canonicalLightChannelKey(data.requiredNonBlankString("channelKey"))
        val channelListIndex = data.requiredNonNegativeInt("channelListIndex")
        val program = parseProgramSnapshot(data.requiredObject("program"))
        require(program.index == programIndex)
        require(program.channelKey == channelKey)
        require(program.bound)
        requireWireRoute(data, "light.program.apply")

        return DeviceLightProgramApplyResult(
            operation = "programApply",
            created = created,
            changed = true,
            saved = saved,
            saveRequested = saveRequested,
            programIndex = programIndex,
            channelKey = channelKey,
            channelListIndex = channelListIndex,
            runtimeTransport = data.requiredNonBlankString("runtimeTransport"),
            command = data.requiredNonBlankString("command"),
            event = data.requiredNonBlankString("event"),
            program = program
        )
    }

    fun parseProgramDeleteResult(data: JSONObject): DeviceLightProgramDeleteResult {
        data.requireExactKeys(PROGRAM_DELETE_RESULT_KEYS, "light.program.delete.data")
        require(data.requiredNonBlankString("operation") == "programDelete")
        require(data.requiredBoolean("deleted"))
        require(data.requiredBoolean("changed"))
        val saved = data.requiredBoolean("saved")
        val saveRequested = data.requiredBoolean("saveRequested")
        require(saved == saveRequested)
        requireWireRoute(data, "light.program.delete")

        return DeviceLightProgramDeleteResult(
            operation = "programDelete",
            deleted = true,
            changed = true,
            saved = saved,
            saveRequested = saveRequested,
            programIndex = data.requiredNonNegativeInt("programIndex"),
            deletedListIndex = data.requiredNonNegativeInt("deletedListIndex"),
            channelKey = canonicalLightChannelKey(data.requiredNonBlankString("channelKey")),
            deletedPointCount = data.requiredNonNegativeInt("deletedPointCount"),
            programCount = data.requiredNonNegativeInt("programCount"),
            runtimeTransport = data.requiredNonBlankString("runtimeTransport"),
            command = data.requiredNonBlankString("command"),
            event = data.requiredNonBlankString("event")
        )
    }

    private fun parseStatusChannels(array: JSONArray): List<DeviceLightChannelStatus> =
        buildList {
            repeat(array.length()) { index ->
                add(parseChannelStatus(array.requiredObject(index), CHANNEL_KEYS))
            }
        }

    private fun parseChannelSnapshots(array: JSONArray): List<DeviceLightChannelSnapshot> =
        buildList {
            repeat(array.length()) { index -> add(parseChannelSnapshot(array.requiredObject(index))) }
        }

    private fun parseChannelSnapshot(json: JSONObject): DeviceLightChannelSnapshot {
        json.requireExactKeys(CHANNEL_SNAPSHOT_KEYS, "light channel mutation snapshot")
        return DeviceLightChannelSnapshot(
            channel = parseChannelStatus(json, CHANNEL_SNAPSHOT_KEYS),
            listIndex = json.requiredNonNegativeInt("listIndex")
        )
    }

    private fun parseChannelStatus(
        json: JSONObject,
        expectedKeys: Set<String>
    ): DeviceLightChannelStatus {
        json.requireExactKeys(expectedKeys, "light channel")
        val editable = json.requiredObject("editable")
        editable.requireExactKeys(CHANNEL_EDITABLE_KEYS, "light channel editable")
        val valueNow = json.requiredFiniteDouble("valueNow")
        val valueAuto = json.requiredFiniteDouble("valueAuto")
        val valueManual = json.requiredFiniteDouble("valueManual")
        val percentNow = json.requiredFiniteDouble("percentNow")
        val percentAuto = json.requiredFiniteDouble("percentAuto")
        val percentManual = json.requiredFiniteDouble("percentManual")
        requireUnitPercent(valueNow, percentNow, allowManualSentinel = false)
        requireUnitPercent(valueAuto, percentAuto, allowManualSentinel = false)
        requireUnitPercent(valueManual, percentManual, allowManualSentinel = true)

        return DeviceLightChannelStatus(
            index = json.requiredNonNegativeInt("index"),
            key = canonicalLightChannelKey(json.requiredNonBlankString("key")),
            name = json.requiredNonBlankString("name"),
            displayName = json.requiredNonBlankString("displayName"),
            profileManaged = json.requiredBoolean("profileManaged"),
            regime = requireNotNull(
                DeviceLightRegime.fromWireExact(json.requiredNonBlankString("regime"))
            ),
            channelKind = requireNotNull(
                DeviceLightChannelKind.fromWireExact(json.requiredNonBlankString("channelKind"))
            ),
            gpio = json.requiredInt("gpio").also { require(it >= -1) },
            ledcChannel = json.requiredInt("ledcChannel").also { require(it >= -1) },
            group = json.requiredInt("group").also { require(it >= -1) },
            valueNow = valueNow,
            valueAuto = valueAuto,
            valueManual = valueManual,
            manualTimeoutMs = json.requiredUnsigned32("manualTimeoutMs"),
            percentNow = percentNow,
            percentAuto = percentAuto,
            percentManual = percentManual,
            invert = json.requiredBoolean("invert"),
            pwmResolutionBits = json.requiredNonNegativeInt("pwmResolutionBits"),
            pwmFrequencyHz = json.requiredNonNegativeInt("pwmFrequencyHz"),
            color = json.requiredInt("color"),
            lumen = json.requiredFiniteDouble("lumen").also { require(it >= 0.0) },
            lux = json.requiredFiniteDouble("lux").also { require(it >= 0.0) },
            watt = json.requiredFiniteDouble("watt").also { require(it >= 0.0) },
            editable = DeviceLightChannelEditable(
                hardware = editable.requiredBoolean("hardware"),
                displayName = editable.requiredBoolean("displayName"),
                color = editable.requiredBoolean("color"),
                hardwareCalibration = editable.requiredBoolean("hardwareCalibration")
            )
        )
    }

    private fun parseStatusPrograms(
        array: JSONArray,
        channels: List<DeviceLightChannelStatus>
    ): List<DeviceLightProgramStatus> {
        val channelKeys = channels.map(DeviceLightChannelStatus::key).toSet()
        return buildList {
            repeat(array.length()) { index ->
                val json = array.requiredObject(index)
                json.requireExactKeys(PROGRAM_STATUS_KEYS, "light status program")
                val channelKey = json.requiredStringAllowEmpty("channelKey")
                val bound = json.requiredBoolean("bound")
                if (bound) require(canonicalLightChannelKey(channelKey) in channelKeys)
                val points = parseStatusPoints(json.requiredArray("points"))
                val pointCount = json.requiredNonNegativeInt("pointCount")
                require(pointCount == points.size)
                add(
                    DeviceLightProgramStatus(
                        index = json.requiredNonNegativeInt("index"),
                        channelKey = channelKey,
                        bound = bound,
                        pointCount = pointCount,
                        points = points
                    )
                )
            }
        }
    }

    private fun parseStatusPoints(array: JSONArray): List<DeviceLightProgramPointStatus> =
        buildList {
            repeat(array.length()) { index ->
                val json = array.requiredObject(index)
                json.requireExactKeys(POINT_STATUS_KEYS, "light status program point")
                add(parsePoint(json))
            }
        }

    private fun parseProgramSnapshot(json: JSONObject): DeviceLightProgramSnapshot {
        json.requireExactKeys(PROGRAM_SNAPSHOT_KEYS, "light program mutation snapshot")
        val channelKey = canonicalLightChannelKey(json.requiredNonBlankString("channelKey"))
        val bound = json.requiredBoolean("bound")
        val pointsArray = json.requiredArray("points")
        val points = buildList {
            repeat(pointsArray.length()) { index ->
                val pointJson = pointsArray.requiredObject(index)
                pointJson.requireExactKeys(POINT_SNAPSHOT_KEYS, "light program mutation point")
                val pointIndex = pointJson.requiredNonNegativeInt("index")
                require(pointIndex == index)
                add(DeviceLightProgramPointSnapshot(pointIndex, parsePoint(pointJson)))
            }
        }
        val pointCount = json.requiredNonNegativeInt("pointCount")
        require(pointCount == points.size)
        return DeviceLightProgramSnapshot(
            listIndex = json.requiredNonNegativeInt("listIndex"),
            index = json.requiredNonNegativeInt("index"),
            channelKey = channelKey,
            bound = bound,
            pointCount = pointCount,
            points = points
        )
    }

    private fun parsePoint(json: JSONObject): DeviceLightProgramPointStatus {
        val timeMs = json.requiredLong("timeMs").also {
            require(it in 0 until DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY)
        }
        val value = json.requiredFiniteDouble("value")
        val percent = json.requiredFiniteDouble("percent")
        requireUnitPercent(value, percent, allowManualSentinel = false)
        val time = json.requiredNonBlankString("time")
        require(time == canonicalTimeText(timeMs))
        return DeviceLightProgramPointStatus(timeMs, time, value, percent)
    }

    private fun parseRuntime(json: JSONObject): DeviceLightRuntimeCapabilities {
        json.requireExactKeys(RUNTIME_KEYS, "light status runtime")
        return DeviceLightRuntimeCapabilities(
            module = json.requiredNonBlankString("module").also { require(it == "light") },
            readOnly = json.requiredBoolean("readOnly").also { require(!it) },
            supportsManualSet = json.requiredBoolean("supportsManualSet"),
            supportsChannelRegimeSet = json.requiredBoolean("supportsChannelRegimeSet")
                .also { require(it) },
            supportsProgramApply = json.requiredBoolean("supportsProgramApply"),
            supportsProgramDelete = json.requiredBoolean("supportsProgramDelete"),
            supportsLiveEdit = json.requiredBoolean("supportsLiveEdit"),
            event = json.requiredNonBlankString("event").also {
                require(it == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
            }
        )
    }

    private fun requireWireRoute(json: JSONObject, expectedCommand: String) {
        require(json.requiredNonBlankString("runtimeTransport") == "websocket")
        require(json.requiredNonBlankString("command") == expectedCommand)
        require(json.requiredNonBlankString("event") == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
    }

    private fun requireUnitPercent(
        value: Double,
        percent: Double,
        allowManualSentinel: Boolean
    ) {
        if (allowManualSentinel && value == -1.0) {
            require(abs(percent + 100.0) <= FLOAT_TOLERANCE)
            return
        }
        require(value in 0.0..1.0)
        require(percent in 0.0..100.0)
        require(abs(percent - value * 100.0) <= FLOAT_TOLERANCE)
    }

    private fun canonicalTimeText(timeMs: Long): String {
        var remaining = timeMs
        val hours = remaining / 3_600_000L
        remaining -= hours * 3_600_000L
        val minutes = remaining / 60_000L
        remaining -= minutes * 60_000L
        val seconds = remaining / 1_000L
        remaining -= seconds * 1_000L
        return buildString {
            append(hours.toString().padStart(2, '0'))
            append(':')
            append(minutes.toString().padStart(2, '0'))
            if (seconds > 0L || remaining > 0L) {
                append(':')
                append(seconds.toString().padStart(2, '0'))
                if (remaining > 0L) {
                    append('.')
                    append(remaining.toString().padStart(3, '0'))
                }
            }
        }
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "$label keys differ from firmware; expected=$expected actual=$actual"
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject =
        get(key) as? JSONObject ?: error("$key must be a JSON object.")

    private fun JSONObject.requiredArray(key: String): JSONArray =
        get(key) as? JSONArray ?: error("$key must be a JSON array.")

    private fun JSONArray.requiredObject(index: Int): JSONObject =
        get(index) as? JSONObject ?: error("array[$index] must be a JSON object.")

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredInt(key: String): Int {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val value = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == value.toDouble())
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return value.toInt()
    }

    private fun JSONObject.requiredNonNegativeInt(key: String): Int =
        requiredInt(key).also { require(it >= 0) }

    private fun JSONObject.requiredLong(key: String): Long {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val value = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == value.toDouble())
        return value
    }

    private fun JSONObject.requiredUnsigned32(key: String): Long =
        requiredLong(key).also { require(it in 0L..UINT32_MAX) }

    private fun JSONObject.requiredFiniteDouble(key: String): Double {
        val number = get(key) as? Number ?: error("$key must be numeric.")
        return number.toDouble().also { require(it.isFinite()) }
    }

    private fun JSONObject.requiredNonBlankString(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isNotEmpty())
        require(value == value.trim())
        require(value.none(Char::isISOControl))
        return value
    }

    private fun JSONObject.requiredStringAllowEmpty(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value == value.trim())
        require(value.none(Char::isISOControl))
        return value
    }

    private const val UINT32_MAX = 4_294_967_295L
    private const val FLOAT_TOLERANCE = 0.11

    private val STATUS_KEYS = setOf(
        "supported", "manualSupported", "programSupported", "presetsSupported",
        "simulationSupported", "channelCount", "programCount", "liveEditEnabled",
        "channelEdit", "powerLimitW", "lockLoop", "temperatureDownStepPercent",
        "temperatureRecoveryMs", "lightCorrectionFactor", "uptimeMs", "channels",
        "programs", "runtime"
    )
    private val CHANNEL_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime",
        "channelKind", "gpio", "ledcChannel", "group", "valueNow", "valueAuto",
        "valueManual", "manualTimeoutMs", "percentNow", "percentAuto", "percentManual",
        "invert", "pwmResolutionBits", "pwmFrequencyHz", "color", "lumen", "lux",
        "watt", "editable"
    )
    private val CHANNEL_SNAPSHOT_KEYS = CHANNEL_KEYS + "listIndex"
    private val CHANNEL_EDITABLE_KEYS = setOf(
        "hardware", "displayName", "color", "hardwareCalibration"
    )
    private val PROGRAM_STATUS_KEYS = setOf(
        "index", "channelKey", "bound", "pointCount", "points"
    )
    private val PROGRAM_SNAPSHOT_KEYS = PROGRAM_STATUS_KEYS + "listIndex"
    private val POINT_STATUS_KEYS = setOf("timeMs", "time", "value", "percent")
    private val POINT_SNAPSHOT_KEYS = POINT_STATUS_KEYS + "index"
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsManualSet", "supportsChannelRegimeSet",
        "supportsProgramApply", "supportsProgramDelete", "supportsLiveEdit", "event"
    )
    private val MANUAL_RESULT_KEYS = setOf(
        "operation", "manualActive", "durationMs", "runtimeTransport", "command",
        "event", "channels", "affectedChannelCount", "saved"
    )
    private val CHANNEL_REGIME_RESULT_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "channelKey", "regime",
        "runtimeTransport", "command", "event", "channel"
    )
    private val PROGRAM_APPLY_RESULT_KEYS = setOf(
        "operation", "created", "changed", "saved", "saveRequested", "programIndex",
        "channelKey", "channelListIndex", "runtimeTransport", "command", "event", "program"
    )
    private val PROGRAM_DELETE_RESULT_KEYS = setOf(
        "operation", "deleted", "changed", "saved", "saveRequested", "programIndex",
        "deletedListIndex", "channelKey", "deletedPointCount", "programCount",
        "runtimeTransport", "command", "event"
    )
}
