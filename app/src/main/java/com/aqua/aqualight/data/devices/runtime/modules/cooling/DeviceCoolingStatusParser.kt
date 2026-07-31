package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.runtime.parsing.requireExactKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requiredArray
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.requiredFiniteDouble
import com.aqua.aqualight.data.devices.runtime.parsing.requiredInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeInts
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requiredObject
import com.aqua.aqualight.data.devices.runtime.parsing.requiredString
import com.aqua.aqualight.data.devices.runtime.parsing.requiredStringAllowEmpty
import org.json.JSONArray
import org.json.JSONObject

object DeviceCoolingStatusParser {

    fun parse(data: JSONObject): DeviceCoolingStatus {
        val status = data.optJSONObject("status") ?: data
        status.requireExactKeys(STATUS_KEYS, "cooling.status.get.data")
        val fans = parseFans(status.requiredArray("fans"))
        val rules = parseRules(status.requiredArray("rules"))
        val fanOutputCount = status.requiredNonNegativeInt("fanOutputCount")
        val ruleCount = status.requiredNonNegativeInt("ruleCount")
        require(fanOutputCount == fans.size) {
            "cooling fanOutputCount differs from fans array size."
        }
        require(ruleCount == rules.size) {
            "cooling ruleCount differs from rules array size."
        }

        val minTemperatureC = status.requiredFiniteDouble("minTemperatureC")
        val maxTemperatureC = status.requiredFiniteDouble("maxTemperatureC")
        require(maxTemperatureC > minTemperatureC) {
            "Cooling maxTemperatureC must be greater than minTemperatureC."
        }

        return DeviceCoolingStatus(
            supported = status.requiredBoolean("supported"),
            fanSupported = status.requiredBoolean("fanSupported"),
            temperatureSupported = status.requiredBoolean("temperatureSupported"),
            fanOutputCount = fanOutputCount,
            ruleCount = ruleCount,
            mode = requireNotNull(
                DeviceCoolingMode.fromWireExact(status.requiredString("mode"))
            ) { "Unknown cooling mode." },
            minTemperatureC = minTemperatureC,
            maxTemperatureC = maxTemperatureC,
            fixedSensorIndex = status.requiredInt("fixedSensorIndex").also { require(it >= -1) },
            uptimeMs = status.requiredNonNegativeLong("uptimeMs"),
            fans = fans,
            rules = rules,
            runtime = parseRuntime(status.requiredObject("runtime"))
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceCoolingRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "cooling.status.get.data.runtime")
        return DeviceCoolingRuntimeCapabilities(
            module = runtime.requiredString("module").also {
                require(it == DeviceCoolingRuntimeContract.MODULE)
            },
            readOnly = runtime.requiredBoolean("readOnly").also { require(!it) },
            supportsConfigApply = runtime.requiredBoolean("supportsConfigApply"),
            supportsModeSet = runtime.requiredBoolean("supportsModeSet"),
            supportsTemperatureRange = runtime.requiredBoolean("supportsTemperatureRange"),
            supportsFanDisplayName = runtime.requiredBoolean("supportsFanDisplayName"),
            hardwareEditable = runtime.requiredBoolean("hardwareEditable").also { require(!it) },
            fanMappingEditable = runtime.requiredBoolean("fanMappingEditable").also { require(!it) },
            sensorMappingEditable = runtime.requiredBoolean("sensorMappingEditable").also {
                require(!it)
            },
            event = runtime.requiredString("event").also {
                require(it == "cooling.status.changed")
            }
        )
    }

    private fun parseFans(fans: JSONArray): List<DeviceCoolingFanStatus> =
        List(fans.length()) { index -> parseFan(fans.requiredObject(index)) }

    private fun parseFan(item: JSONObject): DeviceCoolingFanStatus {
        item.requireExactKeys(FAN_KEYS, "cooling fan")
        val editable = item.requiredObject("editable")
        editable.requireExactKeys(EDITABLE_KEYS, "cooling fan editable")

        val valueMin = item.requiredFiniteDouble("valueMin")
        val valueMax = item.requiredFiniteDouble("valueMax")
        require(valueMax >= valueMin)
        val percentMin = item.requiredFiniteDouble("percentMin")
        val percentMax = item.requiredFiniteDouble("percentMax")
        require(percentMax >= percentMin)

        return DeviceCoolingFanStatus(
            index = item.requiredNonNegativeInt("index"),
            key = item.requiredString("key"),
            name = item.requiredString("name"),
            displayName = item.requiredString("displayName").also(::requireCommercialName),
            profileManaged = item.requiredBoolean("profileManaged"),
            mode = requireNotNull(
                DeviceCoolingMode.fromWireExact(item.requiredString("regime"))
            ) { "Unknown cooling fan regime." },
            channelKind = item.requiredString("channelKind"),
            gpio = item.requiredInt("gpio").also { require(it >= -1) },
            ledcChannel = item.requiredInt("ledcChannel").also { require(it >= -1) },
            group = item.requiredInt("group").also { require(it >= -1) },
            valueNow = item.requiredFiniteDouble("valueNow"),
            valueAuto = item.requiredFiniteDouble("valueAuto"),
            valueManual = item.requiredFiniteDouble("valueManual"),
            valueMin = valueMin,
            valueMax = valueMax,
            manualTimeoutMs = item.requiredNonNegativeLong("manualTimeoutMs"),
            percentNow = item.requiredFiniteDouble("percentNow"),
            percentAuto = item.requiredFiniteDouble("percentAuto"),
            percentManual = item.requiredFiniteDouble("percentManual"),
            percentMin = percentMin,
            percentMax = percentMax,
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

    private fun parseRules(rules: JSONArray): List<DeviceCoolingRuleStatus> =
        List(rules.length()) { index -> parseRule(rules.requiredObject(index)) }

    private fun parseRule(item: JSONObject): DeviceCoolingRuleStatus {
        item.requireExactKeys(RULE_KEYS, "cooling rule")
        val min = item.requiredFiniteDouble("minTemperatureC")
        val max = item.requiredFiniteDouble("maxTemperatureC")
        require(max > min)

        return DeviceCoolingRuleStatus(
            index = item.requiredNonNegativeInt("index"),
            name = item.requiredString("name").also(::requireCommercialName),
            enabled = item.requiredBoolean("enabled"),
            fanIndex = item.requiredInt("fanIndex").also { require(it >= -1) },
            channelKey = item.requiredStringAllowEmpty("channelKey"),
            bound = item.requiredBoolean("bound"),
            minTemperatureC = min,
            maxTemperatureC = max,
            group = item.requiredInt("group").also { require(it >= -1) },
            sensorBindings = item.requiredArray("sensorBindings").requiredNonNegativeInts()
        )
    }

    private fun requireCommercialName(value: String) {
        require(
            value.toByteArray(Charsets.UTF_8).size <=
                DeviceCoolingRuntimeContract.Limit.MAX_DISPLAY_NAME_BYTES
        ) {
            "Cooling display name exceeds firmware UTF-8 byte limit."
        }
    }

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
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "hardwareCalibration")
    private val RULE_KEYS = setOf(
        "index", "name", "enabled", "fanIndex", "channelKey", "bound",
        "minTemperatureC", "maxTemperatureC", "group", "sensorBindings"
    )
}
