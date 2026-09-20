package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import org.json.JSONObject

/** Known runtime reasons plus a lossless fallback for newer firmware contracts. */
sealed interface DeviceLightErrorReason {
    val wireValue: String

    enum class Known(override val wireValue: String) : DeviceLightErrorReason {
        STALE_REVISION("STALE_REVISION"),
        STALE_STORAGE_GENERATION("STALE_STORAGE_GENERATION"),
        AUTO_CAPACITY_REACHED("AUTO_CAPACITY_REACHED"),
        AUTO_PROGRAM_OVERLAP("AUTO_PROGRAM_OVERLAP"),
        AUTO_PROGRAM_NOT_FOUND("AUTO_PROGRAM_NOT_FOUND"),
        AUTO_PLAN_ID_INVALID("AUTO_PLAN_ID_INVALID"),
        AUTO_PLAN_PHASE_COUNT("AUTO_PLAN_PHASE_COUNT"),
        AUTO_PLAN_INITIAL_START_PERCENT("AUTO_PLAN_INITIAL_START_PERCENT"),
        AUTO_PLAN_DATE_RANGE("AUTO_PLAN_DATE_RANGE"),
        AUTO_PLAN_PHASE_GAP("AUTO_PLAN_PHASE_GAP"),
        AUTO_PLAN_OVERNIGHT_UNSUPPORTED("AUTO_PLAN_OVERNIGHT_UNSUPPORTED"),
        AUTO_PLAN_TRANSITION("AUTO_PLAN_TRANSITION"),
        AUTO_PLAN_NOT_FOUND("AUTO_PLAN_NOT_FOUND"),
        AUTO_PLAN_SELECTED("AUTO_PLAN_SELECTED"),
        AUTO_PLAN_INTERNAL_ERROR("AUTO_PLAN_INTERNAL_ERROR"),
        INVALID_WEEKDAYS_MASK("INVALID_WEEKDAYS_MASK"),
        INVALID_TIME_VALUE("INVALID_TIME_VALUE"),
        INVALID_RAMP_VALUE("INVALID_RAMP_VALUE"),
        RAMP_DOES_NOT_FIT("RAMP_DOES_NOT_FIT"),
        INVALID_SCENE_VALUE("INVALID_SCENE_VALUE"),
        CUSTOM_POINT_COUNT("CUSTOM_POINT_COUNT"),
        CUSTOM_POINT_ORDER("CUSTOM_POINT_ORDER"),
        CUSTOM_POINT_VALUE("CUSTOM_POINT_VALUE"),
        ACCLIMATION_START_PERCENT("ACCLIMATION_START_PERCENT"),
        ACCLIMATION_DURATION("ACCLIMATION_DURATION"),
        RTC_NOT_READY("RTC_NOT_READY"),
        OUTPUT_TRANSACTION_FAILED("OUTPUT_TRANSACTION_FAILED"),
        STORAGE_COMMIT_FAILED("STORAGE_COMMIT_FAILED"),
        // Executable firmware uses this when an Invalid mutation has no domain-specific reason.
        INVALID_VALUE("INVALID_VALUE")
    }

    data class Unknown(
        val rawValue: String
    ) : DeviceLightErrorReason {
        override val wireValue: String = rawValue
    }

    companion object {
        private val knownByWireValue = Known.entries.associateBy { it.wireValue }

        fun fromWire(value: String): DeviceLightErrorReason =
            knownByWireValue[value] ?: Unknown(value)
    }
}

data class DeviceLightOverlapGeometry(
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long
)

data class DeviceLightOverlapConflict(
    val withProgramId: String,
    val occurrenceWeekdayMask: Int,
    val overlapStartTimeMs: Long,
    val overlapEndTimeMs: Long,
    val existing: DeviceLightOverlapGeometry,
    val candidate: DeviceLightOverlapGeometry
)

data class DeviceLightFirmwareErrorData(
    val reason: DeviceLightErrorReason?,
    val actualRevision: Long? = null,
    val actualStorageGeneration: Long? = null,
    val capacity: Int? = null,
    val programCount: Int? = null,
    val conflict: DeviceLightOverlapConflict? = null,
    val additionalConflictCount: Int? = null,
    val rollbackOutputHealthy: Boolean? = null
)

/**
 * Strictly decodes known firmware error.data shapes. Empty envelope data is valid, while an
 * unknown reason and its source JSON remain available for forward-compatible handling.
 */
fun DeviceRuntimeCommandOutcome.FirmwareError.lightV1Data(): DeviceLightFirmwareErrorData {
    require(module == DeviceLightRuntimeContract.MODULE)
    val data = JSONObject(structuredDataJson)
    if (data.length() == 0) return DeviceLightFirmwareErrorData(reason = null)
    return parseLightV1ErrorData(data)
}

private fun parseLightV1ErrorData(data: JSONObject): DeviceLightFirmwareErrorData {
    val reason = DeviceLightErrorReason.fromWire(data.requireLightText("reason"))
    return when (reason) {
        DeviceLightErrorReason.Known.STALE_REVISION,
        DeviceLightErrorReason.Known.AUTO_PROGRAM_NOT_FOUND -> parseRevisionError(data, reason)
        DeviceLightErrorReason.Known.STALE_STORAGE_GENERATION ->
            parseStorageGenerationError(data, reason)
        DeviceLightErrorReason.Known.AUTO_CAPACITY_REACHED -> parseCapacityError(data, reason)
        DeviceLightErrorReason.Known.AUTO_PROGRAM_OVERLAP -> parseOverlapError(data, reason)
        DeviceLightErrorReason.Known.OUTPUT_TRANSACTION_FAILED,
        DeviceLightErrorReason.Known.STORAGE_COMMIT_FAILED -> parseRollbackError(data, reason)
        is DeviceLightErrorReason.Unknown -> DeviceLightFirmwareErrorData(reason = reason)
        else -> {
            data.requireLightKeys(setOf("reason"), "Light error.data")
            DeviceLightFirmwareErrorData(reason = reason)
        }
    }
}

