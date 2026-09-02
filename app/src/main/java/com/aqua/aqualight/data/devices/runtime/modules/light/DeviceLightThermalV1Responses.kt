package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

data class DeviceLightThermalTopology(
    val fanOutputCount: Int,
    val temperatureSensorCount: Int
)

data class DeviceLightThermalConfig(
    val mode: DeviceLightThermalMode,
    val minTemperatureC: Double,
    val maxTemperatureC: Double
)

data class DeviceLightThermalTemperature(
    val sensorKey: String,
    val sensorIndex: Int,
    val readingValid: Boolean,
    val temperatureC: Double?,
    val sampledAtMs: Long
)

data class DeviceLightThermalProtection(
    val enabled: Boolean?,
    val active: Boolean,
    val thresholdC: Double
)

data class DeviceLightThermalFanHardware(
    val editable: Boolean?,
    val gpio: Int?,
    val ledcChannel: Int?,
    val pwmFrequencyHz: Int?,
    val pwmResolutionBits: Int?,
    val invert: Boolean?,
    val pwmOutputHealth: String,
    val health: String,
    val physicalFeedbackAvailable: Boolean
)

data class DeviceLightThermalFan(
    val fanKey: String,
    val index: Int?,
    val name: String?,
    val regime: String,
    val valueNow: Double?,
    val valueAuto: Double?,
    val percentNow: Double,
    val percentAuto: Double,
    val hardware: DeviceLightThermalFanHardware
)

data class DeviceLightThermalRuntime(
    val event: String,
    val statusEvent: String,
    val sensorFailSafeActive: Boolean,
    val automaticOutputCycleHealthy: Boolean,
    val hardwareEditable: Boolean,
    val fanMappingEditable: Boolean,
    val sensorMappingEditable: Boolean
)

data class DeviceLightThermalStatus(
    val schema: String,
    val schemaVersion: Int,
    val productKey: String,
    val uptimeMs: Long,
    val topology: DeviceLightThermalTopology,
    val config: DeviceLightThermalConfig,
    val temperature: DeviceLightThermalTemperature,
    val lightProtection: DeviceLightThermalProtection,
    val fans: List<DeviceLightThermalFan>,
    val runtime: DeviceLightThermalRuntime
)

data class DeviceLightThermalConfigApplyResult(
    val schema: String,
    val schemaVersion: Int,
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val command: String,
    val event: String,
    val status: DeviceLightThermalStatus
)

data class DeviceLightThermalTelemetry(
    val schema: String,
    val schemaVersion: Int,
    val productKey: String,
    val uptimeMs: Long,
    val mode: DeviceLightThermalMode,
    val sensorFailSafeActive: Boolean,
    val automaticOutputCycleHealthy: Boolean,
    val temperature: DeviceLightThermalTemperature,
    val lightProtection: DeviceLightThermalProtection,
    val fans: List<DeviceLightThermalFan>
)

object DeviceLightThermalV1ResponseParser {
    fun parseStatus(data: JSONObject): DeviceLightThermalStatus {
        data.requireThermalKeys(STATUS_KEYS, "light.thermal.status")
        val status = DeviceLightThermalStatus(
            schema = data.requireThermalText("schema"),
            schemaVersion = data.requireThermalInt("schemaVersion", 1, 1),
            productKey = data.requireThermalText("productKey"),
            uptimeMs = data.requireThermalLong("uptimeMs"),
            topology = parseTopology(data.requireThermalObject("topology")),
            config = parseConfig(data.requireThermalObject("config")),
            temperature = parseTemperature(data.requireThermalObject("temperature")),
            lightProtection = parseProtection(
                data.requireThermalObject("lightProtection"),
                statusShape = true
            ),
            fans = parseFans(data.requireThermalArray("fans"), statusShape = true),
            runtime = parseRuntime(data.requireThermalObject("runtime"))
        )
        require(status.schema == DeviceLightThermalV1Contract.SCHEMA)
        require(status.productKey == DeviceLightThermalV1Contract.PRODUCT_KEY)
        require(status.topology.fanOutputCount == DeviceLightThermalV1Contract.FAN_OUTPUT_CAPACITY)
        require(
            status.topology.temperatureSensorCount ==
                DeviceLightThermalV1Contract.TEMPERATURE_SENSOR_CAPACITY
        )
        validateFanKeys(status.fans)
        return status
    }

