package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

object DeviceLightStatusParser {

    fun parse(data: JSONObject): DeviceLightStatus {
        val status = data.optJSONObject("status") ?: data

        return DeviceLightStatus(
            supported = status.optBoolean("supported", false),
            manualSupported = status.optBoolean("manualSupported", false),
            programSupported = status.optBoolean("programSupported", false),
            presetsSupported = status.optBoolean("presetsSupported", false),
            simulationSupported = status.optBoolean("simulationSupported", false),
            channelCount = status.optInt("channelCount", 0),
            programCount = status.optInt("programCount", 0),
            liveEditEnabled = status.optBoolean("liveEditEnabled", false),
            channelEdit = status.optInt("channelEdit", 0),
            powerLimitW = status.optDouble("powerLimitW", 0.0),
            lockLoop = status.optBoolean("lockLoop", false),
            temperatureDownStepPercent = status.optDouble("temperatureDownStepPercent", 0.0),
            temperatureRecoveryMs = status.optLong("temperatureRecoveryMs", 0L),
            lightCorrectionFactor = status.optDouble("lightCorrectionFactor", 0.0),
            uptimeMs = status.optLong("uptimeMs", 0L),
            channels = parseChannels(status.optJSONArray("channels")),
            programs = parsePrograms(status.optJSONArray("programs")),
            runtime = parseRuntime(status.optJSONObject("runtime"))
        )
    }

    private fun parseRuntime(runtime: JSONObject?): DeviceLightRuntimeCapabilities {
        return DeviceLightRuntimeCapabilities(
            module = runtime?.optString("module", DeviceLightRuntimeContract.MODULE)
                ?: DeviceLightRuntimeContract.MODULE,
            readOnly = runtime?.optBoolean("readOnly", false) ?: false,
            supportsManualSet = runtime?.optBoolean("supportsManualSet", false) ?: false,
            supportsChannelRegimeSet = runtime?.optBoolean("supportsChannelRegimeSet", false) ?: false,
            supportsProgramApply = runtime?.optBoolean("supportsProgramApply", false) ?: false,
            supportsProgramDelete = runtime?.optBoolean("supportsProgramDelete", false) ?: false,
            supportsLiveEdit = runtime?.optBoolean("supportsLiveEdit", false) ?: false,
            event = runtime?.optString("event", "") ?: ""
        )
    }

    private fun parseChannels(channels: JSONArray?): List<DeviceLightChannelStatus> {
        if (channels == null) return emptyList()

        return buildList {
            for (index in 0 until channels.length()) {
                val item = channels.optJSONObject(index) ?: continue
                add(parseChannel(item))
            }
        }
    }

    private fun parseChannel(item: JSONObject): DeviceLightChannelStatus {
        val editable = item.optJSONObject("editable")

        return DeviceLightChannelStatus(
            index = item.optInt("index", -1),
            key = item.optString("key", ""),
            name = item.optString("name", ""),
            displayName = item.optString("displayName", item.optString("name", "")),
            profileManaged = item.optBoolean("profileManaged", false),
            regime = DeviceLightRegime.fromWire(item.optString("regime", DeviceLightRegime.OFF.wireValue)),
            channelKind = item.optString("channelKind", ""),
            gpio = item.optInt("gpio", -1),
            ledcChannel = item.optInt("ledcChannel", -1),
            group = item.optInt("group", -1),
            valueNow = item.optDouble("valueNow", 0.0),
            valueAuto = item.optDouble("valueAuto", 0.0),
            valueManual = item.optDouble("valueManual", -1.0),
            manualTimeoutMs = item.optLong("manualTimeoutMs", 0L),
            percentNow = item.optDouble("percentNow", item.optDouble("valueNow", 0.0) * 100.0),
            percentAuto = item.optDouble("percentAuto", item.optDouble("valueAuto", 0.0) * 100.0),
            percentManual = item.optDouble("percentManual", item.optDouble("valueManual", -1.0) * 100.0),
            invert = item.optBoolean("invert", false),
            pwmResolutionBits = item.optInt("pwmResolutionBits", 0),
            pwmFrequencyHz = item.optInt("pwmFrequencyHz", 0),
            color = item.optInt("color", 0),
            lumen = item.optDouble("lumen", 0.0),
            lux = item.optDouble("lux", 0.0),
            watt = item.optDouble("watt", 0.0),
            editable = DeviceLightChannelEditable(
                hardware = editable?.optBoolean("hardware", false) ?: false,
                displayName = editable?.optBoolean("displayName", false) ?: false,
                color = editable?.optBoolean("color", false) ?: false,
                hardwareCalibration = editable?.optBoolean("hardwareCalibration", false) ?: false
            )
        )
    }

    private fun parsePrograms(programs: JSONArray?): List<DeviceLightProgramStatus> {
        if (programs == null) return emptyList()

        return buildList {
            for (listIndex in 0 until programs.length()) {
                val item = programs.optJSONObject(listIndex) ?: continue
                add(parseProgram(item, listIndex))
            }
        }
    }

    private fun parseProgram(
        item: JSONObject,
        fallbackListIndex: Int
    ): DeviceLightProgramStatus {
        return DeviceLightProgramStatus(
            listIndex = item.optInt("listIndex", fallbackListIndex),
            index = item.optInt("index", fallbackListIndex),
            channelKey = item.optString("channelKey", ""),
            bound = item.optBoolean("bound", false),
            pointCount = item.optInt("pointCount", 0),
            points = parseProgramPoints(item.optJSONArray("points"))
        )
    }

    private fun parseProgramPoints(points: JSONArray?): List<DeviceLightProgramPointStatus> {
        if (points == null) return emptyList()

        return buildList {
            for (index in 0 until points.length()) {
                val item = points.optJSONObject(index) ?: continue
                add(
                    DeviceLightProgramPointStatus(
                        index = item.optInt("index", index),
                        timeMs = item.optLong("timeMs", 0L),
                        time = item.optString("time", ""),
                        value = item.optDouble("value", 0.0),
                        percent = item.optDouble("percent", item.optDouble("value", 0.0) * 100.0)
                    )
                )
            }
        }
    }
}
