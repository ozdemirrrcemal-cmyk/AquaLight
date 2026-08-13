package com.aqua.aqualight.data.devices.runtime.modules.dosing

import org.json.JSONArray
import org.json.JSONObject

/** Exact JSON builders for the executable `aqualight.dosing.v1` firmware API. */
internal object DeviceDosingRuntimeFixtures {
    fun globalStatus(
        channelCount: Int = 2,
        uptimeMs: Long = 10_000L,
        revision: Long = 7L
    ): JSONObject = commonEnvelope(channelCount, uptimeMs)
        .put("scheduling", scheduling())
        .put(
            "channels",
            JSONArray().also { array ->
                repeat(channelCount) { index ->
                    array.put(
                        globalChannelSummary(
                            channelKey = "channel${index + 1}",
                            effectiveName = "Channel ${index + 1}",
                            revision = revision
                        )
                    )
                }
            }
        )
        .put("runtime", runtimeCapabilities())
        .put("resources", resources())

    @Suppress("LongParameterList")
    fun channelStatus(
        channelKey: String = "channel1",
        index: Int = 0,
        channelCount: Int = 2,
        uptimeMs: Long = 10_000L,
        revision: Long = 7L,
        calibrated: Boolean = true,
        calibrationState: String = "idle",
        program: JSONObject? = singleProgram(),
        displayName: String = "",
        effectiveName: String = "Channel 1",
        active: Boolean = false,
        activeSource: String = "none",
        remainingMs: Long = 0L,
        verificationDoseComplete: Boolean = false
    ): JSONObject = commonEnvelope(channelCount, uptimeMs)
        .put("scheduling", scheduling(calibrated = calibrated))
        .put(
            "channel",
            channelDetail(
                channelKey = channelKey,
                index = index,
                revision = revision,
                calibrated = calibrated,
                calibrationState = calibrationState,
                program = program,
                displayName = displayName,
                effectiveName = effectiveName,
                active = active,
                activeSource = activeSource,
                remainingMs = remainingMs,
                verificationDoseComplete = verificationDoseComplete
            )
        )

    fun statusChanged(
        channelKey: String = "channel1",
        revision: Long = 8L,
        sequence: Long = 3L,
        reason: String = "programChanged"
    ): JSONObject = JSONObject()
        .put("schema", "aqualight.dosing.v1")
        .put("schemaVersion", 1)
        .put("channelKey", channelKey)
        .put("revision", revision)
        .put("storageHealthy", true)
        .put(
            "change",
            runtimeEvent(
                valid = true,
                sequence = sequence,
                kind = "stateChanged",
                reason = reason,
                source = "none"
            )
        )

    fun channelConfigApplyResult(
        channelKey: String = "channel1",
        revision: Long = 8L,
        displayName: String = "Macro Pump"
    ): JSONObject = mutationBase(
        operation = "channelConfigApply",
        channelKey = channelKey,
        revision = revision,
        displayName = displayName,
        effectiveName = displayName
    ).put("saved", true)

    fun programApplyResult(
        channelKey: String = "channel1",
        revision: Long = 8L,
        program: JSONObject = singleProgram()
    ): JSONObject = mutationBase(
        operation = "programApply",
        channelKey = channelKey,
        revision = revision,
        program = program
    ).put("saved", true)

    fun channelResetResult(
        channelKey: String = "channel1",
        revision: Long = 8L
    ): JSONObject = mutationBase(
        operation = "channelReset",
        channelKey = channelKey,
        revision = revision,
        program = null
    ).put("saved", true)

    fun primeStartResult(channelKey: String = "channel1"): JSONObject =
        mutationBase(
            operation = "primeStart",
            channelKey = channelKey,
            revision = 7L,
            active = true,
            activeSource = "prime"
        )
            .put("durationMs", 5_000L)
            .put("doseMsPerMl", 1_250L)
            .put("manualActive", true)

    fun primeStopResult(channelKey: String = "channel1"): JSONObject =
        mutationBase("primeStop", channelKey, revision = 7L)
            .put("manualActive", false)

