package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceCoolingModeParser {
    fun parse(value: String): DeviceCoolingMode = requireNotNull(
        DeviceCoolingMode.values().singleOrNull { mode -> mode.wireValue == value }
    ) { "Unknown firmware cooling mode: $value" }
}

internal object DeviceCoolingTemperatureParser {
    private val KEYS = setOf(
        "sensorIndex",
        "readingValid",
        "temperatureC",
        "sampledAtMs"
    )

    fun parse(data: JSONObject): DeviceCoolingTemperatureSnapshot {
        data.requireCoolingKeys(KEYS, "cooling temperature")
        val readingValid = data.requireCoolingBoolean("readingValid")
        val temperatureC = data.requireCoolingNullableDouble(
            "temperatureC",
            COOLING_MIN_VALID_TEMPERATURE_C,
            COOLING_MAX_VALID_TEMPERATURE_C
        )
        val snapshot = DeviceCoolingTemperatureSnapshot(
            sensorIndex = data.requireCoolingInt(
                "sensorIndex",
                COOLING_UNAVAILABLE_INDEX,
                DeviceCoolingRuntimeContract.Limit.MAX_SENSOR_INDEX
            ),
            readingValid = readingValid,
            temperatureC = temperatureC,
            sampledAtMs = data.requireCoolingLong(
                "sampledAtMs",
                minimum = COOLING_NON_NEGATIVE_LONG,
                maximum = COOLING_DEVICE_UPTIME_MAX_MS
            )
        )
        require(snapshot.readingValid == (snapshot.temperatureC != null)) {
            "temperatureC nullability differs from readingValid."
        }
        if (snapshot.readingValid) {
            val measuredTemperatureC = requireNotNull(snapshot.temperatureC)
            require(measuredTemperatureC > COOLING_MIN_VALID_TEMPERATURE_C)
            require(measuredTemperatureC < COOLING_MAX_VALID_TEMPERATURE_C)
            require(snapshot.sensorIndex >= COOLING_MIN_INDEX)
            require(snapshot.sampledAtMs > COOLING_NON_NEGATIVE_LONG)
        }
        return snapshot
    }
}

internal object DeviceCoolingRuntimeCapabilitiesParser {
    private val KEYS = setOf(
        "module",
        "readOnly",
        "supportsConfigApply",
        "supportsModeSet",
        "supportsTemperatureRange",
        "supportsFanDisplayName",
        "hardwareEditable",
        "fanMappingEditable",
        "sensorMappingEditable",
        "event"
    )

    fun parse(data: JSONObject): DeviceCoolingRuntimeCapabilities {
        data.requireCoolingKeys(KEYS, "cooling runtime capabilities")
        return DeviceCoolingRuntimeCapabilities(
            module = data.requireCoolingText("module"),
            readOnly = data.requireCoolingBoolean("readOnly"),
            supportsConfigApply = data.requireCoolingBoolean("supportsConfigApply"),
            supportsModeSet = data.requireCoolingBoolean("supportsModeSet"),
            supportsTemperatureRange = data.requireCoolingBoolean("supportsTemperatureRange"),
            supportsFanDisplayName = data.requireCoolingBoolean("supportsFanDisplayName"),
            hardwareEditable = data.requireCoolingBoolean("hardwareEditable"),
            fanMappingEditable = data.requireCoolingBoolean("fanMappingEditable"),
            sensorMappingEditable = data.requireCoolingBoolean("sensorMappingEditable"),
            event = data.requireCoolingText("event")
        ).also { runtime ->
            require(runtime.module == DeviceCoolingRuntimeContract.Literal.RUNTIME_MODULE)
            require(!runtime.readOnly)
            require(runtime.supportsConfigApply)
            require(runtime.supportsModeSet)
            require(runtime.supportsTemperatureRange)
            require(!runtime.hardwareEditable)
            require(!runtime.fanMappingEditable)
            require(!runtime.sensorMappingEditable)
            require(runtime.event == DeviceCoolingRuntimeContract.STATUS_EVENT)
        }
    }
}

