package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

object DeviceLightThermalStatusParser {
    private const val SCHEMA = "aql.light-thermal.v1"
    private const val SCHEMA_VERSION = 1

    private val STATUS_KEYS = setOf(
        "schema",
        "schemaVersion",
        "productKey",
        "uptimeMs",
        "topology",
        "config",
        "temperature",
        "lightProtection",
        "fans",
        "runtime"
    )
    private val TOPOLOGY_KEYS = setOf("fanOutputCount", "temperatureSensorCount")
    private val CONFIG_KEYS = setOf("mode", "minTemperatureC", "maxTemperatureC")
    private val TEMPERATURE_KEYS = setOf(
        "sensorKey",
        "sensorIndex",
        "readingValid",
        "temperatureC",
        "sampledAtMs"
    )
    private val PROTECTION_KEYS = setOf("enabled", "active", "thresholdC")
    private val FAN_KEYS = setOf(
        "fanKey",
        "index",
        "name",
        "regime",
        "valueNow",
        "valueAuto",
        "percentNow",
        "percentAuto",
        "hardware"
    )
    private val FAN_HARDWARE_KEYS = setOf(
        "editable",
        "gpio",
        "ledcChannel",
        "pwmFrequencyHz",
        "pwmResolutionBits",
        "invert",
        "pwmOutputHealth",
        "health",
        "physicalFeedbackAvailable"
    )
    private val RUNTIME_KEYS = setOf(
        "event",
        "statusEvent",
        "sensorFailSafeActive",
        "automaticOutputCycleHealthy",
        "hardwareEditable",
        "fanMappingEditable",
        "sensorMappingEditable"
    )

    fun parse(data: JSONObject): DeviceLightThermalStatus {
        data.requireExactKeys(STATUS_KEYS, "light thermal status")
        val schema = data.requireText("schema")
        val schemaVersion = data.requireInt("schemaVersion", minimum = 1)
        require(schema == SCHEMA) { "Unsupported light thermal schema: $schema" }
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported light thermal schemaVersion: $schemaVersion"
        }

        val topology = parseTopology(data.requireObject("topology"))
        val config = parseConfig(data.requireObject("config"))
        val temperature = parseTemperature(data.requireObject("temperature"))
        val lightProtection = parseProtection(data.requireObject("lightProtection"))
        val fans = parseFans(data.requireArray("fans"))
        val runtime = parseRuntime(data.requireObject("runtime"))

        require(topology.fanOutputCount == fans.size) {
            "light thermal fanOutputCount does not match fans[]"
        }
        require(config.minTemperatureC < config.maxTemperatureC) {
            "light thermal minTemperatureC must be below maxTemperatureC"
        }
        require(temperature.readingValid == (temperature.temperatureC != null)) {
            "light thermal temperature validity does not match temperatureC"
        }
        require(fans.map(DeviceLightThermalFanStatus::fanKey).distinct().size == fans.size) {
            "light thermal fan keys must be unique"
        }
        require(fans.map(DeviceLightThermalFanStatus::index).distinct().size == fans.size) {
            "light thermal fan indexes must be unique"
        }

