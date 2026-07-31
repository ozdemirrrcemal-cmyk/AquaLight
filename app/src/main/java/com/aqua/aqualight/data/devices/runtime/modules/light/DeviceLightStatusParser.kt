package com.aqua.aqualight.data.devices.runtime.modules.light

import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions", "LongMethod", "MagicNumber")
object DeviceLightStatusParser {

    fun parse(data: JSONObject): DeviceLightStatus {
        val status = data.optJSONObject("status") ?: data
        status.requireExactKeys(STATUS_KEYS, "light.status.get.data")

        val supported = status.requiredBoolean("supported")
        val manualSupported = status.requiredBoolean("manualSupported")
        val programSupported = status.requiredBoolean("programSupported")
        val presetsSupported = status.requiredBoolean("presetsSupported")
        val simulationSupported = status.requiredBoolean("simulationSupported")
        val channelCount = status.requiredInt("channelCount").also {
            require(it in 0..DeviceLightRuntimeContract.Limit.MAX_MANUAL_CHANNELS) {
                "Light channelCount is outside the commercial WebSocket limit."
            }
        }
        val programCount = status.requiredInt("programCount").also {
            require(it >= 0) { "Light programCount must not be negative." }
        }
        val liveEditEnabled = status.requiredBoolean("liveEditEnabled")
        val channelEdit = status.requiredInt("channelEdit").also {
            require(it >= -1) { "Light channelEdit must be -1 or greater." }
        }
        val powerLimitW = status.requiredDouble("powerLimitW").also {
            require(it >= 0.0) { "Light powerLimitW must not be negative." }
        }
        val lockLoop = status.requiredBoolean("lockLoop")
        val temperatureDownStepPercent = status.requiredDouble("temperatureDownStepPercent")
        val temperatureRecoveryMs = status.requiredLong("temperatureRecoveryMs").also {
            require(it >= 0L) { "Light temperatureRecoveryMs must not be negative." }
        }
        val lightCorrectionFactor = status.requiredDouble("lightCorrectionFactor")
        val uptimeMs = status.requiredLong("uptimeMs").also {
            require(it >= 0L) { "Light uptimeMs must not be negative." }
        }
        val channels = parseChannels(status.requiredArray("channels"))
        val programs = parsePrograms(status.requiredArray("programs"), channels)
        val runtime = parseRuntime(status.requiredObject("runtime"))

        require(channelCount == channels.size) {
            "Light channelCount differs from channels array size."
        }
        require(programCount == programs.size) {
            "Light programCount differs from programs array size."
        }
        require(channelEdit == -1 || channelEdit < channelCount) {
            "Light channelEdit does not reference a configured channel."
        }
        require(runtime.supportsManualSet == manualSupported) {
            "Light runtime supportsManualSet differs from manualSupported."
        }
        require(runtime.supportsProgramApply == programSupported) {
            "Light runtime supportsProgramApply differs from programSupported."
        }
        require(runtime.supportsProgramDelete == programSupported) {
            "Light runtime supportsProgramDelete differs from programSupported."
        }
        require(runtime.supportsLiveEdit == liveEditEnabled) {
            "Light runtime supportsLiveEdit differs from liveEditEnabled."
        }

        return DeviceLightStatus(
            supported = supported,
            manualSupported = manualSupported,
            programSupported = programSupported,
            presetsSupported = presetsSupported,
            simulationSupported = simulationSupported,
            channelCount = channelCount,
            programCount = programCount,
            liveEditEnabled = liveEditEnabled,
            channelEdit = channelEdit,
            powerLimitW = powerLimitW,
            lockLoop = lockLoop,
            temperatureDownStepPercent = temperatureDownStepPercent,
            temperatureRecoveryMs = temperatureRecoveryMs,
            lightCorrectionFactor = lightCorrectionFactor,
            uptimeMs = uptimeMs,
            channels = channels,
            programs = programs,
            runtime = runtime
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceLightRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "light.status.get.data.runtime")
        val parsed = DeviceLightRuntimeCapabilities(
            module = runtime.requiredString("module"),
            readOnly = runtime.requiredBoolean("readOnly"),
            supportsManualSet = runtime.requiredBoolean("supportsManualSet"),
            supportsChannelRegimeSet = runtime.requiredBoolean("supportsChannelRegimeSet"),
            supportsProgramApply = runtime.requiredBoolean("supportsProgramApply"),
            supportsProgramDelete = runtime.requiredBoolean("supportsProgramDelete"),
            supportsLiveEdit = runtime.requiredBoolean("supportsLiveEdit"),
            event = runtime.requiredString("event")
        )
        require(parsed.module == DeviceLightRuntimeContract.MODULE)
        require(!parsed.readOnly) { "Light runtime must not report readOnly=true." }
        require(parsed.supportsChannelRegimeSet) {
            "Commercial Light runtime must support channel regime changes."
        }
        require(parsed.event == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
        return parsed
    }

    private fun parseChannels(channels: JSONArray): List<DeviceLightChannelStatus> {
        val parsed = ArrayList<DeviceLightChannelStatus>(channels.length())
        val indexes = linkedSetOf<Int>()
        val keys = linkedSetOf<String>()
        for (position in 0 until channels.length()) {
            val item = channels.requiredObject(position, "light channel")
            val channel = parseChannel(item)
            require(indexes.add(channel.index)) { "Duplicate Light channel index: ${channel.index}" }
            require(keys.add(channel.key)) { "Duplicate Light channel key: ${channel.key}" }
            parsed += channel
        }
        return parsed
    }

    private fun parseChannel(item: JSONObject): DeviceLightChannelStatus {
        item.requireExactKeys(CHANNEL_KEYS, "light channel")
        val editable = parseEditable(item.requiredObject("editable"))
        val valueNow = item.requiredDouble("valueNow")
        val valueAuto = item.requiredDouble("valueAuto")
        val valueManual = item.requiredDouble("valueManual")
        val percentNow = item.requiredDouble("percentNow")
        val percentAuto = item.requiredDouble("percentAuto")
        val percentManual = item.requiredDouble("percentManual")
        requirePercentEcho(valueNow, percentNow, "percentNow")
        requirePercentEcho(valueAuto, percentAuto, "percentAuto")
        requirePercentEcho(valueManual, percentManual, "percentManual")

        return DeviceLightChannelStatus(
            index = item.requiredInt("index").also {
                require(it >= 0) { "Light channel index must not be negative." }
            },
            key = item.requiredIdentifier("key"),
            name = item.requiredString("name"),
            displayName = item.requiredString("displayName"),
            profileManaged = item.requiredBoolean("profileManaged"),
            regime = requireNotNull(
                DeviceLightRegime.fromWireExact(item.requiredString("regime"))
            ) { "Unknown Light regime." },
            channelKind = item.requiredString("channelKind").also {
                require(it in CHANNEL_KINDS) { "Unknown Light channelKind: $it" }
            },
            gpio = item.requiredInt("gpio").also {
                require(it >= -1) { "Light gpio must be -1 or greater." }
            },
            ledcChannel = item.requiredInt("ledcChannel").also {
                require(it >= -1) { "Light ledcChannel must be -1 or greater." }
            },
            group = item.requiredInt("group").also {
                require(it >= -1) { "Light group must be -1 or greater." }
            },
            valueNow = valueNow,
            valueAuto = valueAuto,
            valueManual = valueManual,
            manualTimeoutMs = item.requiredLong("manualTimeoutMs").also {
                require(it >= 0L) { "Light manualTimeoutMs must not be negative." }
            },
            percentNow = percentNow,
            percentAuto = percentAuto,
            percentManual = percentManual,
            invert = item.requiredBoolean("invert"),
            pwmResolutionBits = item.requiredInt("pwmResolutionBits").also {
                require(it >= 0) { "Light pwmResolutionBits must not be negative." }
            },
            pwmFrequencyHz = item.requiredInt("pwmFrequencyHz").also {
                require(it >= 0) { "Light pwmFrequencyHz must not be negative." }
            },
            color = item.requiredInt("color"),
            lumen = item.requiredDouble("lumen"),
            lux = item.requiredDouble("lux"),
            watt = item.requiredDouble("watt"),
            editable = editable
        )
    }

    private fun parseEditable(editable: JSONObject): DeviceLightChannelEditable {
        editable.requireExactKeys(EDITABLE_KEYS, "light channel editable")
        return DeviceLightChannelEditable(
            hardware = editable.requiredBoolean("hardware"),
            displayName = editable.requiredBoolean("displayName"),
            color = editable.requiredBoolean("color"),
            hardwareCalibration = editable.requiredBoolean("hardwareCalibration")
        )
    }

    private fun parsePrograms(
        programs: JSONArray,
        channels: List<DeviceLightChannelStatus>
    ): List<DeviceLightProgramStatus> {
        val channelKeys = channels.mapTo(linkedSetOf(), DeviceLightChannelStatus::key)
        val indexes = linkedSetOf<Int>()
        val parsed = ArrayList<DeviceLightProgramStatus>(programs.length())
        for (position in 0 until programs.length()) {
            val program = parseProgram(programs.requiredObject(position, "light program"))
            require(indexes.add(program.index)) { "Duplicate Light program index: ${program.index}" }
            if (program.bound) {
                require(program.channelKey in channelKeys) {
                    "Bound Light program references an unknown channelKey."
                }
            }
            parsed += program
        }
        return parsed
    }

    private fun parseProgram(item: JSONObject): DeviceLightProgramStatus {
        item.requireExactKeys(PROGRAM_KEYS, "light program")
        val points = parseProgramPoints(item.requiredArray("points"))
        val pointCount = item.requiredInt("pointCount").also {
            require(it in 0..DeviceLightRuntimeContract.Limit.MAX_PROGRAM_POINTS) {
                "Light pointCount is outside the commercial limit."
            }
        }
        require(pointCount == points.size) {
            "Light pointCount differs from points array size."
        }
        return DeviceLightProgramStatus(
            index = item.requiredInt("index").also {
                require(it >= 0) { "Light program index must not be negative." }
            },
            channelKey = item.requiredIdentifier("channelKey"),
            bound = item.requiredBoolean("bound"),
            pointCount = pointCount,
            points = points
        )
    }

    private fun parseProgramPoints(points: JSONArray): List<DeviceLightProgramPointStatus> {
        val parsed = ArrayList<DeviceLightProgramPointStatus>(points.length())
        var previousTimeMs = -1L
        for (position in 0 until points.length()) {
            val item = points.requiredObject(position, "light program point")
            item.requireExactKeys(PROGRAM_POINT_KEYS, "light program point")
            val timeMs = item.requiredLong("timeMs").also {
                require(it in 0L..DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY) {
                    "Light program point timeMs is outside one day."
                }
                require(it >= previousTimeMs) {
                    "Light program points must be ordered by timeMs."
                }
            }
            previousTimeMs = timeMs
            val value = item.requiredDouble("value")
            val percent = item.requiredDouble("percent")
            requirePercentEcho(value, percent, "program point percent")
            parsed += DeviceLightProgramPointStatus(
                timeMs = timeMs,
                time = item.requiredString("time"),
                value = value,
                percent = percent
            )
        }
        return parsed
    }

    private fun requirePercentEcho(value: Double, percent: Double, label: String) {
        require(abs(percent - value * 100.0) <= PERCENT_ECHO_TOLERANCE) {
            "$label differs from its normalized value."
        }
    }

    private val STATUS_KEYS = setOf(
        "supported", "manualSupported", "programSupported", "presetsSupported",
        "simulationSupported", "channelCount", "programCount", "liveEditEnabled",
        "channelEdit", "powerLimitW", "lockLoop", "temperatureDownStepPercent",
        "temperatureRecoveryMs", "lightCorrectionFactor", "uptimeMs", "channels",
        "programs", "runtime"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsManualSet", "supportsChannelRegimeSet",
        "supportsProgramApply", "supportsProgramDelete", "supportsLiveEdit", "event"
    )
    private val CHANNEL_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime",
        "channelKind", "gpio", "ledcChannel", "group", "valueNow", "valueAuto",
        "valueManual", "manualTimeoutMs", "percentNow", "percentAuto",
        "percentManual", "invert", "pwmResolutionBits", "pwmFrequencyHz", "color",
        "lumen", "lux", "watt", "editable"
    )
    private val EDITABLE_KEYS = setOf(
        "hardware", "displayName", "color", "hardwareCalibration"
    )
    private val PROGRAM_KEYS = setOf(
        "index", "channelKey", "bound", "pointCount", "points"
    )
    private val PROGRAM_POINT_KEYS = setOf("timeMs", "time", "value", "percent")
    private val CHANNEL_KINDS = setOf("gpio", "digital", "none")
    private const val PERCENT_ECHO_TOLERANCE = 0.11
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) { "$label keys differ from the firmware contract." }
}

