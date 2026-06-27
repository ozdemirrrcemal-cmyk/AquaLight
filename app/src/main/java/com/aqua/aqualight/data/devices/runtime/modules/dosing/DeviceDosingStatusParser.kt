package com.aqua.aqualight.data.devices.runtime.modules.dosing

import org.json.JSONArray
import org.json.JSONObject

object DeviceDosingStatusParser {

    fun parse(data: JSONObject): DeviceDosingStatus {
        val status = data.optJSONObject("status") ?: data

        return DeviceDosingStatus(
            supported = status.optBoolean("supported", false),
            channelCount = status.optInt("channelCount", 0),
            scheduleCount = status.optInt("scheduleCount", 0),
            lockLoop = status.optBoolean("lockLoop", false),
            schema = status.optString("schema", ""),
            rootName = status.optString("rootName", DeviceDosingRuntimeContract.MODULE),
            unit = status.optString("unit", "ml"),
            uptimeMs = status.optLong("uptimeMs", 0L),
            channels = parseChannels(status.optJSONArray("channels")),
            schedules = parseSchedules(status.optJSONArray("schedules")),
            runtime = parseRuntime(status.optJSONObject("runtime"))
        )
    }

    private fun parseRuntime(runtime: JSONObject?): DeviceDosingRuntimeCapabilities {
        return DeviceDosingRuntimeCapabilities(
            module = runtime?.optString("module", DeviceDosingRuntimeContract.MODULE)
                ?: DeviceDosingRuntimeContract.MODULE,
            readOnly = runtime?.optBoolean("readOnly", false) ?: false,
            supportsConfigApply = runtime?.optBoolean("supportsConfigApply", false) ?: false,
            supportsSchedules = runtime?.optBoolean("supportsSchedules", false) ?: false,
            supportsChannels = runtime?.optBoolean("supportsChannels", false) ?: false,
            supportsPrime = runtime?.optBoolean("supportsPrime", false) ?: false,
            supportsManualDose = runtime?.optBoolean("supportsManualDose", false) ?: false,
            supportsCalibrationWorkflow = runtime?.optBoolean("supportsCalibrationWorkflow", false) ?: false,
            supportsReservoirRefill = runtime?.optBoolean("supportsReservoirRefill", false) ?: false,
            event = runtime?.optString("event", "") ?: ""
        )
    }

    private fun parseChannels(channels: JSONArray?): List<DeviceDosingChannelStatus> {
        if (channels == null) return emptyList()

        return buildList {
            for (index in 0 until channels.length()) {
                val item = channels.optJSONObject(index) ?: continue
                add(parseChannel(item))
            }
        }
    }

    private fun parseChannel(item: JSONObject): DeviceDosingChannelStatus {
        val editable = item.optJSONObject("editable")

        return DeviceDosingChannelStatus(
            index = item.optInt("index", -1),
            key = item.optString("key", item.optString("channelKey", "")),
            name = item.optString("name", ""),
            displayName = item.optString("displayName", item.optString("name", "")),
            profileManaged = item.optBoolean("profileManaged", false),
            regime = DeviceDosingRegime.fromWire(item.optString("regime", DeviceDosingRegime.OFF.wireValue)),
            channelKind = item.optString("channelKind", ""),
            gpio = item.optInt("gpio", -1),
            ledcChannel = item.optInt("ledcChannel", -1),
            group = item.optInt("group", -1),
            valueNow = item.optDouble("valueNow", 0.0),
            valueAuto = item.optDouble("valueAuto", 0.0),
            valueManual = item.optDouble("valueManual", -1.0),
            manualTimeoutMs = item.optLong("manualTimeoutMs", 0L),
            invert = item.optBoolean("invert", false),
            pwmResolutionBits = item.optInt("pwmResolutionBits", 0),
            pwmFrequencyHz = item.optInt("pwmFrequencyHz", 0),
            editable = DeviceDosingChannelEditable(
                hardware = editable?.optBoolean("hardware", false) ?: false,
                displayName = editable?.optBoolean("displayName", false) ?: false,
                hardwareCalibration = editable?.optBoolean("hardwareCalibration", false) ?: false,
                dosingCalibration = editable?.optBoolean("dosingCalibration", false)
                    ?: editable?.optBoolean("calibration", false)
                    ?: false,
                reservoir = editable?.optBoolean("reservoir", false) ?: false
            ),
            dosing = parsePumpStatus(item)
        )
    }

