package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import org.json.JSONObject

enum class DeviceLightErrorReason(val wireValue: String) {
    STALE_REVISION("STALE_REVISION"),
    AUTO_CAPACITY_REACHED("AUTO_CAPACITY_REACHED"),
    AUTO_PROGRAM_OVERLAP("AUTO_PROGRAM_OVERLAP"),
    AUTO_PROGRAM_NOT_FOUND("AUTO_PROGRAM_NOT_FOUND"),
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
    STORAGE_COMMIT_FAILED("STORAGE_COMMIT_FAILED");

    companion object {
        fun fromWireExact(value: String): DeviceLightErrorReason =
            entries.singleOrNull { it.wireValue == value }
                ?: error("Unknown Light V1 error reason: $value")
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
    val capacity: Int? = null,
    val programCount: Int? = null,
    val conflict: DeviceLightOverlapConflict? = null,
    val additionalConflictCount: Int? = null,
    val rollbackOutputHealthy: Boolean? = null
)

/** Strict decoder for firmware error.data. Empty data is valid for envelope-level failures. */
fun DeviceRuntimeCommandOutcome.FirmwareError.lightV1Data(): DeviceLightFirmwareErrorData {
    require(module == DeviceLightRuntimeContract.MODULE)
    val data = JSONObject(structuredDataJson)
    if (data.length() == 0) return DeviceLightFirmwareErrorData(reason = null)
    val reason = DeviceLightErrorReason.fromWireExact(data.requireLightText("reason"))
    return when (reason) {
        DeviceLightErrorReason.STALE_REVISION,
        DeviceLightErrorReason.AUTO_PROGRAM_NOT_FOUND -> {
            data.requireLightKeys(setOf("reason", "actualRevision"), "Light error.data")
            DeviceLightFirmwareErrorData(
                reason = reason,
                actualRevision = data.requireLightLong(
                    "actualRevision",
                    0,
                    DeviceLightRuntimeContract.Limit.UINT32_MAX
                )
            )
        }
        DeviceLightErrorReason.AUTO_CAPACITY_REACHED -> {
            data.requireLightKeys(
                setOf("reason", "actualRevision", "capacity", "programCount"),
                "Light error.data"
            )
            DeviceLightFirmwareErrorData(
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
        DeviceLightErrorReason.AUTO_PROGRAM_OVERLAP -> {
            data.requireLightKeys(
                setOf("reason", "actualRevision", "conflict", "additionalConflictCount"),
                "Light error.data"
            )
            DeviceLightFirmwareErrorData(
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
        DeviceLightErrorReason.OUTPUT_TRANSACTION_FAILED,
        DeviceLightErrorReason.STORAGE_COMMIT_FAILED -> {
            data.requireLightKeys(
                setOf("reason", "rollbackOutputHealthy"),
                "Light error.data"
            )
            DeviceLightFirmwareErrorData(
                reason = reason,
                rollbackOutputHealthy = data.requireLightBoolean("rollbackOutputHealthy")
            )
        }
        else -> {
            data.requireLightKeys(setOf("reason"), "Light error.data")
            DeviceLightFirmwareErrorData(reason = reason)
        }
    }
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
