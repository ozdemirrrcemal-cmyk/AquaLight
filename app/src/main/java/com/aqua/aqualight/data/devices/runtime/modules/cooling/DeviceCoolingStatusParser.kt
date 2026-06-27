package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

object DeviceCoolingStatusParser {

    fun parse(data: JSONObject): DeviceCoolingStatus {
        val status = data.optJSONObject("status") ?: data

        return DeviceCoolingStatus(
            supported = status.optBoolean("supported", false),
            fanSupported = status.optBoolean("fanSupported", false),
            temperatureSupported = status.optBoolean("temperatureSupported", false),
            fanOutputCount = status.optInt("fanOutputCount", 0),
            ruleCount = status.optInt("ruleCount", 0),
            mode = DeviceCoolingMode.fromWire(status.optString("mode", DeviceCoolingMode.OFF.wireValue)),
            minTemperatureC = status.optDouble("minTemperatureC", 0.0),
            maxTemperatureC = status.optDouble("maxTemperatureC", 0.0),
            fixedSensorIndex = status.optInt("fixedSensorIndex", -1),
            uptimeMs = status.optLong("uptimeMs", 0L),
            fans = parseFans(status.optJSONArray("fans")),
            rules = parseRules(status.optJSONArray("rules")),
            runtime = parseRuntime(status.optJSONObject("runtime"))
        )
    }

    private fun parseRuntime(runtime: JSONObject?): DeviceCoolingRuntimeCapabilities {
        return DeviceCoolingRuntimeCapabilities(
            module = runtime?.optString("module", DeviceCoolingRuntimeContract.MODULE)
                ?: DeviceCoolingRuntimeContract.MODULE,
            readOnly = runtime?.optBoolean("readOnly", false) ?: false,
            supportsConfigApply = runtime?.optBoolean("supportsConfigApply", false) ?: false,
            supportsModeSet = runtime?.optBoolean("supportsModeSet", false) ?: false,
            supportsTemperatureRange = runtime?.optBoolean("supportsTemperatureRange", false) ?: false,
            hardwareEditable = runtime?.optBoolean("hardwareEditable", false) ?: false,
            fanMappingEditable = runtime?.optBoolean("fanMappingEditable", false) ?: false,
            sensorMappingEditable = runtime?.optBoolean("sensorMappingEditable", false) ?: false,
            event = runtime?.optString("event", "") ?: ""
        )
    }

    private fun parseFans(fans: JSONArray?): List<DeviceCoolingFanStatus> {
        if (fans == null) return emptyList()

        return buildList {
            for (index in 0 until fans.length()) {
                val item = fans.optJSONObject(index) ?: continue
                add(parseFan(item))
            }
        }
    }

    private fun parseFan(item: JSONObject): DeviceCoolingFanStatus {
        val editable = item.optJSONObject("editable")

        return DeviceCoolingFanStatus(
            index = item.optInt("index", -1),
            key = item.optString("key", ""),
            name = item.optString("name", ""),
            displayName = item.optString("displayName", item.optString("name", "")),
            profileManaged = item.optBoolean("profileManaged", false),
            mode = DeviceCoolingMode.fromWire(item.optString("regime", DeviceCoolingMode.OFF.wireValue)),
            channelKind = item.optString("channelKind", ""),
            gpio = item.optInt("gpio", -1),
            ledcChannel = item.optInt("ledcChannel", -1),
            group = item.optInt("group", -1),
            valueNow = item.optDouble("valueNow", 0.0),
            valueAuto = item.optDouble("valueAuto", 0.0),
            valueManual = item.optDouble("valueManual", -1.0),
            valueMin = item.optDouble("valueMin", 0.0),
            valueMax = item.optDouble("valueMax", 1.0),
            manualTimeoutMs = item.optLong("manualTimeoutMs", 0L),
            percentNow = item.optDouble("percentNow", 0.0),
            percentAuto = item.optDouble("percentAuto", 0.0),
            percentManual = item.optDouble("percentManual", -100.0),
            percentMin = item.optDouble("percentMin", 0.0),
            percentMax = item.optDouble("percentMax", 100.0),
            invert = item.optBoolean("invert", false),
            pwmResolutionBits = item.optInt("pwmResolutionBits", 0),
            pwmFrequencyHz = item.optInt("pwmFrequencyHz", 0),
            editable = DeviceCoolingFanEditable(
                hardware = editable?.optBoolean("hardware", false) ?: false,
                displayName = editable?.optBoolean("displayName", false) ?: false,
                hardwareCalibration = editable?.optBoolean("hardwareCalibration", false) ?: false
            )
        )
    }

    private fun parseRules(rules: JSONArray?): List<DeviceCoolingRuleStatus> {
        if (rules == null) return emptyList()

        return buildList {
            for (index in 0 until rules.length()) {
                val item = rules.optJSONObject(index) ?: continue
                add(parseRule(item))
            }
        }
    }

    private fun parseRule(item: JSONObject): DeviceCoolingRuleStatus {
        return DeviceCoolingRuleStatus(
            index = item.optInt("index", -1),
            name = item.optString("name", ""),
            enabled = item.optBoolean("enabled", false),
            fanIndex = item.optInt("fanIndex", -1),
            channelKey = item.optString("channelKey", ""),
            bound = item.optBoolean("bound", false),
            minTemperatureC = item.optDouble("minTemperatureC", 0.0),
            maxTemperatureC = item.optDouble("maxTemperatureC", 0.0),
            group = item.optInt("group", -1),
            sensorBindings = parseIntArray(item.optJSONArray("sensorBindings"))
        )
    }

    private fun parseIntArray(values: JSONArray?): List<Int> {
        if (values == null) return emptyList()

        val result = mutableListOf<Int>()
        for (index in 0 until values.length()) {
            val value = values.optInt(index, -1)
            if (value >= 0) result.add(value)
        }
        return result
    }
}