internal object DeviceCoolingFanParser {
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "hardwareCalibration")
    private val STATUS_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime",
        "channelKind", "gpio", "ledcChannel", "group", "valueNow", "valueAuto",
        "valueManual", "valueMin", "valueMax", "manualTimeoutMs", "percentNow",
        "percentAuto", "percentManual", "percentMin", "percentMax", "invert",
        "pwmResolutionBits", "pwmFrequencyHz", "editable"
    )
    private val CONFIG_KEYS = STATUS_KEYS + "listIndex"
    private val CHANNEL_KINDS = setOf(
        DeviceCoolingRuntimeContract.Literal.CHANNEL_KIND_GPIO,
        DeviceCoolingRuntimeContract.Literal.CHANNEL_KIND_DIGITAL,
        DeviceCoolingRuntimeContract.Literal.CHANNEL_KIND_NONE
    )

    fun parseStatus(data: JSONObject): DeviceCoolingFanStatus =
        parse(data, STATUS_KEYS, "cooling status fan")

    fun parseConfig(data: JSONObject): DeviceCoolingFanConfigSnapshot {
        data.requireCoolingKeys(CONFIG_KEYS, "cooling config fan")
        return DeviceCoolingFanConfigSnapshot(
            listIndex = data.requireCoolingInt("listIndex", minimum = COOLING_MIN_INDEX),
            fan = parse(data, CONFIG_KEYS, "cooling config fan")
        )
    }

    private fun parse(
        data: JSONObject,
        expectedKeys: Set<String>,
        label: String
    ): DeviceCoolingFanStatus {
        data.requireCoolingKeys(expectedKeys, label)
        val fan = DeviceCoolingFanStatus(
            index = data.requireCoolingInt("index", minimum = COOLING_MIN_INDEX),
            key = data.requireCoolingText("key"),
            name = data.requireCoolingText("name"),
            displayName = data.requireCoolingText("displayName"),
            profileManaged = data.requireCoolingBoolean("profileManaged"),
            mode = DeviceCoolingModeParser.parse(data.requireCoolingText("regime")),
            channelKind = data.requireCoolingText("channelKind"),
            gpio = data.requireCoolingInt("gpio"),
            ledcChannel = data.requireCoolingInt("ledcChannel"),
            group = data.requireCoolingInt("group"),
            valueNow = data.requireCoolingDouble(
                "valueNow",
                COOLING_NORMALIZED_MIN,
                COOLING_NORMALIZED_MAX
            ),
            valueAuto = data.requireCoolingDouble(
                "valueAuto",
                COOLING_NORMALIZED_MIN,
                COOLING_NORMALIZED_MAX
            ),
            valueManual = data.requireCoolingDouble(
                "valueManual",
                COOLING_MANUAL_INACTIVE_VALUE,
                COOLING_NORMALIZED_MAX
            ),
            valueMin = data.requireCoolingDouble(
                "valueMin",
                COOLING_NORMALIZED_MIN,
                COOLING_NORMALIZED_MAX
            ),
            valueMax = data.requireCoolingDouble(
                "valueMax",
                COOLING_NORMALIZED_MIN,
                COOLING_NORMALIZED_MAX
            ),
            manualTimeoutMs = data.requireCoolingLong(
                "manualTimeoutMs",
                minimum = COOLING_NON_NEGATIVE_LONG,
                maximum = COOLING_DEVICE_UPTIME_MAX_MS
            ),
            percentNow = data.requireCoolingDouble(
                "percentNow",
                COOLING_PERCENT_MIN,
                COOLING_PERCENT_MAX
            ),
            percentAuto = data.requireCoolingDouble(
                "percentAuto",
                COOLING_PERCENT_MIN,
                COOLING_PERCENT_MAX
            ),
            percentManual = data.requireCoolingDouble(
                "percentManual",
                COOLING_MANUAL_INACTIVE_PERCENT,
                COOLING_PERCENT_MAX
            ),
            percentMin = data.requireCoolingDouble(
                "percentMin",
                COOLING_PERCENT_MIN,
                COOLING_PERCENT_MAX
            ),
            percentMax = data.requireCoolingDouble(
                "percentMax",
                COOLING_PERCENT_MIN,
                COOLING_PERCENT_MAX
            ),
            invert = data.requireCoolingBoolean("invert"),
            pwmResolutionBits = data.requireCoolingInt(
                "pwmResolutionBits",
                minimum = COOLING_MIN_COUNT
            ),
            pwmFrequencyHz = data.requireCoolingInt(
                "pwmFrequencyHz",
                minimum = COOLING_MIN_COUNT
            ),
            editable = parseEditable(data.requireCoolingObject("editable"))
        )
        validate(fan)
        return fan
    }

    private fun parseEditable(data: JSONObject): DeviceCoolingFanEditable {
        data.requireCoolingKeys(EDITABLE_KEYS, "cooling fan editable")
        return DeviceCoolingFanEditable(
            hardware = data.requireCoolingBoolean("hardware"),
            displayName = data.requireCoolingBoolean("displayName"),
            hardwareCalibration = data.requireCoolingBoolean("hardwareCalibration")
        ).also { editable ->
            require(!editable.hardware)
            require(!editable.hardwareCalibration)
        }
    }

    private fun validate(fan: DeviceCoolingFanStatus) {
        require(fan.channelKind in CHANNEL_KINDS)
        require(fan.valueMin <= fan.valueMax)
        require(fan.percentMin <= fan.percentMax)
        require(coolingValuesEquivalent(fan.percentNow, fan.valueNow * COOLING_PERCENT_SCALE))
        require(coolingValuesEquivalent(fan.percentAuto, fan.valueAuto * COOLING_PERCENT_SCALE))
        require(
            coolingValuesEquivalent(fan.percentManual, fan.valueManual * COOLING_PERCENT_SCALE)
        )
        require(coolingValuesEquivalent(fan.percentMin, fan.valueMin * COOLING_PERCENT_SCALE))
        require(coolingValuesEquivalent(fan.percentMax, fan.valueMax * COOLING_PERCENT_SCALE))
    }
}