    fun parseConfigApply(data: JSONObject): DeviceLightThermalConfigApplyResult {
        data.requireThermalKeys(CONFIG_APPLY_KEYS, "light.thermal.config.apply")
        return DeviceLightThermalConfigApplyResult(
            schema = data.requireThermalText("schema"),
            schemaVersion = data.requireThermalInt("schemaVersion", 1, 1),
            operation = data.requireThermalText("operation"),
            changed = data.requireThermalBoolean("changed"),
            saved = data.requireThermalBoolean("saved"),
            saveRequested = data.requireThermalBoolean("saveRequested"),
            command = data.requireThermalText("command"),
            event = data.requireThermalText("event"),
            status = parseStatus(data.requireThermalObject("status"))
        ).also { result ->
            require(result.schema == DeviceLightThermalV1Contract.SCHEMA)
            require(result.operation == "configApply")
            require(result.saved == result.saveRequested)
            require(result.command == "light.thermal.config.apply")
            require(result.event == DeviceLightThermalV1Contract.Event.STATUS_CHANGED)
        }
    }

    fun parseTelemetry(data: JSONObject): DeviceLightThermalTelemetry {
        data.requireThermalKeys(TELEMETRY_KEYS, "light.thermal.telemetry")
        return DeviceLightThermalTelemetry(
            schema = data.requireThermalText("schema"),
            schemaVersion = data.requireThermalInt("schemaVersion", 1, 1),
            productKey = data.requireThermalText("productKey"),
            uptimeMs = data.requireThermalLong("uptimeMs"),
            mode = parseMode(data.requireThermalText("mode")),
            sensorFailSafeActive = data.requireThermalBoolean("sensorFailSafeActive"),
            automaticOutputCycleHealthy =
                data.requireThermalBoolean("automaticOutputCycleHealthy"),
            temperature = parseTemperature(data.requireThermalObject("temperature")),
            lightProtection = parseProtection(
                data.requireThermalObject("lightProtection"),
                statusShape = false
            ),
            fans = parseFans(data.requireThermalArray("fans"), statusShape = false)
        ).also { telemetry ->
            require(telemetry.schema == DeviceLightThermalV1Contract.SCHEMA)
            require(telemetry.productKey == DeviceLightThermalV1Contract.PRODUCT_KEY)
            validateFanKeys(telemetry.fans)
        }
    }

    private fun parseTopology(data: JSONObject): DeviceLightThermalTopology {
        data.requireThermalKeys(TOPOLOGY_KEYS, "light thermal topology")
        return DeviceLightThermalTopology(
            fanOutputCount = data.requireThermalInt("fanOutputCount", 0, 2),
            temperatureSensorCount = data.requireThermalInt("temperatureSensorCount", 0, 1)
        )
    }

    private fun parseConfig(data: JSONObject): DeviceLightThermalConfig {
        data.requireThermalKeys(CONFIG_KEYS, "light thermal config")
        val config = DeviceLightThermalConfig(
            mode = parseMode(data.requireThermalText("mode")),
            minTemperatureC = data.requireThermalDouble("minTemperatureC", 0.0, 80.0),
            maxTemperatureC = data.requireThermalDouble("maxTemperatureC", 1.0, 90.0)
        )
        require(config.minTemperatureC < config.maxTemperatureC)
        return config
    }

    private fun parseTemperature(data: JSONObject): DeviceLightThermalTemperature {
        data.requireThermalKeys(TEMPERATURE_KEYS, "light thermal temperature")
        val valid = data.requireThermalBoolean("readingValid")
        val temperature = data.requireThermalNullableDouble("temperatureC", -40.0, 125.0)
        require(valid == (temperature != null))
        return DeviceLightThermalTemperature(
            sensorKey = data.requireThermalText("sensorKey"),
            sensorIndex = data.requireThermalInt("sensorIndex", -1, 7),
            readingValid = valid,
            temperatureC = temperature,
            sampledAtMs = data.requireThermalLong("sampledAtMs")
        ).also { sample ->
            require(sample.sensorKey == DeviceLightThermalV1Contract.FIXTURE_SENSOR_KEY)
        }
    }

    private fun parseProtection(
        data: JSONObject,
        statusShape: Boolean
    ): DeviceLightThermalProtection {
        data.requireThermalKeys(
            if (statusShape) PROTECTION_STATUS_KEYS else PROTECTION_EVENT_KEYS,
            "light thermal protection"
        )
        return DeviceLightThermalProtection(
            enabled = if (statusShape) data.requireThermalBoolean("enabled") else null,
            active = data.requireThermalBoolean("active"),
            thresholdC = data.requireThermalDouble("thresholdC", 50.0, 70.0)
        )
    }

