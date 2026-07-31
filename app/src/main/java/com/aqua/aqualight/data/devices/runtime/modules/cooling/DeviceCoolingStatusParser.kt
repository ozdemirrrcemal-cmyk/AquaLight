package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import org.json.JSONArray
import org.json.JSONObject

object DeviceCoolingStatusParser {

    fun parse(data: JSONObject): DeviceCoolingStatus {
        data.requireExactKeys(STATUS_KEYS, "cooling.status.get.data")

        val fans = parseStatusFans(data.requiredArray("fans"))
        val rules = parseStatusRules(data.requiredArray("rules"))
        val fanOutputCount = data.requiredNonNegativeInt("fanOutputCount")
        val ruleCount = data.requiredNonNegativeInt("ruleCount")

        require(fanOutputCount == fans.size) {
            "cooling status fanOutputCount differs from fans size."
        }
        require(ruleCount == rules.size) {
            "cooling status ruleCount differs from rules size."
        }

        requireUnique(fans.map(DeviceCoolingFanStatus::index), "cooling fan index")
        requireUnique(fans.map(DeviceCoolingFanStatus::key), "cooling fan key")
        requireUnique(rules.map(DeviceCoolingRuleStatus::index), "cooling rule index")

        val minTemperatureC = data.requiredFiniteDouble("minTemperatureC")
        val maxTemperatureC = data.requiredFiniteDouble("maxTemperatureC")
        require(maxTemperatureC > minTemperatureC)

        return DeviceCoolingStatus(
            supported = data.requiredBoolean("supported"),
            fanSupported = data.requiredBoolean("fanSupported"),
            temperatureSupported = data.requiredBoolean("temperatureSupported"),
            fanOutputCount = fanOutputCount,
            ruleCount = ruleCount,
            mode = DeviceCoolingMode.fromWire(data.requiredNonBlankString("mode")),
            minTemperatureC = minTemperatureC,
            maxTemperatureC = maxTemperatureC,
            fixedSensorIndex = data.requiredInt("fixedSensorIndex").also { require(it >= -1) },
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            fans = fans,
            rules = rules,
            runtime = parseRuntime(data.requiredObject("runtime"))
        )
    }

    fun parseConfigApply(data: JSONObject): DeviceCoolingConfigApplyResult {
        data.requireExactKeys(CONFIG_APPLY_KEYS, "cooling.config.apply.data")

        val saveRequested = data.requiredBoolean("saveRequested")
        val saved = data.requiredBoolean("saved")
        require(saved == saveRequested)

        val appliedGlobalConfig = data.requiredBoolean("appliedGlobalConfig")
        val appliedFanDisplayNames = data.requiredBoolean("appliedFanDisplayNames")
        require(appliedGlobalConfig || appliedFanDisplayNames)

        return DeviceCoolingConfigApplyResult(
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
                require(it == COOLING_CONFIG_APPLY_COMMAND)
            },
            event = data.requiredNonBlankString("event").also(::requireStatusEvent),
            appliedGlobalConfig = appliedGlobalConfig,
            appliedFanDisplayNames = appliedFanDisplayNames,
            config = parseConfigSnapshot(data.requiredObject("config"))
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceCoolingRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "cooling status runtime")

