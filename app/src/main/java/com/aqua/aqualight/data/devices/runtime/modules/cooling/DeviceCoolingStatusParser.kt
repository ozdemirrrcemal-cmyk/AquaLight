package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

/** Strict mirror of `cooling.status.get.data`. */
object DeviceCoolingStatusParser {

    fun parse(data: JSONObject): DeviceCoolingStatus {
        val status = data.optJSONObject("status") ?: data
        status.requireExactKeys(STATUS_KEYS, "cooling.status.get.data")

        val fanOutputCount = status.requireNonNegativeInt("fanOutputCount")
        val ruleCount = status.requireNonNegativeInt("ruleCount")
        val minTemperatureC = status.requireFiniteDouble("minTemperatureC")
        val maxTemperatureC = status.requireFiniteDouble("maxTemperatureC")
        require(minTemperatureC < maxTemperatureC) {
            "cooling.status.get temperature range is invalid."
        }

        val fans = parseFans(status.requireArray("fans"))
        val rules = parseRules(status.requireArray("rules"))
        val runtime = parseRuntime(status.requireObject("runtime"))

        require(fans.size == fanOutputCount) {
            "cooling.status.get fanOutputCount does not match fans."
        }
        require(rules.size == ruleCount) {
            "cooling.status.get ruleCount does not match rules."
        }
        require(fans.map(DeviceCoolingFanStatus::index).toSet().size == fans.size) {
            "cooling.status.get contains duplicate fan indexes."
        }
        require(fans.map(DeviceCoolingFanStatus::key).toSet().size == fans.size) {
            "cooling.status.get contains duplicate fan keys."
        }
        require(rules.map(DeviceCoolingRuleStatus::index).toSet().size == rules.size) {
            "cooling.status.get contains duplicate rule indexes."
        }
        require(fans.all { fan ->
            fan.editable.displayName == runtime.supportsFanDisplayName
        }) {
            "cooling status display-name editability disagrees with runtime capability."
        }

        return DeviceCoolingStatus(
            supported = status.requireBoolean("supported"),
            fanSupported = status.requireBoolean("fanSupported"),
            temperatureSupported = status.requireBoolean("temperatureSupported"),
            fanOutputCount = fanOutputCount,
            ruleCount = ruleCount,
            mode = requireNotNull(
                DeviceCoolingMode.fromWireExact(status.requireText("mode"))
            ) { "cooling.status.get mode is not an exact firmware value." },
            minTemperatureC = minTemperatureC,
            maxTemperatureC = maxTemperatureC,
            fixedSensorIndex = status.requireInt("fixedSensorIndex"),
            uptimeMs = status.requireNonNegativeLong("uptimeMs"),
            fans = fans,
            rules = rules,
            runtime = runtime
        )
    }

    fun parseExact(data: JSONObject): Result<DeviceCoolingStatus> = runCatching {
        parse(data)
    }

