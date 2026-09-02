package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import org.json.JSONArray
import org.json.JSONObject

data class DeviceCoolingV1ConfigApplyPayload(
    val expectedConfigRevision: Long,
    val controlMode: DeviceCoolingV1ControlMode? = null,
    val startTemperatureC: Double? = null,
    val fullSpeedTemperatureC: Double? = null,
    val silentModeEnabled: Boolean? = null
) {
    init {
        requireRevision(expectedConfigRevision, "expectedConfigRevision")
        require(
            controlMode != null ||
                startTemperatureC != null ||
                fullSpeedTemperatureC != null ||
                silentModeEnabled != null
        ) { "cooling.config.apply requires at least one config field." }
        startTemperatureC?.let(::requireCoolingTemperature)
        fullSpeedTemperatureC?.let(::requireCoolingTemperature)
        if (startTemperatureC != null && fullSpeedTemperatureC != null) {
            require(
                fullSpeedTemperatureC - startTemperatureC >=
                    DeviceCoolingV1Contract.Limit.MINIMUM_AUTOMATIC_GAP_C
            ) { "Cooling automatic temperature gap is too small." }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("expectedConfigRevision", expectedConfigRevision)
        .also { data ->
            controlMode?.let { data.put("controlMode", it.wireValue) }
            startTemperatureC?.let { data.put("startTemperatureC", it) }
            fullSpeedTemperatureC?.let { data.put("fullSpeedTemperatureC", it) }
            silentModeEnabled?.let { data.put("silentModeEnabled", it) }
        }
}

data class DeviceCoolingV1ManualApplyPayload(
    val expectedConfigRevision: Long,
    val fanKey: String = DeviceCoolingV1Contract.FAN_KEY,
    val targetPercent: Double
) {
    init {
        requireRevision(expectedConfigRevision, "expectedConfigRevision")
        require(fanKey == DeviceCoolingV1Contract.FAN_KEY) {
            "Cool Pro 1F exposes only fan1."
        }
        requireCoolingFanPercent(targetPercent)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("expectedConfigRevision", expectedConfigRevision)
        .put("fanKey", fanKey)
        .put("targetPercent", targetPercent)
}

data class DeviceCoolingV1ProgramSlotPayload(
    val startMinute: Int,
    val endMinute: Int,
    val fanOnTemperatureC: Double,
    val fanPercent: Double
) {
    init {
        val limit = DeviceCoolingV1Contract.Limit
        require(startMinute in 0 until limit.MINUTES_PER_DAY)
        require(endMinute in 1..limit.MINUTES_PER_DAY)
        require(startMinute < endMinute)
        require(startMinute % limit.PROGRAM_TIME_STEP_MINUTES == 0)
        require(endMinute % limit.PROGRAM_TIME_STEP_MINUTES == 0)
        require(endMinute - startMinute >= limit.PROGRAM_MINIMUM_DURATION_MINUTES)
        requireCoolingTemperature(fanOnTemperatureC)
        requireCoolingFanPercent(fanPercent)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("startMinute", startMinute)
        .put("endMinute", endMinute)
        .put("fanOnTemperatureC", fanOnTemperatureC)
        .put("fanPercent", fanPercent)
}

data class DeviceCoolingV1ProgramApplyPayload(
    val expectedProgramRevision: Long,
    val slots: List<DeviceCoolingV1ProgramSlotPayload>
) {
    init {
        requireRevision(expectedProgramRevision, "expectedProgramRevision")
        require(slots.size <= DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_CAPACITY)
        val ordered = slots.sortedBy(DeviceCoolingV1ProgramSlotPayload::startMinute)
        ordered.zipWithNext().forEach { (left, right) ->
            require(left.endMinute <= right.startMinute) {
                "Cooling program slots must not overlap."
            }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("expectedProgramRevision", expectedProgramRevision)
        .put("slots", JSONArray().also { array -> slots.forEach { array.put(it.toJson()) } })
}

data class DeviceCoolingV1HistoryGetPayload(
    val range: DeviceCoolingV1HistoryRange
) {
    fun toJson(): JSONObject = JSONObject().put("range", range.wireValue)
}

internal fun requireRevision(value: Long, field: String) {
    require(value in 0L..DeviceCoolingV1Contract.Limit.UINT32_MAX) {
        "$field must fit firmware uint32."
    }
}

internal fun requireCoolingTemperature(value: Double) {
    val limit = DeviceCoolingV1Contract.Limit
    require(value.isFinite())
    require(value in limit.TEMPERATURE_MINIMUM_C..limit.TEMPERATURE_MAXIMUM_C)
    requireAligned(value, limit.TEMPERATURE_MINIMUM_C, limit.TEMPERATURE_STEP_C)
}

internal fun requireCoolingFanPercent(value: Double) {
    val limit = DeviceCoolingV1Contract.Limit
    require(value.isFinite())
    require(value in limit.FAN_PERCENT_MINIMUM..limit.FAN_PERCENT_MAXIMUM)
    requireAligned(value, limit.FAN_PERCENT_MINIMUM, limit.FAN_PERCENT_STEP)
}

private fun requireAligned(value: Double, origin: Double, step: Double) {
    val scaled = (value - origin) / step
    require(kotlin.math.abs(scaled - kotlin.math.round(scaled)) <= 0.0001) {
        "$value is not aligned to firmware step $step."
    }
}
