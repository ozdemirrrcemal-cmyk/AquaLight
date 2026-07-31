package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.runtime.parsing.requireExactKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requiredArray
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBooleans
import com.aqua.aqualight.data.devices.runtime.parsing.requiredFiniteDouble
import com.aqua.aqualight.data.devices.runtime.parsing.requiredInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requiredObject
import com.aqua.aqualight.data.devices.runtime.parsing.requiredString
import org.json.JSONArray
import org.json.JSONObject

object DeviceDosingStatusParser {

    fun parse(data: JSONObject): DeviceDosingStatus {
        val status = data.optJSONObject("status") ?: data
        status.requireExactKeys(STATUS_KEYS, "dosing.status.get.data")
        val channels = parseChannels(status.requiredArray("channels"))
        val schedules = parseSchedules(status.requiredArray("schedules"))
        val channelCount = status.requiredNonNegativeInt("channelCount")
        val scheduleCount = status.requiredNonNegativeInt("scheduleCount")
        require(channelCount == channels.size) {
            "dosing channelCount differs from channels array size."
        }
        require(scheduleCount == schedules.size) {
            "dosing scheduleCount differs from schedules array size."
        }

        return DeviceDosingStatus(
            supported = status.requiredBoolean("supported"),
            channelCount = channelCount,
            scheduleCount = scheduleCount,
            lockLoop = status.requiredBoolean("lockLoop"),
            schema = status.requiredString("schema"),
            rootName = status.requiredString("rootName"),
            unit = status.requiredString("unit").also { require(it == DOSING_UNIT) },
            uptimeMs = status.requiredNonNegativeLong("uptimeMs"),
            channels = channels,
            schedules = schedules,
            runtime = parseRuntime(status.requiredObject("runtime"))
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceDosingRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "dosing.status.get.data.runtime")
        return DeviceDosingRuntimeCapabilities(
            module = runtime.requiredString("module").also {
                require(it == DeviceDosingRuntimeContract.MODULE)
            },
            readOnly = runtime.requiredBoolean("readOnly").also { require(!it) },
            supportsConfigApply = runtime.requiredBoolean("supportsConfigApply"),
            supportsSchedules = runtime.requiredBoolean("supportsSchedules"),
            supportsChannels = runtime.requiredBoolean("supportsChannels"),
            supportsPrime = runtime.requiredBoolean("supportsPrime"),
            supportsManualDose = runtime.requiredBoolean("supportsManualDose"),
            supportsCalibrationWorkflow = runtime.requiredBoolean(
                "supportsCalibrationWorkflow"
            ),
            supportsReservoirRefill = runtime.requiredBoolean("supportsReservoirRefill"),
            event = runtime.requiredString("event").also {
                require(it == "dosing.status.changed")
            }
        )
    }

    private fun parseChannels(channels: JSONArray): List<DeviceDosingChannelStatus> =
        List(channels.length()) { index ->
            parseChannel(channels.requiredObject(index))
        }

    private fun parseChannel(item: JSONObject): DeviceDosingChannelStatus {
        item.requireExactKeys(CHANNEL_KEYS, "dosing channel")
        val editable = item.requiredObject("editable")
        val dosing = item.requiredObject("dosing")
        editable.requireExactKeys(EDITABLE_KEYS, "dosing channel editable")
        dosing.requireExactKeys(DOSING_KEYS, "dosing channel calibration/reservoir")

        return DeviceDosingChannelStatus(
            index = item.requiredNonNegativeInt("index"),
            key = item.requiredString("key"),
            name = item.requiredString("name"),
            displayName = item.requiredString("displayName").also(::requireCommercialName),
            profileManaged = item.requiredBoolean("profileManaged"),
            regime = requireNotNull(
                DeviceDosingRegime.fromWireExact(item.requiredString("regime"))
            ) { "Unknown dosing regime." },
            channelKind = item.requiredString("channelKind"),
            gpio = item.requiredInt("gpio").also { require(it >= -1) },
            ledcChannel = item.requiredInt("ledcChannel").also { require(it >= -1) },
            group = item.requiredInt("group").also { require(it >= -1) },
            valueNow = item.requiredFiniteDouble("valueNow"),
            valueAuto = item.requiredFiniteDouble("valueAuto"),
            valueManual = item.requiredFiniteDouble("valueManual"),
            manualTimeoutMs = item.requiredNonNegativeLong("manualTimeoutMs"),
            invert = item.requiredBoolean("invert"),
            pwmResolutionBits = item.requiredNonNegativeInt("pwmResolutionBits"),
            pwmFrequencyHz = item.requiredNonNegativeInt("pwmFrequencyHz"),
            editable = DeviceDosingChannelEditable(
                hardware = editable.requiredBoolean("hardware"),
                displayName = editable.requiredBoolean("displayName"),
                hardwareCalibration = editable.requiredBoolean("hardwareCalibration"),
                dosingCalibration = editable.requiredBoolean("dosingCalibration"),
                reservoir = editable.requiredBoolean("reservoir")
            ),
            dosing = parsePumpStatus(dosing)
        )
    }