    private fun parseRuntime(runtime: JSONObject): DeviceCoolingRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "cooling.status.get.data.runtime")
        val parsed = DeviceCoolingRuntimeCapabilities(
            module = runtime.requireText("module"),
            readOnly = runtime.requireBoolean("readOnly"),
            supportsConfigApply = runtime.requireBoolean("supportsConfigApply"),
            supportsModeSet = runtime.requireBoolean("supportsModeSet"),
            supportsTemperatureRange = runtime.requireBoolean("supportsTemperatureRange"),
            hardwareEditable = runtime.requireBoolean("hardwareEditable"),
            fanMappingEditable = runtime.requireBoolean("fanMappingEditable"),
            sensorMappingEditable = runtime.requireBoolean("sensorMappingEditable"),
            event = runtime.requireText("event"),
            supportsFanDisplayName = runtime.requireBoolean("supportsFanDisplayName")
        )

        require(parsed.module == DeviceCoolingRuntimeContract.MODULE) {
            "cooling runtime module is incompatible."
        }
        require(!parsed.readOnly && parsed.supportsConfigApply && parsed.supportsModeSet &&
            parsed.supportsTemperatureRange) {
            "cooling runtime write capabilities are incompatible."
        }
        require(!parsed.hardwareEditable && !parsed.fanMappingEditable &&
            !parsed.sensorMappingEditable) {
            "cooling runtime exposes forbidden hardware editability."
        }
        require(parsed.event == STATUS_CHANGED_EVENT) {
            "cooling runtime event is incompatible."
        }
        return parsed
    }

    private fun parseFans(fans: JSONArray): List<DeviceCoolingFanStatus> = buildList {
        repeat(fans.length()) { arrayIndex ->
            val item = fans.requireObject(arrayIndex, "fans")
            item.requireExactKeys(FAN_KEYS, "cooling.status.get.data.fans[$arrayIndex]")
            val editable = item.requireObject("editable")
            editable.requireExactKeys(
                EDITABLE_KEYS,
                "cooling.status.get.data.fans[$arrayIndex].editable"
            )

            val mode = requireNotNull(
                DeviceCoolingMode.fromWireExact(item.requireText("regime"))
            ) { "cooling fan regime is not an exact firmware value." }
            val channelKind = item.requireText("channelKind")
            require(channelKind in CHANNEL_KINDS) {
                "cooling fan channelKind is not an exact firmware value."
            }

            add(
                DeviceCoolingFanStatus(
                    index = item.requireNonNegativeInt("index"),
                    key = item.requireText("key"),
                    name = item.requireText("name"),
                    displayName = item.requireText("displayName"),
                    profileManaged = item.requireBoolean("profileManaged"),
                    mode = mode,
                    channelKind = channelKind,
                    gpio = item.requireInt("gpio"),
                    ledcChannel = item.requireInt("ledcChannel"),
                    group = item.requireInt("group"),
                    valueNow = item.requireFiniteDouble("valueNow"),
                    valueAuto = item.requireFiniteDouble("valueAuto"),
                    valueManual = item.requireFiniteDouble("valueManual"),
                    valueMin = item.requireFiniteDouble("valueMin"),
                    valueMax = item.requireFiniteDouble("valueMax"),
                    manualTimeoutMs = item.requireNonNegativeLong("manualTimeoutMs"),
                    percentNow = item.requireFiniteDouble("percentNow"),
                    percentAuto = item.requireFiniteDouble("percentAuto"),
                    percentManual = item.requireFiniteDouble("percentManual"),
                    percentMin = item.requireFiniteDouble("percentMin"),
                    percentMax = item.requireFiniteDouble("percentMax"),
                    invert = item.requireBoolean("invert"),
                    pwmResolutionBits = item.requireNonNegativeInt("pwmResolutionBits"),
                    pwmFrequencyHz = item.requireNonNegativeInt("pwmFrequencyHz"),
                    editable = DeviceCoolingFanEditable(
                        hardware = editable.requireBoolean("hardware"),
                        displayName = editable.requireBoolean("displayName"),
                        hardwareCalibration = editable.requireBoolean("hardwareCalibration")
                    )
                )
            )
        }
    }

    private fun parseRules(rules: JSONArray): List<DeviceCoolingRuleStatus> = buildList {
        repeat(rules.length()) { arrayIndex ->
            val item = rules.requireObject(arrayIndex, "rules")
            item.requireExactKeys(RULE_KEYS, "cooling.status.get.data.rules[$arrayIndex]")
            val minTemperatureC = item.requireFiniteDouble("minTemperatureC")
            val maxTemperatureC = item.requireFiniteDouble("maxTemperatureC")
            require(minTemperatureC < maxTemperatureC) {
                "cooling rule temperature range is invalid."
            }

            add(
                DeviceCoolingRuleStatus(
                    index = item.requireNonNegativeInt("index"),
                    name = item.requireOptionalText("name"),
                    enabled = item.requireBoolean("enabled"),
                    fanIndex = item.requireInt("fanIndex"),
                    channelKey = item.requireOptionalText("channelKey"),
                    bound = item.requireBoolean("bound"),
                    minTemperatureC = minTemperatureC,
                    maxTemperatureC = maxTemperatureC,
                    group = item.requireInt("group"),
                    sensorBindings = item.requireIntArray("sensorBindings")
                )
            )
        }
    }

    private const val STATUS_CHANGED_EVENT = "cooling.status.changed"
    private val CHANNEL_KINDS = setOf("gpio", "digital", "none")
    private val STATUS_KEYS = setOf(
        "supported", "fanSupported", "temperatureSupported", "fanOutputCount",
        "ruleCount", "mode", "minTemperatureC", "maxTemperatureC",
        "fixedSensorIndex", "uptimeMs", "fans", "rules", "runtime"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsModeSet",
        "supportsTemperatureRange", "supportsFanDisplayName", "hardwareEditable",
        "fanMappingEditable", "sensorMappingEditable", "event"
    )
    private val FAN_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime",
        "channelKind", "gpio", "ledcChannel", "group", "valueNow", "valueAuto",
        "valueManual", "valueMin", "valueMax", "manualTimeoutMs", "percentNow",
        "percentAuto", "percentManual", "percentMin", "percentMax", "invert",
        "pwmResolutionBits", "pwmFrequencyHz", "editable"
    )
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "hardwareCalibration")
    private val RULE_KEYS = setOf(
        "index", "name", "enabled", "fanIndex", "channelKey", "bound",
        "minTemperatureC", "maxTemperatureC", "group", "sensorBindings"
    )
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) {
        "$label keys differ from the firmware contract; expected=$expected actual=$actual"
    }
}

