package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceCoolingMutationParser {
    private val RESULT_KEYS = setOf(
        "operation",
        "changed",
        "saved",
        "saveRequested",
        "runtimeTransport",
        "command",
        "event",
        "appliedGlobalConfig",
        "appliedFanDisplayNames",
        "config"
    )
    private val CONFIG_KEYS = setOf(
        "supported",
        "fanSupported",
        "temperatureSupported",
        "fanOutputCount",
        "ruleCount",
        "mode",
        "minTemperatureC",
        "maxTemperatureC",
        "fixedSensorIndex",
        "hardwareEditable",
        "fanMappingEditable",
        "sensorMappingEditable",
        "fans",
        "rules"
    )

    fun parseConfigApply(data: JSONObject): DeviceCoolingConfigApplyResult {
        data.requireCoolingKeys(RESULT_KEYS, "cooling config apply result")
        return DeviceCoolingConfigApplyResult(
            operation = data.requireCoolingText("operation"),
            changed = data.requireCoolingBoolean("changed"),
            saved = data.requireCoolingBoolean("saved"),
            saveRequested = data.requireCoolingBoolean("saveRequested"),
            runtimeTransport = data.requireCoolingText("runtimeTransport"),
            command = data.requireCoolingText("command"),
            event = data.requireCoolingText("event"),
            appliedGlobalConfig = data.requireCoolingBoolean("appliedGlobalConfig"),
            appliedFanDisplayNames = data.requireCoolingBoolean("appliedFanDisplayNames"),
            config = parseConfig(data.requireCoolingObject("config"))
        ).also(::validateResultLiterals)
    }

    private fun parseConfig(data: JSONObject): DeviceCoolingConfigSnapshot {
        data.requireCoolingKeys(CONFIG_KEYS, "cooling config snapshot")
        val fans = parseFans(data.requireCoolingArray("fans"))
        val rules = parseRules(data.requireCoolingArray("rules"))
        return DeviceCoolingConfigSnapshot(
            supported = data.requireCoolingBoolean("supported"),
            fanSupported = data.requireCoolingBoolean("fanSupported"),
            temperatureSupported = data.requireCoolingBoolean("temperatureSupported"),
            fanOutputCount = data.requireCoolingInt(
                "fanOutputCount",
                minimum = COOLING_MIN_COUNT,
                maximum = DeviceCoolingRuntimeContract.Limit.MAX_FAN_COUNT
            ),
            ruleCount = data.requireCoolingInt(
                "ruleCount",
                minimum = COOLING_MIN_COUNT,
                maximum = DeviceCoolingRuntimeContract.Limit.MAX_FAN_COUNT
            ),
            mode = DeviceCoolingModeParser.parse(data.requireCoolingText("mode")),
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
            fixedSensorIndex = data.requireCoolingInt(
                "fixedSensorIndex",
                COOLING_UNAVAILABLE_INDEX,
                DeviceCoolingRuntimeContract.Limit.MAX_SENSOR_INDEX
            ),
            hardwareEditable = data.requireCoolingBoolean("hardwareEditable"),
            fanMappingEditable = data.requireCoolingBoolean("fanMappingEditable"),
            sensorMappingEditable = data.requireCoolingBoolean("sensorMappingEditable"),
            fans = fans,
            rules = rules
        ).also(::validateConfig)
    }

    private fun parseFans(data: JSONArray): List<DeviceCoolingFanConfigSnapshot> =
        List(data.length()) { index ->
            DeviceCoolingFanParser.parseConfig(data.requireCoolingObject(index))
        }

    private fun parseRules(data: JSONArray): List<DeviceCoolingRuleConfigSnapshot> =
        List(data.length()) { index ->
            DeviceCoolingRuleParser.parseConfig(data.requireCoolingObject(index))
        }

    private fun validateResultLiterals(result: DeviceCoolingConfigApplyResult) {
        require(result.operation == DeviceCoolingRuntimeContract.Literal.CONFIG_APPLY_OPERATION)
        require(result.runtimeTransport == DeviceCoolingRuntimeContract.Literal.RUNTIME_TRANSPORT)
        require(
            result.command ==
                "${DeviceCoolingRuntimeContract.MODULE}.${DeviceCoolingRuntimeContract.Action.CONFIG_APPLY}"
        )
        require(result.event == DeviceCoolingRuntimeContract.STATUS_EVENT)
    }

    private fun validateConfig(config: DeviceCoolingConfigSnapshot) {
        require(config.minTemperatureC < config.maxTemperatureC)
        require(config.fanOutputCount == config.fans.size)
        require(config.ruleCount == config.rules.size)
        require(!config.hardwareEditable)
        require(!config.fanMappingEditable)
        require(!config.sensorMappingEditable)
        require(config.fans.map(DeviceCoolingFanConfigSnapshot::listIndex) == config.fans.indices.toList())
        require(config.rules.map(DeviceCoolingRuleConfigSnapshot::listIndex) == config.rules.indices.toList())
        require(config.fans.map { snapshot -> snapshot.fan.key }.distinct().size == config.fans.size)
        require(config.rules.all { rule -> rule.minTemperatureC == config.minTemperatureC })
        require(config.rules.all { rule -> rule.maxTemperatureC == config.maxTemperatureC })
    }
}