private fun JSONObject.requiredObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

private fun JSONObject.requiredArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be an array.")

private fun JSONArray.requiredObject(index: Int, label: String): JSONObject =
    get(index) as? JSONObject ?: error("$label at index $index must be an object.")

private fun JSONObject.requiredString(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.isNotEmpty()) { "$key must not be empty." }
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

private fun JSONObject.requiredIdentifier(key: String): String = requiredString(key).also { value ->
    require(value.length <= 64) { "$key is too long." }
    require(value.all { character -> character.isLetterOrDigit() || character in "._-" }) {
        "$key contains unsupported identifier characters."
    }
}

private fun JSONObject.requiredBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

private fun JSONObject.requiredInt(key: String): Int {
    val number = get(key) as? Number ?: error("$key must be an integer.")
    val double = number.toDouble()
    val long = number.toLong()
    require(double.isFinite() && double == long.toDouble()) { "$key must be an integer." }
    require(long in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$key is outside Int range." }
    return long.toInt()
}

private fun JSONObject.requiredLong(key: String): Long {
    val number = get(key) as? Number ?: error("$key must be an integer.")
    val double = number.toDouble()
    val long = number.toLong()
    require(double.isFinite() && double == long.toDouble()) { "$key must be an integer." }
    return long
}

private fun JSONObject.requiredDouble(key: String): Double {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    return number.toDouble().also { require(it.isFinite()) { "$key must be finite." } }
}