    fun doseNowResult(
        channelKey: String = "channel1",
        pending: Boolean = false
    ): JSONObject = mutationBase(
        operation = "doseNow",
        channelKey = channelKey,
        revision = 7L,
        calibrationState = if (pending) "pendingVerification" else "idle",
        active = true,
        activeSource = if (pending) "verification" else "manual"
    )
        .put("amountMl", if (pending) 4.0 else 2.0)
        .put("durationMs", if (pending) 5_000L else 2_500L)
        .put("doseMsPerMl", 1_250L)
        .put("usePendingCalibration", pending)
        .put("manualActive", true)

    fun doseStopResult(channelKey: String = "channel1"): JSONObject =
        mutationBase("doseStop", channelKey, revision = 7L)
            .put("manualActive", false)

    fun calibrationStartResult(channelKey: String = "channel1"): JSONObject =
        mutationBase(
            operation = "calibrationStart",
            channelKey = channelKey,
            revision = 7L,
            calibrationState = "running",
            active = true,
            activeSource = "calibration"
        )
            .put("durationMs", 5_000L)
            .put("calibrationState", "running")

    fun calibrationFinishResult(channelKey: String = "channel1"): JSONObject =
        mutationBase(
            operation = "calibrationFinish",
            channelKey = channelKey,
            revision = 7L,
            calibrationState = "pendingVerification"
        )
            .put("measuredMl", 4.0)
            .put("durationMs", 5_000L)
            .put("pendingDoseMsPerMl", 1_250L)
            .put("calibrationState", "pendingVerification")

    fun calibrationConfirmResult(channelKey: String = "channel1"): JSONObject =
        mutationBase(
            operation = "calibrationConfirm",
            channelKey = channelKey,
            revision = 8L
        )
            .put("revision", 8L)
            .put("doseMsPerMl", 1_250L)
            .put("lastCalibratedAt", 1_786_320_000L)
            .put("calibrationState", "idle")
            .put("saved", true)

    fun calibrationCancelResult(channelKey: String = "channel1"): JSONObject =
        mutationBase("calibrationCancel", channelKey, revision = 7L)
            .put("discardedPendingCalibration", true)
            .put("calibrationState", "idle")

    fun reservoirRefillResult(channelKey: String = "channel1"): JSONObject =
        mutationBase("reservoirRefill", channelKey, revision = 7L)
            .put("reservoirRemainingMlBefore", 100.0)
            .put("reservoirRemainingMl", 450.0)
            .put("persisted", true)

    fun singleProgram(
        enabled: Boolean = true,
        recovery: Boolean = false,
        dailyDoseMl: Double = 3.0,
        startTimeMs: Long = 36_000_000L
    ): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("weekdays", booleanWeekdays())
        .put("mode", "single")
        .put("missedDoseRecoveryEnabled", recovery)
        .put(
            "config",
            JSONObject()
                .put("dailyDoseMl", dailyDoseMl)
                .put("startTimeMs", startTimeMs)
        )

    fun hourlyProgram(
        dailyDoseMl: Double = 2.4,
        startTimeMs: Long = 36_900_000L
    ): JSONObject = JSONObject()
        .put("enabled", true)
        .put("weekdays", booleanWeekdays())
        .put("mode", "hourly24")
        .put("missedDoseRecoveryEnabled", false)
        .put(
            "config",
            JSONObject()
                .put("dailyDoseMl", dailyDoseMl)
                .put("startTimeMs", startTimeMs)
        )