    private fun parseFans(data: JSONArray, statusShape: Boolean): List<DeviceLightThermalFan> =
        List(data.length()) { index ->
            val item = data.get(index) as? JSONObject ?: error("fans[$index] must be an object.")
            if (statusShape) parseStatusFan(item) else parseEventFan(item)
        }

    private fun parseStatusFan(data: JSONObject): DeviceLightThermalFan {
        data.requireThermalKeys(FAN_STATUS_KEYS, "light thermal status fan")
        val hardware = data.requireThermalObject("hardware")
        hardware.requireThermalKeys(FAN_HARDWARE_KEYS, "light thermal fan hardware")
        return DeviceLightThermalFan(
            fanKey = data.requireThermalText("fanKey"),
            index = data.requireThermalInt("index", 0, 1),
            name = data.requireThermalText("name"),
            regime = data.requireThermalText("regime"),
            valueNow = data.requireThermalDouble("valueNow", 0.0, 1.0),
            valueAuto = data.requireThermalDouble("valueAuto", 0.0, 1.0),
            percentNow = data.requireThermalDouble("percentNow", 0.0, 100.0),
            percentAuto = data.requireThermalDouble("percentAuto", 0.0, 100.0),
            hardware = DeviceLightThermalFanHardware(
                editable = hardware.requireThermalBoolean("editable"),
                gpio = hardware.requireThermalInt("gpio", 0, 48),
                ledcChannel = hardware.requireThermalInt("ledcChannel", 0, 15),
                pwmFrequencyHz = hardware.requireThermalInt("pwmFrequencyHz", 1, Int.MAX_VALUE),
                pwmResolutionBits =
                    hardware.requireThermalInt("pwmResolutionBits", 1, 16),
                invert = hardware.requireThermalBoolean("invert"),
                pwmOutputHealth = hardware.requireThermalText("pwmOutputHealth"),
                health = hardware.requireThermalText("health"),
                physicalFeedbackAvailable =
                    hardware.requireThermalBoolean("physicalFeedbackAvailable")
            )
        ).also(::validateFan)
    }

    private fun parseEventFan(data: JSONObject): DeviceLightThermalFan {
        data.requireThermalKeys(FAN_EVENT_KEYS, "light thermal telemetry fan")
        return DeviceLightThermalFan(
            fanKey = data.requireThermalText("fanKey"),
            index = null,
            name = null,
            regime = data.requireThermalText("regime"),
            valueNow = null,
            valueAuto = null,
            percentNow = data.requireThermalDouble("percentNow", 0.0, 100.0),
            percentAuto = data.requireThermalDouble("percentAuto", 0.0, 100.0),
            hardware = DeviceLightThermalFanHardware(
                editable = null,
                gpio = null,
                ledcChannel = null,
                pwmFrequencyHz = null,
                pwmResolutionBits = null,
                invert = null,
                pwmOutputHealth = data.requireThermalText("pwmOutputHealth"),
                health = data.requireThermalText("health"),
                physicalFeedbackAvailable =
                    data.requireThermalBoolean("physicalFeedbackAvailable")
            )
        ).also(::validateFan)
    }

    private fun parseRuntime(data: JSONObject): DeviceLightThermalRuntime {
        data.requireThermalKeys(RUNTIME_KEYS, "light thermal runtime")
        return DeviceLightThermalRuntime(
            event = data.requireThermalText("event"),
            statusEvent = data.requireThermalText("statusEvent"),
            sensorFailSafeActive = data.requireThermalBoolean("sensorFailSafeActive"),
            automaticOutputCycleHealthy =
                data.requireThermalBoolean("automaticOutputCycleHealthy"),
            hardwareEditable = data.requireThermalBoolean("hardwareEditable"),
            fanMappingEditable = data.requireThermalBoolean("fanMappingEditable"),
            sensorMappingEditable = data.requireThermalBoolean("sensorMappingEditable")
        ).also { runtime ->
            require(runtime.event == DeviceLightThermalV1Contract.Event.TELEMETRY_CHANGED)
            require(runtime.statusEvent == DeviceLightThermalV1Contract.Event.STATUS_CHANGED)
            require(!runtime.hardwareEditable)
            require(!runtime.fanMappingEditable)
            require(!runtime.sensorMappingEditable)
        }
    }