    private fun parsePumpStatus(dosing: JSONObject): DeviceDosingPumpStatus {
        val doseMsPerMl = dosing.requiredNonNegativeLong("doseMsPerMl")
        val calibrated = dosing.requiredBoolean("calibrated")
        require(calibrated == (doseMsPerMl > 0L)) {
            "Dosing calibrated flag differs from doseMsPerMl."
        }

        val tracking = dosing.requiredBoolean("reservoirTrackingEnabled")
        val capacity = dosing.requiredFiniteDouble("reservoirCapacityMl")
        val remaining = dosing.requiredFiniteDouble("reservoirRemainingMl")
        val percent = dosing.requiredFiniteDouble("reservoirRemainingPercent")
        require(capacity >= 0.0)
        if (tracking) {
            require(capacity > 0.0)
            require(remaining in 0.0..capacity)
            require(percent in 0.0..RESERVOIR_PERCENT_MAX)
        } else {
            require(remaining == UNAVAILABLE_MEASUREMENT)
            require(percent == UNAVAILABLE_MEASUREMENT)
        }

        return DeviceDosingPumpStatus(
            unit = dosing.requiredString("unit").also { require(it == DOSING_UNIT) },
            doseMsPerMl = doseMsPerMl,
            lastCalibratedAt = dosing.requiredNonNegativeLong("lastCalibratedAt"),
            calibrated = calibrated,
            reservoirTrackingEnabled = tracking,
            reservoirCapacityMl = capacity,
            reservoirRemainingMl = remaining,
            reservoirRemainingPercent = percent
        )
    }

    private fun parseSchedules(schedules: JSONArray): List<DeviceDosingScheduleStatus> =
        List(schedules.length()) { index ->
            parseSchedule(schedules.requiredObject(index))
        }

    private fun parseSchedule(item: JSONObject): DeviceDosingScheduleStatus {
        item.requireExactKeys(SCHEDULE_KEYS, "dosing schedule")
        return DeviceDosingScheduleStatus(
            index = item.requiredNonNegativeInt("index"),
            enabled = item.requiredBoolean("enabled"),
            runtimeEnabled = item.requiredBoolean("runtimeEnabled"),
            name = item.requiredString("name").also(::requireCommercialName),
            channelKey = item.requiredString("channelKey"),
            bound = item.requiredBoolean("bound"),
            group = item.requiredInt("group").also { require(it >= -1) },
            weekdays = item.requiredArray("weekdays").requiredBooleans(
                expectedSize = WEEKDAY_COUNT,
                label = "dosing schedule weekdays"
            ),
            startTimeMs = item.requiredNonNegativeLong("startTimeMs").also {
                require(it < MILLIS_PER_DAY)
            },
            startTime = item.requiredString("startTime"),
            intervalOnMs = item.requiredNonNegativeLong("intervalOnMs"),
            intervalOn = item.requiredString("intervalOn"),
            intervalOffMs = item.requiredNonNegativeLong("intervalOffMs"),
            intervalOff = item.requiredString("intervalOff"),
            repeatCount = item.requiredNonNegativeInt("repeatCount"),
            amountMl = item.requiredFiniteDouble("amountMl").also { require(it >= 0.0) },
            pulseCountRuntime = item.requiredNonNegativeInt("pulseCountRuntime"),
            pulseOffPending = item.requiredBoolean("pulseOffPending"),
            pulseRemainingMs = item.requiredNonNegativeLong("pulseRemainingMs")
        )
    }

    private fun requireCommercialName(value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_DISPLAY_NAME_BYTES) {
            "Commercial display name exceeds firmware UTF-8 byte limit."
        }
    }

    private val STATUS_KEYS = setOf(
        "supported", "channelCount", "scheduleCount", "lockLoop", "schema", "rootName",
        "unit", "uptimeMs", "channels", "schedules", "runtime"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsSchedules", "supportsChannels",
        "supportsPrime", "supportsManualDose", "supportsCalibrationWorkflow",
        "supportsReservoirRefill", "event"
    )
    private val CHANNEL_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime", "channelKind",
        "gpio", "ledcChannel", "group", "valueNow", "valueAuto", "valueManual",
        "manualTimeoutMs", "invert", "pwmResolutionBits", "pwmFrequencyHz", "editable",
        "dosing"
    )
    private val EDITABLE_KEYS = setOf(
        "hardware", "displayName", "hardwareCalibration", "dosingCalibration", "reservoir"
    )
    private val DOSING_KEYS = setOf(
        "unit", "doseMsPerMl", "lastCalibratedAt", "calibrated",
        "reservoirTrackingEnabled", "reservoirCapacityMl", "reservoirRemainingMl",
        "reservoirRemainingPercent"
    )
    private val SCHEDULE_KEYS = setOf(
        "index", "enabled", "runtimeEnabled", "name", "channelKey", "bound", "group",
        "weekdays", "startTimeMs", "startTime", "intervalOnMs", "intervalOn",
        "intervalOffMs", "intervalOff", "repeatCount", "amountMl", "pulseCountRuntime",
        "pulseOffPending", "pulseRemainingMs"
    )

    private const val DOSING_UNIT = "ml"
    private const val WEEKDAY_COUNT = 7
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val RESERVOIR_PERCENT_MAX = 100.0
    private const val UNAVAILABLE_MEASUREMENT = -1.0
    private const val MAX_DISPLAY_NAME_BYTES = 32
}