    fun customProgram(): JSONObject = JSONObject()
        .put("enabled", true)
        .put("weekdays", booleanWeekdays())
        .put("mode", "customPeriods")
        .put("missedDoseRecoveryEnabled", true)
        .put(
            "config",
            JSONObject()
                .put("dailyDoseMl", 6.0)
                .put(
                    "periods",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("startTimeMs", 36_000_000L)
                                .put("endTimeMs", 39_600_000L)
                                .put("doseCount", 3)
                        )
                        .put(
                            JSONObject()
                                .put("startTimeMs", 50_400_000L)
                                .put("endTimeMs", 57_600_000L)
                                .put("doseCount", 3)
                        )
                )
        )

    fun timerProgram(): JSONObject = JSONObject()
        .put("enabled", true)
        .put("weekdays", booleanWeekdays())
        .put("mode", "timer")
        .put("missedDoseRecoveryEnabled", false)
        .put(
            "config",
            JSONObject().put(
                "events",
                JSONArray()
                    .put(JSONObject().put("timeMs", 36_000_000L).put("amountMl", 1.0))
                    .put(JSONObject().put("timeMs", 50_400_000L).put("amountMl", 5.0))
            )
        )

    fun scheduling(calibrated: Boolean = true): JSONObject = JSONObject()
        .put("contract", "aqualight.dosing.v1")
        .put("schemaVersion", 1)
        .put("amountResolutionMl", 0.001)
        .put("maxEventsPerChannel", 24)
        .put("maxCustomPeriodsPerChannel", 24)
        .put("scheduledDispatchGraceMs", 2_000L)
        .put("missedDoseRecoveryWindowMs", 900_000L)
        .put("minPumpRunDurationMs", 100L)
        .put("maxPumpRunDurationMs", 3_600_000L)
        .put("maxManualDoseMl", 1_000.0)
        .put("supportsWeekdayRecurrence", true)
        .put("supportsMissedDoseRecovery", true)
        .put("supportsChannelReset", true)
        .put("supportsDailyDeliveredUsage", true)
        .put(
            "supportedModes",
            JSONArray(listOf("single", "hourly24", "customPeriods", "timer"))
        )
        .put(
            "weekdayOrder",
            JSONArray(
                listOf(
                    "monday",
                    "tuesday",
                    "wednesday",
                    "thursday",
                    "friday",
                    "saturday",
                    "sunday"
                )
            )
        )
        .put(
            "effectiveScheduledDose",
            JSONObject()
                .put("available", calibrated)
                .put("minDoseMl", if (calibrated) 0.1 else JSONObject.NULL)
                .put("maxDoseMl", if (calibrated) 3_600.0 else JSONObject.NULL)
        )

    private fun commonEnvelope(channelCount: Int, uptimeMs: Long) = JSONObject()
        .put("supported", true)
        .put("schema", "aqualight.dosing.v1")
        .put("schemaVersion", 1)
        .put("unit", "ml")
        .put("channelCount", channelCount)
        .put("uptimeMs", uptimeMs)
        .put("bootReady", true)
        .put("storageHealthy", true)
        .put("storageIssue", "")

    private fun globalChannelSummary(
        channelKey: String,
        effectiveName: String,
        revision: Long
    ) = JSONObject()
        .put("channelKey", channelKey)
        .put("effectiveName", effectiveName)
        .put("revision", revision)
        .put("runtimeEnabled", true)
        .put("runtimeReason", "none")
        .put("programEnabled", true)
        .put("programMode", "single")
        .put("deliveryAccountingCertain", true)
        .put("usageToday", usageToday())
        .put(
            "reservoir",
            JSONObject()
                .put("trackingEnabled", true)
                .put("remainingMl", 300.0)
                .put("accountingCertain", true)
        )
        .put("active", false)

    private fun runtimeCapabilities() = JSONObject()
        .put("module", "dosing")
        .put("supportsProgramApply", true)
        .put("supportsChannelConfig", true)
        .put("supportsChannelReset", true)
        .put("supportsPrime", true)
        .put("supportsManualDose", true)
        .put("supportsCalibrationWorkflow", true)
        .put("supportsReservoirRefill", true)
        .put("supportsChannelScopedStatus", true)
        .put("displayNameEditable", true)

    private fun resources() = JSONObject()
        .put("freeHeapBytes", 100_000L)
        .put("minimumFreeHeapBytes", 80_000L)
        .put("largestFreeBlockBytes", 50_000L)
        .put("taskStackHighWaterBytes", 4_000L)
        .put("checkpointWritesThisBoot", 3L)
        .put("canonicalConfigBytes", 1_000L)
        .put("programServiceBytes", 2_000L)
        .put("runtimeSnapshotBytes", 3_000L)
        .put("statusSnapshotBytes", 4_000L)

    @Suppress("LongParameterList")
    private fun channelDetail(
        channelKey: String,
        index: Int,
        revision: Long,
        calibrated: Boolean,
        calibrationState: String,
        program: JSONObject?,
        displayName: String,
        effectiveName: String,
        active: Boolean,
        activeSource: String,
        remainingMs: Long,
        verificationDoseComplete: Boolean
    ) = JSONObject()
        .put("channelKey", channelKey)
        .put("revision", revision)
        .put("runtimeEnabled", calibrated && program?.optBoolean("enabled", false) == true)
        .put(
            "runtimeReason",
            if (!calibrated) {
                "missingCalibration"
            } else if (program?.optBoolean("enabled", false) == true) {
                "none"
            } else {
                "programDisabled"
            }
        )
        .put("program", program ?: JSONObject.NULL)
        .put("usageToday", usageToday())
        .put("index", index)
        .put("defaultName", "Channel ${index + 1}")
        .put("displayName", displayName)
        .put("effectiveName", effectiveName)
        .put("profileManaged", true)
        .put("deliveryAccountingCertain", true)
        .put(
            "hardware",
            JSONObject()
                .put("channelType", "pump")
                .put("gpio", 12 + index)
                .put("ledcChannel", index)
                .put("resolutionBits", 8)
                .put("frequencyHz", 1_000)
        )
        .put(
            "calibration",
            JSONObject()
                .put("confirmed", calibrated)
                .put("doseMsPerMl", if (calibrated) 1_250L else 0L)
                .put("lastCalibratedAt", if (calibrated) 1_786_320_000L else 0L)
                .put("state", calibrationState)
                .put("durationMs", if (calibrationState == "running") 5_000L else 0L)
                .put("measuredMl", if (calibrationState == "pendingVerification") 4.0 else 0.0)
                .put(
                    "pendingDoseMsPerMl",
                    if (calibrationState == "pendingVerification") 1_250L else 0L
                )
                .put(
                    "verificationDoseStarted",
                    calibrationState == "pendingVerification" &&
                        (activeSource == "verification" || verificationDoseComplete)
                )
                .put("verificationDoseComplete", verificationDoseComplete)
        )
        .put(
            "reservoir",
            JSONObject()
                .put("trackingEnabled", true)
                .put("capacityMl", 450.0)
                .put("remainingMl", 300.0)
                .put("accountingCertain", true)
                .put("remainingPercent", 66.666)
        )
        .put(
            "activeRun",
            JSONObject()
                .put("active", active)
                .put("source", activeSource)
                .put("targetAmountMl", if (active) 4.0 else 0.0)
                .put("remainingMs", remainingMs)
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

    private fun usageToday() = JSONObject()
        .put("dateValid", true)
        .put("localDate", "2026-08-13")
        .put("scheduledDeliveredMl", 6.0)
        .put("manualDeliveredMl", 2.0)
        .put("totalDeliveredMl", 8.0)

    private fun runtimeEvent(
        valid: Boolean = false,
        sequence: Long = 0L,
        kind: String = "none",
        reason: String = "none",
        source: String = "none"
    ) = JSONObject()
        .put("valid", valid)
        .put("sequence", sequence)
        .put("occurredAtMs", if (valid) 9_000L else 0L)
        .put("kind", kind)
        .put("reason", reason)
        .put("source", source)

    @Suppress("LongParameterList")
    private fun mutationBase(
        operation: String,
        channelKey: String,
        revision: Long,
        program: JSONObject? = singleProgram(),
        calibrationState: String = "idle",
        active: Boolean = false,
        activeSource: String = "none",
        displayName: String = "",
        effectiveName: String = "Channel 1"
    ): JSONObject = JSONObject()
        .put("operation", operation)
        .put("channelKey", channelKey)
        .put("event", "dosing.status.changed")
        .put(
            "channel",
            channelDetail(
                channelKey = channelKey,
                index = channelKey.removePrefix("channel").toIntOrNull()?.minus(1) ?: 0,
                revision = revision,
                calibrated = true,
                calibrationState = calibrationState,
                program = program,
                displayName = displayName,
                effectiveName = effectiveName,
                active = active,
                activeSource = activeSource,
                remainingMs = if (active) 4_000L else 0L,
                verificationDoseComplete = false
            )
        )

    private fun booleanWeekdays() = JSONArray(
        listOf(true, true, true, true, true, true, true)
    )
}
