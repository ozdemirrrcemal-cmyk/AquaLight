package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

object DeviceCoolingStatusParser {
    private val STATUS_KEYS = setOf(
        "supported",
        "fanSupported",
        "temperatureSupported",
        "fanOutputCount",
        "ruleCount",
        "mode",
        "minTemperatureC",
        "maxTemperatureC",
        "fixedSensorIndex",
        "uptimeMs",
        "fans",
        "rules",
        "runtime",
        "temperature"
    )

    fun parse(data: JSONObject): DeviceCoolingStatus {
        data.requireCoolingKeys(STATUS_KEYS, "cooling status")
        val fans = parseFans(data.requireCoolingArray("fans"))
        val rules = parseRules(data.requireCoolingArray("rules"))
        val status = DeviceCoolingStatus(
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
            uptimeMs = data.requireCoolingLong(
                "uptimeMs",
                minimum = COOLING_NON_NEGATIVE_LONG,
                maximum = COOLING_DEVICE_UPTIME_MAX_MS
            ),
            temperature = DeviceCoolingTemperatureParser.parse(
                data.requireCoolingObject("temperature")
            ),
            fans = fans,
            rules = rules,
            runtime = DeviceCoolingRuntimeCapabilitiesParser.parse(
                data.requireCoolingObject("runtime")
            )
        )
        validate(status)
        return status
    }

    private fun parseFans(data: JSONArray): List<DeviceCoolingFanStatus> =
        List(data.length()) { index ->
            DeviceCoolingFanParser.parseStatus(data.requireCoolingObject(index))
        }

    private fun parseRules(data: JSONArray): List<DeviceCoolingRuleStatus> =
        List(data.length()) { index ->
            DeviceCoolingRuleParser.parseStatus(data.requireCoolingObject(index))
        }

    private fun validate(status: DeviceCoolingStatus) {
        require(status.minTemperatureC < status.maxTemperatureC)
        require(status.fanOutputCount == status.fans.size)
        require(status.ruleCount == status.rules.size)
        require(status.rules.all { rule -> rule.minTemperatureC == status.minTemperatureC })
        require(status.rules.all { rule -> rule.maxTemperatureC == status.maxTemperatureC })
        require(status.rules.map(DeviceCoolingRuleStatus::fanIndex).distinct().size == status.rules.size)
        require(status.fans.map(DeviceCoolingFanStatus::key).distinct().size == status.fans.size)
        require(status.temperature.sensorIndex == status.fixedSensorIndex)
        if (status.fixedSensorIndex >= COOLING_MIN_INDEX) {
            require(status.rules.all { rule -> rule.sensorBindings == listOf(status.fixedSensorIndex) })
        } else {
            require(status.rules.all { rule -> rule.sensorBindings.isEmpty() })
            require(!status.temperature.readingValid)
        }
        require(status.runtime.supportsFanDisplayName == status.fans.any { fan -> fan.editable.displayName })
    }
}
