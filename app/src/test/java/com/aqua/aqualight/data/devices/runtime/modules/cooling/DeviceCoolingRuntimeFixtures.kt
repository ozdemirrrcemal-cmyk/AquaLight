package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceCoolingRuntimeFixtures {
    fun status(
        temperatureC: Double? = 27.4,
        fanDisplayNameEditable: Boolean = true
    ): JSONObject = JSONObject()
        .put("supported", true)
        .put("fanSupported", true)
        .put("temperatureSupported", true)
        .put("fanOutputCount", 1)
        .put("ruleCount", 1)
        .put("mode", "Auto")
        .put("minTemperatureC", 28.0)
        .put("maxTemperatureC", 35.0)
        .put("fixedSensorIndex", 0)
        .put("uptimeMs", 12_000L)
        .put("fans", JSONArray().put(fan(fanDisplayNameEditable = fanDisplayNameEditable)))
        .put("rules", JSONArray().put(statusRule()))
        .put(
            "runtime",
            JSONObject()
                .put("module", "cooling")
                .put("readOnly", false)
                .put("supportsConfigApply", true)
                .put("supportsModeSet", true)
                .put("supportsTemperatureRange", true)
                .put("supportsFanDisplayName", fanDisplayNameEditable)
                .put("hardwareEditable", false)
                .put("fanMappingEditable", false)
                .put("sensorMappingEditable", false)
                .put("event", "cooling.status.changed")
        )
        .put("temperature", temperature(temperatureC))

    fun temperature(temperatureC: Double? = 27.4): JSONObject = JSONObject()
        .put("sensorIndex", if (temperatureC == null) 0 else 0)
        .put("readingValid", temperatureC != null)
        .put("temperatureC", temperatureC ?: JSONObject.NULL)
        .put("sampledAtMs", 12_000L)

    fun configApply(
        mode: String = "On",
        displayName: String = "Sol Fan",
        save: Boolean = true,
        fanDisplayNameEditable: Boolean = true
    ): JSONObject = JSONObject()
        .put("operation", "configApply")
        .put("changed", true)
        .put("saved", save)
        .put("saveRequested", save)
        .put("runtimeTransport", "websocket")
        .put("command", "cooling.config.apply")
        .put("event", "cooling.status.changed")
        .put("appliedGlobalConfig", true)
        .put("appliedFanDisplayNames", fanDisplayNameEditable)
        .put(
            "config",
            JSONObject()
                .put("supported", true)
                .put("fanSupported", true)
                .put("temperatureSupported", true)
                .put("fanOutputCount", 1)
                .put("ruleCount", 1)
                .put("mode", mode)
                .put("minTemperatureC", 29.0)
                .put("maxTemperatureC", 36.0)
                .put("fixedSensorIndex", 0)
                .put("hardwareEditable", false)
                .put("fanMappingEditable", false)
                .put("sensorMappingEditable", false)
                .put(
                    "fans",
                    JSONArray().put(
                        fan(
                            mode = mode,
                            displayName = displayName,
                            fanDisplayNameEditable = fanDisplayNameEditable,
                            listIndex = 0
                        )
                    )
                )
                .put("rules", JSONArray().put(configRule(mode)))
        )

    private fun fan(
        mode: String = "Auto",
        displayName: String = "Fan 1",
        fanDisplayNameEditable: Boolean,
        listIndex: Int? = null
    ): JSONObject = JSONObject()
        .put("index", 0)
        .put("key", "fan1")
        .put("name", "Fan 1")
        .put("displayName", displayName)
        .put("profileManaged", true)
        .put("regime", mode)
        .put("channelKind", "gpio")
        .put("gpio", 4)
        .put("ledcChannel", 0)
        .put("group", 0)
        .put("valueNow", 0.5)
        .put("valueAuto", 0.5)
        .put("valueManual", -1.0)
        .put("valueMin", 0.0)
        .put("valueMax", 1.0)
        .put("manualTimeoutMs", 0L)
        .put("percentNow", 50.0)
        .put("percentAuto", 50.0)
        .put("percentManual", -100.0)
        .put("percentMin", 0.0)
        .put("percentMax", 100.0)
        .put("invert", false)
        .put("pwmResolutionBits", 12)
        .put("pwmFrequencyHz", 5_000)
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", fanDisplayNameEditable)
                .put("hardwareCalibration", false)
        ).also { result -> listIndex?.let { index -> result.put("listIndex", index) } }

    private fun statusRule(): JSONObject = JSONObject()
        .put("index", 0)
        .put("name", "Cooling")
        .put("enabled", true)
        .put("fanIndex", 0)
        .put("channelKey", "fan1")
        .put("bound", true)
        .put("minTemperatureC", 28.0)
        .put("maxTemperatureC", 35.0)
        .put("group", 0)
        .put("sensorBindings", JSONArray().put(0))

    private fun configRule(mode: String): JSONObject = JSONObject()
        .put("listIndex", 0)
        .put("index", 0)
        .put("name", "Cooling")
        .put("enabled", mode == "Auto")
        .put("fanIndex", 0)
        .put("channelKey", "fan1")
        .put("bound", true)
        .put("minTemperatureC", 29.0)
        .put("maxTemperatureC", 36.0)
        .put("group", 0)
}