    private fun parsePumpStatus(item: JSONObject): DeviceDosingPumpStatus {
        val dosing = item.optJSONObject("dosing")

        return DeviceDosingPumpStatus(
            doseMsPerMl = dosing?.optLong("doseMsPerMl", item.optLong("doseMsPerMl", -1L))
                ?: item.optLong("doseMsPerMl", -1L),
            doseUnit = dosing?.optString("doseUnit", item.optString("doseUnit", "ml"))
                ?: item.optString("doseUnit", "ml"),
            lastCalibratedAt = dosing?.optLong("lastCalibratedAt", item.optLong("lastCalibratedAt", 0L))
                ?: item.optLong("lastCalibratedAt", 0L),
            calibrated = dosing?.optBoolean("calibrated", item.optBoolean("calibrated", false))
                ?: item.optBoolean("calibrated", false),
            reservoirTrackingEnabled = dosing?.optBoolean(
                "reservoirTrackingEnabled",
                item.optBoolean("reservoirTrackingEnabled", false)
            ) ?: item.optBoolean("reservoirTrackingEnabled", false),
            reservoirCapacityMl = dosing?.optDouble(
                "reservoirCapacityMl",
                item.optDouble("reservoirCapacityMl", 0.0)
            ) ?: item.optDouble("reservoirCapacityMl", 0.0),
            reservoirRemainingMl = dosing?.optDouble(
                "reservoirRemainingMl",
                item.optDouble("reservoirRemainingMl", -1.0)
            ) ?: item.optDouble("reservoirRemainingMl", -1.0),
            reservoirRemainingPercent = dosing?.optDouble(
                "reservoirRemainingPercent",
                item.optDouble("reservoirRemainingPercent", -1.0)
            ) ?: item.optDouble("reservoirRemainingPercent", -1.0),
            reservoirStatus = dosing?.optString("reservoirStatus", item.optString("reservoirStatus", "unknown"))
                ?: item.optString("reservoirStatus", "unknown")
        )
    }

    private fun parseSchedules(schedules: JSONArray?): List<DeviceDosingScheduleStatus> {
        if (schedules == null) return emptyList()

        return buildList {
            for (index in 0 until schedules.length()) {
                val item = schedules.optJSONObject(index) ?: continue
                add(parseSchedule(item))
            }
        }
    }

    private fun parseSchedule(item: JSONObject): DeviceDosingScheduleStatus {
        return DeviceDosingScheduleStatus(
            index = item.optInt("index", -1),
            enabled = item.optBoolean("enabled", false),
            runtimeEnabled = item.optBoolean("runtimeEnabled", false),
            name = item.optString("name", ""),
            channelKey = item.optString("channelKey", ""),
            bound = item.optBoolean("bound", false),
            group = item.optInt("group", -1),
            weekdays = parseWeekdays(item.optJSONArray("weekdays")),
            startTimeMs = item.optLong("startTimeMs", 0L),
            startTime = item.optString("startTime", ""),
            intervalOnMs = item.optLong("intervalOnMs", 0L),
            intervalOn = item.optString("intervalOn", ""),
            intervalOffMs = item.optLong("intervalOffMs", 0L),
            intervalOff = item.optString("intervalOff", ""),
            repeatCount = item.optInt("repeatCount", 0),
            amountMl = item.optDouble("amountMl", 0.0),
            pulseCountRuntime = item.optInt("pulseCountRuntime", 0),
            pulseOffPending = item.optBoolean("pulseOffPending", false),
            pulseRemainingMs = item.optLong("pulseRemainingMs", 0L)
        )
    }

    private fun parseWeekdays(weekdays: JSONArray?): List<Boolean> {
        if (weekdays == null) return List(7) { false }

        return List(7) { index ->
            weekdays.optBoolean(index, false)
        }
    }
}