private fun parseRevisionError(
    data: JSONObject,
    reason: DeviceLightErrorReason
): DeviceLightFirmwareErrorData {
    data.requireLightKeys(setOf("reason", "actualRevision"), "Light error.data")
    return DeviceLightFirmwareErrorData(
        reason = reason,
        actualRevision = data.requireLightLong(
            "actualRevision",
            0,
            DeviceLightRuntimeContract.Limit.UINT32_MAX
        )
    )
}

private fun parseStorageGenerationError(
    data: JSONObject,
    reason: DeviceLightErrorReason
): DeviceLightFirmwareErrorData {
    data.requireLightKeys(setOf("reason", "actualStorageGeneration"), "Light error.data")
    return DeviceLightFirmwareErrorData(
        reason = reason,
        actualStorageGeneration = data.requireLightLong(
            "actualStorageGeneration",
            0,
            DeviceLightRuntimeContract.Limit.UINT32_MAX
        )
    )
}

private fun parseCapacityError(
    data: JSONObject,
    reason: DeviceLightErrorReason
): DeviceLightFirmwareErrorData {
    data.requireLightKeys(
        setOf("reason", "actualRevision", "capacity", "programCount"),
        "Light error.data"
    )
    return DeviceLightFirmwareErrorData(
        reason = reason,
        actualRevision = data.requireLightLong(
            "actualRevision",
            0,
            DeviceLightRuntimeContract.Limit.UINT32_MAX
        ),
        capacity = data.requireLightInt("capacity", 0),
        programCount = data.requireLightInt("programCount", 0)
    ).also {
        require(it.capacity == DeviceLightRuntimeContract.Limit.AUTO_PROGRAM_CAPACITY)
        require(requireNotNull(it.programCount) <= requireNotNull(it.capacity))
    }
}

private fun parseOverlapError(
    data: JSONObject,
    reason: DeviceLightErrorReason
): DeviceLightFirmwareErrorData {
    data.requireLightKeys(
        setOf("reason", "actualRevision", "conflict", "additionalConflictCount"),
        "Light error.data"
    )
    return DeviceLightFirmwareErrorData(
        reason = reason,
        actualRevision = data.requireLightLong(
            "actualRevision",
            0,
            DeviceLightRuntimeContract.Limit.UINT32_MAX
        ),
        conflict = parseConflict(data.requireLightObject("conflict")),
        additionalConflictCount = data.requireLightInt("additionalConflictCount", 0)
    )
}

private fun parseRollbackError(
    data: JSONObject,
    reason: DeviceLightErrorReason
): DeviceLightFirmwareErrorData {
    data.requireLightKeys(setOf("reason", "rollbackOutputHealthy"), "Light error.data")
    return DeviceLightFirmwareErrorData(
        reason = reason,
        rollbackOutputHealthy = data.requireLightBoolean("rollbackOutputHealthy")
    )
}

internal object DeviceLightCommandValidation {
    fun requireProduct(expected: DeviceLightProduct, actual: DeviceLightProduct) {
        require(expected == actual) { "Light payload product differs from authoritative status." }
    }

    fun validateTemperatureProtection(
        request: DeviceLightTemperatureProtectionSetPayload,
        result: DeviceLightTemperatureProtectionSetResult
    ) {
        require(result.saveRequested == request.save)
        require(result.saved == request.save)
        require(result.status.temperatureProtection.thresholdC == request.thresholdC)
    }
}

private fun parseConflict(data: JSONObject): DeviceLightOverlapConflict {
    data.requireLightKeys(
        setOf(
            "withProgramId", "occurrenceWeekdayMask", "overlapStartTimeMs", "overlapEndTimeMs",
            "existing", "candidate"
        ),
        "Light overlap conflict"
    )
    val withProgramId = data.requireLightText("withProgramId")
    require(PROGRAM_ID.matches(withProgramId))
    return DeviceLightOverlapConflict(
        withProgramId = withProgramId,
        occurrenceWeekdayMask = data.requireLightInt(
            "occurrenceWeekdayMask",
            DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN,
            DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
        ),
        overlapStartTimeMs = data.requireLightLong(
            "overlapStartTimeMs",
            0,
            DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY
        ),
        overlapEndTimeMs = data.requireLightLong(
            "overlapEndTimeMs",
            0,
            DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY
        ),
        existing = parseGeometry(data.requireLightObject("existing")),
        candidate = parseGeometry(data.requireLightObject("candidate"))
    )
}

private fun parseGeometry(data: JSONObject): DeviceLightOverlapGeometry {
    data.requireLightKeys(
        setOf("weekdaysMask", "startTimeMs", "endTimeMs"),
        "Light overlap geometry"
    )
    return DeviceLightOverlapGeometry(
        weekdaysMask = data.requireLightInt(
            "weekdaysMask",
            DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MIN,
            DeviceLightRuntimeContract.Limit.WEEKDAY_MASK_MAX
        ),
        startTimeMs = data.requireLightLong(
            "startTimeMs",
            0,
            DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
        ),
        endTimeMs = data.requireLightLong(
            "endTimeMs",
            0,
            DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
        )
    )
}

private val PROGRAM_ID = Regex("^ap-[0-9a-f]{8}$")
