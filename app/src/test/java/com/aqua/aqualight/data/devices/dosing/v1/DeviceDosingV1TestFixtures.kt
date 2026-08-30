package com.aqua.aqualight.data.devices.dosing.v1

import org.json.JSONArray
import org.json.JSONObject

@Suppress("MagicNumber", "TooManyFunctions")
internal object DeviceDosingV1TestFixtures {
    fun globalStatus(): JSONObject = envelope(channelCount = 2)
        .put("scheduling", scheduling())
        .put(
            "channels",
            JSONArray()
                .put(globalChannel("channel1", "Macro", revision = 7))
                .put(globalChannel("channel2", "Micro", revision = 3))
        )
        .put(
            "runtime",
            JSONObject()
                .put("module", "dosing")
                .put("supportsProgramApply", true)
                .put("supportsChannelConfig", true)
                .put("supportsChannelReset", true)
                .put("supportsPrime", true)
                .put("supportsManualDose", true)
                .put("supportsCalibrationWorkflow", true)
                .put("supportsReservoirRefill", true)
                .put("supportsChannelScopedStatus", true)
        )
        .put(
            "resources",
            JSONObject()
                .put("freeHeapBytes", 120_000)
                .put("minimumFreeHeapBytes", 100_000)
                .put("largestFreeBlockBytes", 80_000)
                .put("taskStackHighWaterBytes", 4_000)
                .put("checkpointWritesThisBoot", 2)
                .put("canonicalConfigBytes", 1_024)
                .put("programServiceBytes", 2_048)
                .put("runtimeSnapshotBytes", 3_072)
                .put("statusSnapshotBytes", 4_096)
        )

    fun channelStatus(
        channel: JSONObject = channelDetail()
    ): JSONObject = envelope(channelCount = 2)
        .put("scheduling", scheduling())
        .put("channel", channel)

    fun progressStatus(): JSONObject = envelope(channelCount = 2)
        .put("channelKey", "channel1")
        .put("revision", 7)
        .put("programEnabled", true)
        .put("programMode", "hourly24")
        .put(
            "progress",
            JSONObject()
                .put("scheduleState", "active")
                .put("total", 2)
                .put("completed", 1)
                .put("resolved", 1)
                .put("pending", 0)
                .put("running", 1)
                .put("skipped", 0)
                .put("uncertain", 0)
                .put("totalAmountMl", 2.4)
                .put("completedAmountMl", 1.1)
                .put("remainingAmountMl", 1.3)
                .put("completionPercent", 45.833)
                .put("executionCurrent", true)
                .put("programDayDate", "2026-08-14")
        )
        .put(
            "occurrences",
            JSONArray()
                .put(occurrence(index = 0, eventId = 901, timeMillis = 36_900_000, status = "completed"))
                .put(occurrence(index = 1, eventId = 902, timeMillis = 40_500_000, status = "running"))
        )

    fun directEvent(): JSONObject = JSONObject()
        .put("schema", DeviceDosingV1Contract.SCHEMA)
        .put("schemaVersion", DeviceDosingV1Contract.SCHEMA_VERSION)
        .put("channelKey", "channel1")
        .put("revision", 8)
        .put("storageHealthy", true)
        .put(
            "change",
            runtimeEvent(
                valid = true,
                sequence = 12,
                kind = "stateChanged",
                reason = "programChanged",
                source = "scheduled"
            )
        )

    fun savedMutation(operation: String): JSONObject = mutationBase(operation)
        .put("saved", true)

    fun primeStart(): JSONObject = mutationBase(DeviceDosingV1Contract.Literal.PRIME_START)
        .put("durationMs", 30_000)
        .put("doseMsPerMl", 1_250)
        .put("manualActive", true)

    fun simpleStop(operation: String): JSONObject = mutationBase(operation)
        .put("manualActive", false)

    fun doseNow(): JSONObject = mutationBase(DeviceDosingV1Contract.Literal.DOSE_NOW)
        .put("amountMl", 4.0)
        .put("durationMs", 5_000)
        .put("doseMsPerMl", 1_250)
        .put("usePendingCalibration", true)
        .put("manualActive", true)

    fun calibrationStart(): JSONObject =
        mutationBase(DeviceDosingV1Contract.Literal.CALIBRATION_START)
            .put("durationMs", 5_000)
            .put("calibrationState", "running")