private fun JSONObject.requireObject(key: String): JSONObject {
    require(has(key) && !isNull(key)) { "$key is required." }
    return get(key) as? JSONObject ?: error("$key must be a JSON object.")
}

private fun JSONObject.requireArray(key: String): JSONArray {
    require(has(key) && !isNull(key)) { "$key is required." }
    return get(key) as? JSONArray ?: error("$key must be a JSON array.")
}

private fun JSONArray.requireObject(index: Int, label: String): JSONObject {
    return get(index) as? JSONObject ?: error("$label[$index] must be a JSON object.")
}

private fun JSONObject.requireText(key: String): String {
    val value = requireOptionalText(key)
    require(value.isNotEmpty()) { "$key must not be empty." }
    return value
}

private fun JSONObject.requireOptionalText(key: String): String {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

private fun JSONObject.requireBoolean(key: String): Boolean {
    require(has(key) && !isNull(key)) { "$key is required." }
    return get(key) as? Boolean ?: error("$key must be a boolean.")
}

private fun JSONObject.requireInt(key: String): Int {
    val value = requireIntegralLong(key)
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "$key is outside the supported integer range."
    }
    return value.toInt()
}

private fun JSONObject.requireNonNegativeInt(key: String): Int =
    requireInt(key).also { value -> require(value >= 0) { "$key must not be negative." } }

private fun JSONObject.requireNonNegativeLong(key: String): Long =
    requireIntegralLong(key).also { value -> require(value >= 0L) { "$key must not be negative." } }

private fun JSONObject.requireIntegralLong(key: String): Long {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key) as? Number ?: error("$key must be an integer.")
    val asLong = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble()) {
        "$key must be an integer."
    }
    return asLong
}

private fun JSONObject.requireFiniteDouble(key: String): Double {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key) as? Number ?: error("$key must be a number.")
    return value.toDouble().also { parsed -> require(parsed.isFinite()) { "$key must be finite." } }
}

private fun JSONObject.requireIntArray(key: String): List<Int> {
    val array = requireArray(key)
    return buildList {
        repeat(array.length()) { index ->
            val raw = array.get(index) as? Number
                ?: error("$key[$index] must be an integer.")
            val asLong = raw.toLong()
            require(raw.toDouble().isFinite() && raw.toDouble() == asLong.toDouble()) {
                "$key[$index] must be an integer."
            }
            require(asLong in 0..Int.MAX_VALUE.toLong()) {
                "$key[$index] is outside the supported sensor-index range."
            }
            add(asLong.toInt())
        }
        require(size == toSet().size) { "$key must not contain duplicate indexes." }
    }
}