        return DeviceLightThermalStatus(
            schema = schema,
            schemaVersion = schemaVersion,
            productKey = data.requireText("productKey"),
            uptimeMs = data.requireLong("uptimeMs", minimum = 0L),
            topology = topology,
            config = config,
            temperature = temperature,
            lightProtection = lightProtection,
            fans = fans,
            runtime = runtime
        )
    }

    private fun parseTopology(data: JSONObject): DeviceLightThermalTopology {
        data.requireExactKeys(TOPOLOGY_KEYS, "light thermal topology")
        return DeviceLightThermalTopology(
            fanOutputCount = data.requireInt("fanOutputCount", minimum = 0),
            temperatureSensorCount = data.requireInt("temperatureSensorCount", minimum = 0)
        )
    }

    private fun parseConfig(data: JSONObject): DeviceLightThermalConfig {
        data.requireExactKeys(CONFIG_KEYS, "light thermal config")
        return DeviceLightThermalConfig(
            mode = requireNotNull(DeviceLightThermalMode.fromWireExact(data.requireText("mode"))) {
                "Unsupported light thermal mode."
            },
            minTemperatureC = data.requireFiniteDouble("minTemperatureC"),
            maxTemperatureC = data.requireFiniteDouble("maxTemperatureC")
        )
    }

    private fun parseTemperature(data: JSONObject): DeviceLightThermalTemperature {
        data.requireExactKeys(TEMPERATURE_KEYS, "light thermal temperature")
        return DeviceLightThermalTemperature(
            sensorKey = data.requireText("sensorKey"),
            sensorIndex = data.requireInt("sensorIndex", minimum = 0),
            readingValid = data.requireBoolean("readingValid"),
            temperatureC = data.requireNullableFiniteDouble("temperatureC"),
            sampledAtMs = data.requireLong("sampledAtMs", minimum = 0L)
        )
    }

    private fun parseProtection(data: JSONObject): DeviceLightThermalProtection {
        data.requireExactKeys(PROTECTION_KEYS, "light thermal protection")
        return DeviceLightThermalProtection(
            enabled = data.requireBoolean("enabled"),
            active = data.requireBoolean("active"),
            thresholdC = data.requireFiniteDouble("thresholdC")
        )
    }

    private fun parseFans(data: JSONArray): List<DeviceLightThermalFanStatus> =
        List(data.length()) { index ->
            val value = data.get(index)
            require(value is JSONObject) { "light thermal fans[$index] must be an object" }
            parseFan(value)
        }

    private fun parseFan(data: JSONObject): DeviceLightThermalFanStatus {
        data.requireExactKeys(FAN_KEYS, "light thermal fan")
        return DeviceLightThermalFanStatus(
            fanKey = data.requireText("fanKey"),
            index = data.requireInt("index", minimum = 0),
            name = data.requireText("name"),
            regime = data.requireText("regime"),
            valueNow = data.requireFiniteDouble("valueNow"),
            valueAuto = data.requireFiniteDouble("valueAuto"),
            percentNow = data.requireFiniteDouble("percentNow"),
            percentAuto = data.requireFiniteDouble("percentAuto"),
            hardware = parseFanHardware(data.requireObject("hardware"))
        )
    }

    private fun parseFanHardware(data: JSONObject): DeviceLightThermalFanHardware {
        data.requireExactKeys(FAN_HARDWARE_KEYS, "light thermal fan hardware")
        return DeviceLightThermalFanHardware(
            editable = data.requireBoolean("editable"),
            gpio = data.requireInt("gpio", minimum = 0),
            ledcChannel = data.requireInt("ledcChannel", minimum = 0),
            pwmFrequencyHz = data.requireLong("pwmFrequencyHz", minimum = 1L),
            pwmResolutionBits = data.requireInt("pwmResolutionBits", minimum = 1),
            invert = data.requireBoolean("invert"),
            pwmOutputHealth = data.requireText("pwmOutputHealth"),
            health = data.requireText("health"),
            physicalFeedbackAvailable = data.requireBoolean("physicalFeedbackAvailable")
        )
    }

    private fun parseRuntime(data: JSONObject): DeviceLightThermalRuntimeCapabilities {
        data.requireExactKeys(RUNTIME_KEYS, "light thermal runtime")
        return DeviceLightThermalRuntimeCapabilities(
            event = data.requireText("event"),
            statusEvent = data.requireText("statusEvent"),
            sensorFailSafeActive = data.requireBoolean("sensorFailSafeActive"),
            automaticOutputCycleHealthy = data.requireBoolean("automaticOutputCycleHealthy"),
            hardwareEditable = data.requireBoolean("hardwareEditable"),
            fanMappingEditable = data.requireBoolean("fanMappingEditable"),
            sensorMappingEditable = data.requireBoolean("sensorMappingEditable")
        )
    }
}

private fun JSONObject.requireExactKeys(expected: Set<String>, context: String) {
    val actual = linkedSetOf<String>()
    val iterator = keys()
    while (iterator.hasNext()) actual += iterator.next()
    require(actual == expected) {
        "$context keys mismatch; expected=$expected actual=$actual"
    }
}

private fun JSONObject.requireObject(key: String): JSONObject {
    require(has(key) && !isNull(key)) { "$key is required" }
    return get(key) as? JSONObject ?: error("$key must be an object")
}

private fun JSONObject.requireArray(key: String): JSONArray {
    require(has(key) && !isNull(key)) { "$key is required" }
    return get(key) as? JSONArray ?: error("$key must be an array")
}

private fun JSONObject.requireText(key: String): String {
    require(has(key) && !isNull(key)) { "$key is required" }
    val value = get(key) as? String ?: error("$key must be a string")
    require(value.isNotEmpty() && value == value.trim()) { "$key must be exact non-empty text" }
    return value
}

private fun JSONObject.requireBoolean(key: String): Boolean {
    require(has(key) && !isNull(key)) { "$key is required" }
    return get(key) as? Boolean ?: error("$key must be a boolean")
}

private fun JSONObject.requireInt(key: String, minimum: Int): Int {
    val value = requireNumber(key).toDouble()
    require(value.isFinite() && value % 1.0 == 0.0) { "$key must be an integer" }
    require(value >= minimum.toDouble() && value <= Int.MAX_VALUE.toDouble()) {
        "$key is outside the supported integer range"
    }
    return value.toInt()
}

private fun JSONObject.requireLong(key: String, minimum: Long): Long {
    val number = requireNumber(key)
    val value = number.toDouble()
    require(value.isFinite() && value % 1.0 == 0.0) { "$key must be an integer" }
    val converted = number.toLong()
    require(converted >= minimum) { "$key is below the supported range" }
    return converted
}

private fun JSONObject.requireFiniteDouble(key: String): Double {
    val value = requireNumber(key).toDouble()
    require(value.isFinite()) { "$key must be finite" }
    return value
}

private fun JSONObject.requireNullableFiniteDouble(key: String): Double? {
    require(has(key)) { "$key is required" }
    if (isNull(key)) return null
    return requireFiniteDouble(key)
}

private fun JSONObject.requireNumber(key: String): Number {
    require(has(key) && !isNull(key)) { "$key is required" }
    return get(key) as? Number ?: error("$key must be a number")
}
