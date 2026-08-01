package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceLightRuntimeFixtures {
    fun status(): JSONObject = JSONObject()
        .put("supported", true)
        .put("manualSupported", true)
        .put("programSupported", true)
        .put("presetsSupported", true)
        .put("simulationSupported", true)
        .put("channelCount", 1)
        .put("programCount", 1)
        .put("liveEditEnabled", true)
        .put("channelEdit", 0)
        .put("powerLimitW", 120.0)
        .put("lockLoop", false)
        .put("temperatureDownStepPercent", 10.0)
        .put("temperatureRecoveryMs", 30_000L)
        .put("lightCorrectionFactor", 1.0)
        .put("uptimeMs", 12_000L)
        .put("channels", JSONArray().put(channel()))
        .put("programs", JSONArray().put(program(mutation = false, index = 0)))
        .put(
            "runtime",
            JSONObject()
                .put("module", "light")
                .put("readOnly", false)
                .put("supportsManualSet", true)
                .put("supportsChannelRegimeSet", true)
                .put("supportsProgramApply", true)
                .put("supportsProgramDelete", true)
                .put("supportsLiveEdit", true)
                .put("event", "light.status.changed")
        )

    fun manual(): JSONObject = JSONObject()
        .put("operation", "manualState")
        .put("manualActive", true)
        .put("durationMs", 60_000L)
        .put("runtimeTransport", "websocket")
        .put("command", "light.manual.set")
        .put("event", "light.status.changed")
        .put(
            "channels",
            JSONArray().put(channel(regime = "Auto", manualValue = 0.25, mutation = true))
        )
        .put("affectedChannelCount", 1)
        .put("saved", false)

    fun regime(): JSONObject = JSONObject()
        .put("operation", "channelRegimeSet")
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("channelKey", "white")
        .put("regime", "On")
        .put("runtimeTransport", "websocket")
        .put("command", "light.channel.regime.set")
        .put("event", "light.status.changed")
        .put("channel", channel(regime = "On", mutation = true))

    fun programApply(): JSONObject = JSONObject()
        .put("operation", "programApply")
        .put("created", true)
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("programIndex", 1)
        .put("channelKey", "white")
        .put("channelListIndex", 0)
        .put("runtimeTransport", "websocket")
        .put("command", "light.program.apply")
        .put("event", "light.status.changed")
        .put("program", program(mutation = true, index = 1))

    fun programDelete(): JSONObject = JSONObject()
        .put("operation", "programDelete")
        .put("deleted", true)
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("programIndex", 1)
        .put("deletedListIndex", 1)
        .put("channelKey", "white")
        .put("deletedPointCount", 1)
        .put("programCount", 1)
        .put("runtimeTransport", "websocket")
        .put("command", "light.program.delete")
        .put("event", "light.status.changed")

    private fun channel(
        regime: String = "Auto",
        manualValue: Double = -1.0,
        mutation: Boolean = false
    ): JSONObject = JSONObject()
        .put("index", 0)
        .put("key", "white")
        .put("name", "White")
        .put("displayName", "White")
        .put("profileManaged", true)
        .put("regime", regime)
        .put("channelKind", "gpio")
        .put("gpio", 4)
        .put("ledcChannel", 0)
        .put("group", 0)
        .put("valueNow", if (manualValue >= 0.0) manualValue else 0.5)
        .put("valueAuto", 0.5)
        .put("valueManual", manualValue)
        .put("manualTimeoutMs", if (manualValue >= 0.0) 60_000L else 0L)
        .put("percentNow", if (manualValue >= 0.0) manualValue * 100.0 else 50.0)
        .put("percentAuto", 50.0)
        .put("percentManual", manualValue * 100.0)
        .put("invert", false)
        .put("pwmResolutionBits", 12)
        .put("pwmFrequencyHz", 5_000)
        .put("color", 16_777_215)
        .put("lumen", 0.0)
        .put("lux", 0.0)
        .put("watt", 24.0)
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", false)
                .put("color", false)
                .put("hardwareCalibration", false)
        ).also { result ->
            if (mutation) result.put("listIndex", 0)
        }

    private fun program(mutation: Boolean, index: Int): JSONObject = JSONObject()
        .put("index", index)
        .put("channelKey", "white")
        .put("bound", true)
        .put("pointCount", 1)
        .put(
            "points",
            JSONArray().put(
                JSONObject()
                    .put("timeMs", 0L)
                    .put("time", "00:00:00")
                    .put("value", 0.3)
                    .put("percent", 30.0)
                    .also { point -> if (mutation) point.put("index", 0) }
            )
        ).also { result ->
            if (mutation) result.put("listIndex", index)
        }
}