    private fun validateFan(fan: DeviceLightThermalFan) {
        require(fan.regime in setOf("Auto", "On", "Off"))
        require(fan.hardware.pwmOutputHealth in setOf("OK", "FAULT"))
        require(fan.hardware.health in setOf("UNVERIFIED", "HARDWARE_FAULT"))
        require(!fan.hardware.physicalFeedbackAvailable)
        fan.hardware.editable?.let { require(!it) }
    }

    private fun validateFanKeys(fans: List<DeviceLightThermalFan>) {
        require(
            fans.map(DeviceLightThermalFan::fanKey) == listOf(
                DeviceLightThermalV1Contract.FAN_1_KEY,
                DeviceLightThermalV1Contract.FAN_2_KEY
            )
        )
    }

    private fun parseMode(value: String): DeviceLightThermalMode =
        DeviceLightThermalMode.entries.singleOrNull { it.wireValue == value }
            ?: error("Unknown light thermal mode: $value")

    private val STATUS_KEYS = setOf(
        "schema", "schemaVersion", "productKey", "uptimeMs", "topology", "config",
        "temperature", "lightProtection", "fans", "runtime"
    )
    private val CONFIG_APPLY_KEYS = setOf(
        "schema", "schemaVersion", "operation", "changed", "saved", "saveRequested",
        "command", "event", "status"
    )
    private val TELEMETRY_KEYS = setOf(
        "schema", "schemaVersion", "productKey", "uptimeMs", "mode",
        "sensorFailSafeActive", "automaticOutputCycleHealthy", "temperature",
        "lightProtection", "fans"
    )
    private val TOPOLOGY_KEYS = setOf("fanOutputCount", "temperatureSensorCount")
    private val CONFIG_KEYS = setOf("mode", "minTemperatureC", "maxTemperatureC")
    private val TEMPERATURE_KEYS = setOf(
        "sensorKey", "sensorIndex", "readingValid", "temperatureC", "sampledAtMs"
    )
    private val PROTECTION_STATUS_KEYS = setOf("enabled", "active", "thresholdC")
    private val PROTECTION_EVENT_KEYS = setOf("active", "thresholdC")
    private val FAN_STATUS_KEYS = setOf(
        "fanKey", "index", "name", "regime", "valueNow", "valueAuto",
        "percentNow", "percentAuto", "hardware"
    )
    private val FAN_HARDWARE_KEYS = setOf(
        "editable", "gpio", "ledcChannel", "pwmFrequencyHz", "pwmResolutionBits",
        "invert", "pwmOutputHealth", "health", "physicalFeedbackAvailable"
    )
    private val FAN_EVENT_KEYS = setOf(
        "fanKey", "regime", "percentNow", "percentAuto", "pwmOutputHealth",
        "health", "physicalFeedbackAvailable"
    )
    private val RUNTIME_KEYS = setOf(
        "event", "statusEvent", "sensorFailSafeActive", "automaticOutputCycleHealthy",
        "hardwareEditable", "fanMappingEditable", "sensorMappingEditable"
    )
}

private fun JSONObject.requireThermalKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from firmware; expected=$expected actual=$actual"
    }
}

private fun JSONObject.requireThermalObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

private fun JSONObject.requireThermalArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be an array.")

private fun JSONObject.requireThermalText(key: String): String =
    (get(key) as? String)?.also { value ->
        require(value.isNotEmpty() && value == value.trim())
        require(value.none(Char::isISOControl))
    } ?: error("$key must be a canonical string.")

private fun JSONObject.requireThermalBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be boolean.")

private fun JSONObject.requireThermalInt(key: String, minimum: Int, maximum: Int): Int {
    val value = get(key) as? Number ?: error("$key must be integer.")
    val long = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == long.toDouble())
    require(long in minimum.toLong()..maximum.toLong())
    return long.toInt()
}

private fun JSONObject.requireThermalLong(key: String): Long {
    val value = get(key) as? Number ?: error("$key must be integer.")
    val long = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == long.toDouble())
    require(long >= 0L)
    return long
}

private fun JSONObject.requireThermalDouble(
    key: String,
    minimum: Double,
    maximum: Double
): Double = (get(key) as? Number)?.toDouble()?.also { value ->
    require(value.isFinite() && value in minimum..maximum)
} ?: error("$key must be numeric.")

private fun JSONObject.requireThermalNullableDouble(
    key: String,
    minimum: Double,
    maximum: Double
): Double? = if (get(key) === JSONObject.NULL) {
    null
} else {
    requireThermalDouble(key, minimum, maximum)
}