internal object DeviceCoolingRuleParser {
    private val STATUS_KEYS = setOf(
        "index", "name", "enabled", "fanIndex", "channelKey", "bound",
        "minTemperatureC", "maxTemperatureC", "group", "sensorBindings"
    )
    private val CONFIG_KEYS = setOf(
        "listIndex", "index", "name", "enabled", "fanIndex", "channelKey",
        "bound", "minTemperatureC", "maxTemperatureC", "group"
    )

    fun parseStatus(data: JSONObject): DeviceCoolingRuleStatus {
        data.requireCoolingKeys(STATUS_KEYS, "cooling status rule")
        val bindings = parseBindings(data.requireCoolingArray("sensorBindings"))
        return DeviceCoolingRuleStatus(
            index = data.requireCoolingInt("index", minimum = COOLING_MIN_INDEX),
            name = data.requireCoolingText("name"),
            enabled = data.requireCoolingBoolean("enabled"),
            fanIndex = data.requireCoolingInt("fanIndex", minimum = COOLING_MIN_INDEX),
            channelKey = data.requireCoolingText("channelKey"),
            bound = data.requireCoolingBoolean("bound"),
            minTemperatureC = data.requireCoolingDouble(
                "minTemperatureC",
                DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C,
                DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C
            ),
            maxTemperatureC = data.requireCoolingDouble(
                "maxTemperatureC",
                DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C,
                DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C
            ),
            group = data.requireCoolingInt("group"),
            sensorBindings = bindings
        ).also(::validateStatus)
    }

    fun parseConfig(data: JSONObject): DeviceCoolingRuleConfigSnapshot {
        data.requireCoolingKeys(CONFIG_KEYS, "cooling config rule")
        return DeviceCoolingRuleConfigSnapshot(
            listIndex = data.requireCoolingInt("listIndex", minimum = COOLING_MIN_INDEX),
            index = data.requireCoolingInt("index", minimum = COOLING_MIN_INDEX),
            name = data.requireCoolingText("name"),
            enabled = data.requireCoolingBoolean("enabled"),
            fanIndex = data.requireCoolingInt("fanIndex", minimum = COOLING_MIN_INDEX),
            channelKey = data.requireCoolingText("channelKey"),
            bound = data.requireCoolingBoolean("bound"),
            minTemperatureC = data.requireCoolingDouble(
                "minTemperatureC",
                DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C,
                DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C
            ),
            maxTemperatureC = data.requireCoolingDouble(
                "maxTemperatureC",
                DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C,
                DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C
            ),
            group = data.requireCoolingInt("group")
        ).also { rule -> require(rule.minTemperatureC < rule.maxTemperatureC) }
    }

    private fun parseBindings(values: JSONArray): List<Int> = List(values.length()) { index ->
        values.requireCoolingInt(index, minimum = COOLING_MIN_INDEX)
    }.also { bindings -> require(bindings.distinct().size == bindings.size) }

    private fun validateStatus(rule: DeviceCoolingRuleStatus) {
        require(rule.minTemperatureC < rule.maxTemperatureC)
    }
}