        return DeviceCoolingRuntimeCapabilities(
            module = runtime.requiredNonBlankString("module").also {
                require(it == DeviceCoolingRuntimeContract.MODULE)
            },
            readOnly = runtime.requiredBoolean("readOnly").also { require(!it) },
            supportsConfigApply = runtime.requiredBoolean("supportsConfigApply")
                .also(::requireTrue),
            supportsModeSet = runtime.requiredBoolean("supportsModeSet")
                .also(::requireTrue),
            supportsTemperatureRange = runtime.requiredBoolean("supportsTemperatureRange")
                .also(::requireTrue),
            supportsFanDisplayName = runtime.requiredBoolean("supportsFanDisplayName"),
            hardwareEditable = runtime.requiredBoolean("hardwareEditable")
                .also(::requireFalse),
            fanMappingEditable = runtime.requiredBoolean("fanMappingEditable")
                .also(::requireFalse),
            sensorMappingEditable = runtime.requiredBoolean("sensorMappingEditable")
                .also(::requireFalse),
            event = runtime.requiredNonBlankString("event").also(::requireStatusEvent)
        )
    }

    private fun parseStatusFans(fans: JSONArray): List<DeviceCoolingFanStatus> =
        List(fans.length()) { index ->
            parseFan(
                item = fans.requiredObject(index, "cooling status fans"),
                label = "cooling status fans[$index]",
                expectedKeys = FAN_KEYS
            )
        }

    private fun parseFan(
        item: JSONObject,
        label: String,
        expectedKeys: Set<String>
    ): DeviceCoolingFanStatus {
        item.requireExactKeys(expectedKeys, label)
        val editable = item.requiredObject("editable")
        editable.requireExactKeys(EDITABLE_KEYS, "$label.editable")

        return DeviceCoolingFanStatus(
            index = item.requiredNonNegativeInt("index"),
            key = item.requiredNonBlankString("key"),
            name = item.requiredStringAllowEmpty("name"),
            displayName = item.requiredStringAllowEmpty("displayName"),
            profileManaged = item.requiredBoolean("profileManaged"),
            mode = DeviceCoolingMode.fromWire(item.requiredNonBlankString("regime")),
            channelKind = item.requiredNonBlankString("channelKind").also {
                require(it in CHANNEL_KINDS)
            },
            gpio = item.requiredInt("gpio"),
            ledcChannel = item.requiredInt("ledcChannel"),
            group = item.requiredInt("group"),
            valueNow = item.requiredFiniteDouble("valueNow"),
            valueAuto = item.requiredFiniteDouble("valueAuto"),
            valueManual = item.requiredFiniteDouble("valueManual"),
            valueMin = item.requiredFiniteDouble("valueMin"),
            valueMax = item.requiredFiniteDouble("valueMax"),
            manualTimeoutMs = item.requiredNonNegativeLong("manualTimeoutMs"),
            percentNow = item.requiredFiniteDouble("percentNow"),
            percentAuto = item.requiredFiniteDouble("percentAuto"),
            percentManual = item.requiredFiniteDouble("percentManual"),
            percentMin = item.requiredFiniteDouble("percentMin"),
            percentMax = item.requiredFiniteDouble("percentMax"),
            invert = item.requiredBoolean("invert"),
            pwmResolutionBits = item.requiredNonNegativeInt("pwmResolutionBits"),
            pwmFrequencyHz = item.requiredNonNegativeInt("pwmFrequencyHz"),
            editable = DeviceCoolingFanEditable(
                hardware = editable.requiredBoolean("hardware"),
                displayName = editable.requiredBoolean("displayName"),
                hardwareCalibration = editable.requiredBoolean("hardwareCalibration")
            )
        )
    }

    private fun parseStatusRules(rules: JSONArray): List<DeviceCoolingRuleStatus> =
        List(rules.length()) { index ->
            val item = rules.requiredObject(index, "cooling status rules")
            val label = "cooling status rules[$index]"
            item.requireExactKeys(RULE_STATUS_KEYS, label)

            DeviceCoolingRuleStatus(
                index = item.requiredNonNegativeInt("index"),
                name = item.requiredStringAllowEmpty("name"),
                enabled = item.requiredBoolean("enabled"),
                fanIndex = item.requiredInt("fanIndex").also { require(it >= -1) },
                channelKey = item.requiredStringAllowEmpty("channelKey"),
                bound = item.requiredBoolean("bound"),
                minTemperatureC = item.requiredFiniteDouble("minTemperatureC"),
                maxTemperatureC = item.requiredFiniteDouble("maxTemperatureC"),
                group = item.requiredInt("group"),
                sensorBindings = item.requiredNonNegativeIntList("sensorBindings")
            ).also {
                require(it.maxTemperatureC > it.minTemperatureC)
                requireUnique(it.sensorBindings, "$label sensor binding")
            }
        }

    private fun parseConfigSnapshot(data: JSONObject): DeviceCoolingConfigSnapshot {
        data.requireExactKeys(CONFIG_SNAPSHOT_KEYS, "cooling.config.apply.data.config")

        val fansJson = data.requiredArray("fans")
        val rulesJson = data.requiredArray("rules")
        val fans = List(fansJson.length()) { index ->
            val item = fansJson.requiredObject(index, "cooling config fans")
            DeviceCoolingFanConfigSnapshot(
                listIndex = item.requiredNonNegativeInt("listIndex"),
                fan = parseFan(
                    item = item,
                    label = "cooling config fans[$index]",
                    expectedKeys = CONFIG_FAN_KEYS
                )
            )
        }
        val rules = List(rulesJson.length()) { index ->
            parseConfigRule(
                rulesJson.requiredObject(index, "cooling config rules"),
                "cooling config rules[$index]"
            )
        }
        val fanOutputCount = data.requiredNonNegativeInt("fanOutputCount")
        val ruleCount = data.requiredNonNegativeInt("ruleCount")

        require(fanOutputCount == fans.size) {
            "cooling config fanOutputCount differs from fans size."
        }
        require(ruleCount == rules.size) {
            "cooling config ruleCount differs from rules size."
        }

        requireUnique(fans.map { it.fan.index }, "cooling config fan index")
        requireUnique(fans.map { it.fan.key }, "cooling config fan key")
        requireUnique(fans.map(DeviceCoolingFanConfigSnapshot::listIndex), "cooling fan listIndex")
        requireUnique(rules.map(DeviceCoolingRuleConfigSnapshot::index), "cooling config rule index")
        requireUnique(rules.map(DeviceCoolingRuleConfigSnapshot::listIndex), "cooling rule listIndex")
        require(fans.all { it.listIndex < fanOutputCount })
        require(rules.all { it.listIndex < ruleCount })

        val minTemperatureC = data.requiredFiniteDouble("minTemperatureC")
        val maxTemperatureC = data.requiredFiniteDouble("maxTemperatureC")
        require(maxTemperatureC > minTemperatureC)

        return DeviceCoolingConfigSnapshot(
            supported = data.requiredBoolean("supported"),
            fanSupported = data.requiredBoolean("fanSupported"),
            temperatureSupported = data.requiredBoolean("temperatureSupported"),
            fanOutputCount = fanOutputCount,
            ruleCount = ruleCount,
            mode = DeviceCoolingMode.fromWire(data.requiredNonBlankString("mode")),
            minTemperatureC = minTemperatureC,
            maxTemperatureC = maxTemperatureC,
            fixedSensorIndex = data.requiredInt("fixedSensorIndex").also { require(it >= -1) },
            hardwareEditable = data.requiredBoolean("hardwareEditable").also(::requireFalse),
            fanMappingEditable = data.requiredBoolean("fanMappingEditable").also(::requireFalse),
            sensorMappingEditable = data.requiredBoolean("sensorMappingEditable").also(::requireFalse),
            fans = fans,
            rules = rules
        )
    }

    private fun parseConfigRule(
        item: JSONObject,
        label: String
    ): DeviceCoolingRuleConfigSnapshot {
        item.requireExactKeys(CONFIG_RULE_KEYS, label)

        return DeviceCoolingRuleConfigSnapshot(
            listIndex = item.requiredNonNegativeInt("listIndex"),
            index = item.requiredNonNegativeInt("index"),
            name = item.requiredStringAllowEmpty("name"),
            enabled = item.requiredBoolean("enabled"),
            fanIndex = item.requiredInt("fanIndex").also { require(it >= -1) },
            channelKey = item.requiredStringAllowEmpty("channelKey"),
            bound = item.requiredBoolean("bound"),
            minTemperatureC = item.requiredFiniteDouble("minTemperatureC"),
            maxTemperatureC = item.requiredFiniteDouble("maxTemperatureC"),
            group = item.requiredInt("group")
        ).also {
            require(it.maxTemperatureC > it.minTemperatureC)
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

    private fun JSONObject.requiredNonNegativeIntList(key: String): List<Int> {
        val values = requiredArray(key)
        return List(values.length()) { index ->
            val number = values.get(index) as? Number
                ?: error("$key[$index] must be an integer.")
            val asLong = number.toLong()
            require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
            require(asLong in 0L..Int.MAX_VALUE.toLong())
            asLong.toInt()
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

    private fun requireFalse(value: Boolean) {
        require(!value)
    }

    private fun requireStatusEvent(value: String) {
        require(value == AqlWsContract.Event.STATUS_CHANGED)
    }

    private const val RUNTIME_TRANSPORT = "websocket"
    private const val COOLING_CONFIG_APPLY_COMMAND = "cooling.config.apply"

    private val CHANNEL_KINDS = setOf("gpio", "digital", "none")

    private val STATUS_KEYS = setOf(
        "supported", "fanSupported", "temperatureSupported", "fanOutputCount", "ruleCount",
        "mode", "minTemperatureC", "maxTemperatureC", "fixedSensorIndex", "uptimeMs",
        "fans", "rules", "runtime"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsModeSet",
        "supportsTemperatureRange", "supportsFanDisplayName", "hardwareEditable",
        "fanMappingEditable", "sensorMappingEditable", "event"
    )
    private val FAN_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime", "channelKind",
        "gpio", "ledcChannel", "group", "valueNow", "valueAuto", "valueManual",
        "valueMin", "valueMax", "manualTimeoutMs", "percentNow", "percentAuto",
        "percentManual", "percentMin", "percentMax", "invert", "pwmResolutionBits",
        "pwmFrequencyHz", "editable"
    )
    private val CONFIG_FAN_KEYS = FAN_KEYS + "listIndex"
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "hardwareCalibration")
    private val RULE_STATUS_KEYS = setOf(
        "index", "name", "enabled", "fanIndex", "channelKey", "bound",
        "minTemperatureC", "maxTemperatureC", "group", "sensorBindings"
    )
    private val CONFIG_RULE_KEYS = setOf(
        "listIndex", "index", "name", "enabled", "fanIndex", "channelKey", "bound",
        "minTemperatureC", "maxTemperatureC", "group"
    )
    private val CONFIG_APPLY_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "runtimeTransport", "command",
        "event", "appliedGlobalConfig", "appliedFanDisplayNames", "config"
    )
    private val CONFIG_SNAPSHOT_KEYS = setOf(
        "supported", "fanSupported", "temperatureSupported", "fanOutputCount", "ruleCount",
        "mode", "minTemperatureC", "maxTemperatureC", "fixedSensorIndex",
        "hardwareEditable", "fanMappingEditable", "sensorMappingEditable", "fans", "rules"
    )
}