    fun calibrationFinish(): JSONObject =
        mutationBase(DeviceDosingV1Contract.Literal.CALIBRATION_FINISH)
            .put("measuredMl", 4.0)
            .put("durationMs", 5_000)
            .put("pendingDoseMsPerMl", 1_250)
            .put("calibrationState", "pendingVerification")

    fun calibrationConfirm(): JSONObject =
        mutationBase(
            DeviceDosingV1Contract.Literal.CALIBRATION_CONFIRM,
            detail = channelDetail(revision = 8)
        )
            .put("revision", 8)
            .put("doseMsPerMl", 1_250)
            .put("lastCalibratedAt", 1_786_320_000)
            .put("calibrationState", "idle")
            .put("saved", true)

    fun calibrationCancel(): JSONObject =
        mutationBase(DeviceDosingV1Contract.Literal.CALIBRATION_CANCEL)
            .put("discardedPendingCalibration", true)
            .put("calibrationState", "idle")

    fun reservoirRefill(): JSONObject =
        mutationBase(DeviceDosingV1Contract.Literal.RESERVOIR_REFILL)
            .put("reservoirRemainingMlBefore", 35.5)
            .put("reservoirRemainingMl", 500.0)
            .put("persisted", true)

    @Suppress("LongMethod") // The fixture mirrors one complete firmware channel document.
    fun channelDetail(
        revision: Long = 7,
        runtimeReason: String = "none",
        programMode: String = "hourly24"
    ): JSONObject = JSONObject()
        .put("channelKey", "channel1")
        .put("revision", revision)
        .put("runtimeEnabled", true)
        .put("runtimeReason", runtimeReason)
        .put("program", program(programMode))
        .put("usageToday", usage())
        .put("index", 0)
        .put("defaultName", "Channel 1")
        .put("displayName", "Macro")
        .put("effectiveName", "Macro")
        .put("profileManaged", true)
        .put("deliveryAccountingCertain", true)
        .put(
            "hardware",
            JSONObject()
                .put("channelType", "pwm")
                .put("gpio", 18)
                .put("ledcChannel", 0)
                .put("resolutionBits", 12)
                .put("frequencyHz", 1_000)
        )
        .put(
            "calibration",
            JSONObject()
                .put("confirmed", true)
                .put("doseMsPerMl", 1_250)
                .put("lastCalibratedAt", 1_786_320_000)
                .put("state", "idle")
                .put("durationMs", 0)
                .put("measuredMl", 0.0)
                .put("pendingDoseMsPerMl", 0.0)
                .put("verificationDoseStarted", false)
                .put("verificationDoseComplete", false)
        )
        .put(
            "reservoir",
            JSONObject()
                .put("trackingEnabled", true)
                .put("capacityMl", 500.0)
                .put("remainingMl", 325.0)
                .put("accountingCertain", true)
                .put("lowLevelActive", false)
                .put("remainingPercent", 65.0)
        )
        .put(
            "activeRun",
            JSONObject()
                .put("active", false)
                .put("source", "none")
                .put("targetAmountMl", 0.0)
                .put("remainingMs", 0)
        )
        .put("lastRuntimeEvent", runtimeEvent())
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", true)
                .put("dosingCalibration", true)
                .put("reservoir", true)
        )

    private fun envelope(channelCount: Int): JSONObject = JSONObject()
        .put("supported", true)
        .put("schema", DeviceDosingV1Contract.SCHEMA)
        .put("schemaVersion", DeviceDosingV1Contract.SCHEMA_VERSION)
        .put("unit", DeviceDosingV1Contract.UNIT)
        .put("channelCount", channelCount)
        .put("uptimeMs", 123_456)
        .put("bootReady", true)
        .put("storageHealthy", true)
        .put("storageIssue", "")

    private fun scheduling(): JSONObject = JSONObject()
        .put("contract", DeviceDosingV1Contract.SCHEMA)
        .put("schemaVersion", DeviceDosingV1Contract.SCHEMA_VERSION)
        .put("amountResolutionMl", 0.001)
        .put("maxEventsPerChannel", 24)
        .put("maxCustomPeriodsPerChannel", 24)
        .put("scheduledDispatchGraceMs", 2_000)
        .put("missedDoseRecoveryProgramDay", true)
        .put("minPumpRunDurationMs", 100)
        .put("maxPumpRunDurationMs", 3_600_000)
        .put("maxManualDoseMl", 1_000)
        .put("supportsWeekdayRecurrence", true)
        .put("supportsMissedDoseRecovery", true)
        .put("supportsChannelReset", true)
        .put("supportsDailyDeliveredUsage", true)
        .put(
            "supportedModes",
            JSONArray().put("single").put("hourly24").put("customPeriods").put("timer")
        )
        .put(
            "weekdayOrder",
            JSONArray()
                .put("monday")
                .put("tuesday")
                .put("wednesday")
                .put("thursday")
                .put("friday")
                .put("saturday")
                .put("sunday")
        )
        .put(
            "effectiveScheduledDose",
            JSONObject()
                .put("available", true)
                .put("minDoseMl", 0.08)
                .put("maxDoseMl", 2_880.0)
        )

    private fun globalChannel(
        channelKey: String,
        name: String,
        revision: Long
    ): JSONObject = JSONObject()
        .put("channelKey", channelKey)
        .put("effectiveName", name)
        .put("revision", revision)
        .put("runtimeEnabled", true)
        .put("runtimeReason", "none")
        .put("programEnabled", true)
        .put("programMode", "hourly24")
        .put("deliveryAccountingCertain", true)
        .put("usageToday", usage())
        .put(
            "reservoir",
            JSONObject()
                .put("trackingEnabled", true)
                .put("remainingMl", 325.0)
                .put("accountingCertain", true)
                .put("lowLevelActive", false)
        )
        .put("active", false)

    private fun usage(): JSONObject = JSONObject()
        .put("dateValid", true)
        .put("localDate", "2026-08-14")
        .put("scheduledDeliveredMl", 1.1)
        .put("manualDeliveredMl", 0.3)
        .put("totalDeliveredMl", 1.4)

    private fun program(mode: String): JSONObject {
        val config = when (mode) {
            "single" -> JSONObject()
                .put("dailyDoseMl", 2.4)
                .put("startTimeMs", 36_900_000)
            "hourly24" -> JSONObject()
                .put("dailyDoseMl", 2.4)
                .put("minuteOfHour", 15)
            "customPeriods" -> JSONObject()
                .put("dailyDoseMl", 6.0)
                .put(
                    "periods",
                    JSONArray().put(
                        JSONObject()
                            .put("startTimeMs", 36_000_000)
                            .put("endTimeMs", 39_600_000)
                            .put("doseCount", 3)
                    )
                )
            "timer" -> JSONObject().put(
                "events",
                JSONArray().put(
                    JSONObject().put("timeMs", 36_000_000).put("amountMl", 1.0)
                )
            )
            else -> JSONObject().put("futureField", "preserved")
        }
        return JSONObject()
            .put("enabled", true)
            .put(
                "weekdays",
                JSONArray().put(true).put(true).put(true).put(true).put(true).put(true).put(true)
            )
            .put("mode", mode)
            .put("missedDoseRecoveryEnabled", false)
            .put("config", config)
    }

    private fun occurrence(
        index: Int,
        eventId: Long,
        timeMillis: Long,
        status: String
    ): JSONObject = JSONObject()
        .put("index", index)
        .put("eventId", eventId)
        .put("programDayOffset", 0)
        .put("timeMs", timeMillis)
        .put("amountMl", 1.2)
        .put("status", status)

    private fun runtimeEvent(
        valid: Boolean = true,
        sequence: Long = 11,
        kind: String = "runCompleted",
        reason: String = "naturalDeadline",
        source: String = "scheduled"
    ): JSONObject = JSONObject()
        .put("valid", valid)
        .put("sequence", sequence)
        .put("occurredAtMs", 122_000)
        .put("kind", kind)
        .put("reason", reason)
        .put("source", source)

    private fun mutationBase(
        operation: String,
        detail: JSONObject = channelDetail()
    ): JSONObject = JSONObject()
        .put("operation", operation)
        .put("channelKey", "channel1")
        .put("event", DeviceDosingV1Contract.STATUS_CHANGED_EVENT)
        .put("channel", detail)
}
