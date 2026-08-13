package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions")
internal object DeviceDosingRuntimeFixtures {
    fun globalStatus(
        uptimeMs: Long = 12_000L,
        channelOneRevision: Long = 7L,
        channelOneName: String = "Nutrients"
    ): JSONObject = commonStatus(uptimeMs)
        .put("scheduling", scheduling())
        .put(
            "channels",
            JSONArray()
                .put(globalChannel("channel1", channelOneName, channelOneRevision, programEnabled = true))
                .put(globalChannel("channel2", "Channel 2", 2L, programEnabled = false))
        )
        .put("runtime", runtimeCapabilities())
        .put("resources", resources())

    @Suppress("LongParameterList")
    fun channelStatus(
        uptimeMs: Long = 12_000L,
        revision: Long = 7L,
        displayName: String = "Nutrients",
        program: JSONObject? = singleProgram(),
        calibrationState: String = DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_IDLE,
        calibrationDurationMs: Long = 0L,
        measuredMl: Double = 0.0,
        pendingDoseMsPerMl: Long = 0L,
        verificationDoseStarted: Boolean = false,
        verificationDoseComplete: Boolean = false,
        active: Boolean = false,
        activeSource: String = "none",
        targetAmountMl: Double = 0.0,
        remainingMs: Long = 0L,
        reservoirRemainingMl: Double = 400.0,
        accountingCertain: Boolean = true,
        lastCalibratedAt: Long = 100L,
        doseMsPerMl: Long = 1_000L
    ): JSONObject = commonStatus(uptimeMs)
        .put("scheduling", scheduling())
        .put(
            "channel",
            channelDetail(
                revision = revision,
                displayName = displayName,
                program = program,
                calibrationState = calibrationState,
                calibrationDurationMs = calibrationDurationMs,
                measuredMl = measuredMl,
                pendingDoseMsPerMl = pendingDoseMsPerMl,
                verificationDoseStarted = verificationDoseStarted,
                verificationDoseComplete = verificationDoseComplete,
                active = active,
                activeSource = activeSource,
                targetAmountMl = targetAmountMl,
                remainingMs = remainingMs,
                reservoirRemainingMl = reservoirRemainingMl,
                accountingCertain = accountingCertain,
                lastCalibratedAt = lastCalibratedAt,
                doseMsPerMl = doseMsPerMl
            )
        )

    fun statusChange(
        revision: Long = 8L,
        kind: String = "programApplied",
        source: String = "scheduled"
    ): JSONObject = JSONObject()
        .put("schema", DeviceDosingRuntimeContract.SCHEMA)
        .put("schemaVersion", DeviceDosingRuntimeContract.SCHEMA_VERSION)
        .put("channelKey", "channel1")
        .put("revision", revision)
        .put("storageHealthy", true)
        .put("change", runtimeEvent(valid = true, sequence = revision, kind = kind, source = source))

    fun channelConfigApply(
        revision: Long = 8L,
        displayName: String = "Macro Pump"
    ): JSONObject = persistedMutation(
        operation = DeviceDosingRuntimeContract.Literal.CHANNEL_CONFIG_APPLY_OPERATION,
        channel = channelDetail(revision = revision, displayName = displayName)
    )

    fun programApply(
        revision: Long = 8L,
        program: JSONObject = singleProgram()
    ): JSONObject = persistedMutation(
        operation = DeviceDosingRuntimeContract.Literal.PROGRAM_APPLY_OPERATION,
        channel = channelDetail(revision = revision, program = program)
    )

    fun channelReset(revision: Long = 8L): JSONObject = persistedMutation(
        operation = DeviceDosingRuntimeContract.Literal.CHANNEL_RESET_OPERATION,
        channel = channelDetail(
            revision = revision,
            program = null,
            runtimeEnabled = false,
            runtimeReason = "programDisabled"
        )
    )

    fun pump(action: String, active: Boolean): JSONObject {
        val operation = when (action) {
            DeviceDosingRuntimeContract.Action.PRIME_START ->
                DeviceDosingRuntimeContract.Literal.PRIME_START_OPERATION
            DeviceDosingRuntimeContract.Action.PRIME_STOP ->
                DeviceDosingRuntimeContract.Literal.PRIME_STOP_OPERATION
            DeviceDosingRuntimeContract.Action.DOSE_STOP ->
                DeviceDosingRuntimeContract.Literal.DOSE_STOP_OPERATION
            else -> error("Unsupported Dosing pump fixture action: $action")
        }
        return JSONObject()
            .put("operation", operation)
            .put("channelKey", "channel1")
            .also { result ->
                if (active) {
                    result.put("durationMs", 5_000L)
                    result.put("doseMsPerMl", 1_000L)
                }
            }
            .put("manualActive", active)
            .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
            .put(
                "channel",
                channelDetail(
                    active = active,
                    activeSource = if (active) "prime" else "none",
                    remainingMs = if (active) 5_000L else 0L
                )
            )
    }

    fun doseNow(
        amountMl: Double = 10.0,
        doseMsPerMl: Long = 1_000L,
        usePendingCalibration: Boolean = false
    ): JSONObject {
        val durationMs = (amountMl * doseMsPerMl).toLong()
        return JSONObject()
            .put("operation", DeviceDosingRuntimeContract.Literal.DOSE_NOW_OPERATION)
            .put("channelKey", "channel1")
            .put("amountMl", amountMl)
            .put("durationMs", durationMs)
            .put("doseMsPerMl", doseMsPerMl)
            .put("usePendingCalibration", usePendingCalibration)
            .put("manualActive", true)
            .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
            .put(
                "channel",
                channelDetail(
                    calibrationState = if (usePendingCalibration) {
                        DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_PENDING_VERIFICATION
                    } else {
                        DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_IDLE
                    },
                    calibrationDurationMs = if (usePendingCalibration) 5_000L else 0L,
                    measuredMl = if (usePendingCalibration) 5.0 else 0.0,
                    pendingDoseMsPerMl = if (usePendingCalibration) doseMsPerMl else 0L,
                    verificationDoseStarted = usePendingCalibration,
                    active = true,
                    activeSource = if (usePendingCalibration) "verification" else "manual",
                    targetAmountMl = amountMl,
                    remainingMs = durationMs
                )
            )
    }

    fun calibrationStart(durationMs: Long = 5_000L): JSONObject = JSONObject()
        .put("operation", DeviceDosingRuntimeContract.Literal.CALIBRATION_START_OPERATION)
        .put("channelKey", "channel1")
        .put("durationMs", durationMs)
        .put("calibrationState", DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_RUNNING)
        .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
        .put(
            "channel",
            channelDetail(
                calibrationState = DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_RUNNING,
                calibrationDurationMs = durationMs,
                active = true,
                activeSource = "calibration",
                remainingMs = durationMs
            )
        )

    fun calibrationFinish(
        measuredMl: Double = 5.0,
        durationMs: Long = 5_000L
    ): JSONObject {
        val pendingDoseMsPerMl = (durationMs / measuredMl).toLong()
        return JSONObject()
            .put("operation", DeviceDosingRuntimeContract.Literal.CALIBRATION_FINISH_OPERATION)
            .put("channelKey", "channel1")
            .put("measuredMl", measuredMl)
            .put("durationMs", durationMs)
            .put("pendingDoseMsPerMl", pendingDoseMsPerMl)
            .put(
                "calibrationState",
                DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_PENDING_VERIFICATION
            )
            .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
            .put(
                "channel",
                channelDetail(
                    calibrationState =
                        DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_PENDING_VERIFICATION,
                    calibrationDurationMs = durationMs,
                    measuredMl = measuredMl,
                    pendingDoseMsPerMl = pendingDoseMsPerMl
                )
            )
    }

    fun calibrationConfirm(
        revision: Long = 8L,
        doseMsPerMl: Long = 1_250L,
        lastCalibratedAt: Long = 12_100L
    ): JSONObject = JSONObject()
        .put("operation", DeviceDosingRuntimeContract.Literal.CALIBRATION_CONFIRM_OPERATION)
        .put("channelKey", "channel1")
        .put("revision", revision)
        .put("doseMsPerMl", doseMsPerMl)
        .put("lastCalibratedAt", lastCalibratedAt)
        .put("calibrationState", DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_IDLE)
        .put("saved", true)
        .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
        .put(
            "channel",
            channelDetail(
                revision = revision,
                doseMsPerMl = doseMsPerMl,
                lastCalibratedAt = lastCalibratedAt
            )
        )

    fun calibrationCancel(): JSONObject = JSONObject()
        .put("operation", DeviceDosingRuntimeContract.Literal.CALIBRATION_CANCEL_OPERATION)
        .put("channelKey", "channel1")
        .put("discardedPendingCalibration", true)
        .put("calibrationState", DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_IDLE)
        .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
        .put("channel", channelDetail())

    fun reservoirRefill(
        beforeMl: Double = 400.0,
        remainingMl: Double = 500.0
    ): JSONObject = JSONObject()
        .put("operation", DeviceDosingRuntimeContract.Literal.RESERVOIR_REFILL_OPERATION)
        .put("channelKey", "channel1")
        .put("reservoirRemainingMlBefore", beforeMl)
        .put("reservoirRemainingMl", remainingMl)
        .put("persisted", true)
        .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
        .put("channel", channelDetail(reservoirRemainingMl = remainingMl))

    fun singleProgram(
        enabled: Boolean = true,
        dailyDoseMl: Double = 10.0,
        startTimeMs: Long = 28_800_000L,
        missedDoseRecoveryEnabled: Boolean = false
    ): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("weekdays", JSONArray(List(7) { true }))
        .put("mode", "single")
        .put("missedDoseRecoveryEnabled", missedDoseRecoveryEnabled)
        .put("config", JSONObject().put("dailyDoseMl", dailyDoseMl).put("startTimeMs", startTimeMs))

    fun timerProgram(): JSONObject = JSONObject()
        .put("enabled", true)
        .put("weekdays", JSONArray(List(7) { true }))
        .put("mode", "timer")
        .put("missedDoseRecoveryEnabled", true)
        .put(
            "config",
            JSONObject().put(
                "events",
                JSONArray()
                    .put(JSONObject().put("timeMs", 28_800_000L).put("amountMl", 2.0))
                    .put(JSONObject().put("timeMs", 72_000_000L).put("amountMl", 3.0))
            )
        )

    private fun commonStatus(uptimeMs: Long): JSONObject = JSONObject()
        .put("supported", true)
        .put("schema", DeviceDosingRuntimeContract.SCHEMA)
        .put("schemaVersion", DeviceDosingRuntimeContract.SCHEMA_VERSION)
        .put("unit", DeviceDosingRuntimeContract.UNIT_ML)
        .put("channelCount", 2)
        .put("uptimeMs", uptimeMs)
        .put("bootReady", true)
        .put("storageHealthy", true)
        .put("storageIssue", "")

    private fun scheduling(): JSONObject = JSONObject()
        .put("contract", DeviceDosingRuntimeContract.SCHEMA)
        .put("schemaVersion", DeviceDosingRuntimeContract.SCHEMA_VERSION)
        .put("amountResolutionMl", 0.001)
        .put("maxEventsPerChannel", 24)
        .put("maxCustomPeriodsPerChannel", 8)
        .put("scheduledDispatchGraceMs", 2_000L)
        .put("missedDoseRecoveryWindowMs", 900_000L)
        .put("minPumpRunDurationMs", 50L)
        .put("maxPumpRunDurationMs", 3_600_000L)
        .put("maxManualDoseMl", 1_000.0)
        .put("supportsWeekdayRecurrence", true)
        .put("supportsMissedDoseRecovery", true)
        .put("supportsChannelReset", true)
        .put("supportsDailyDeliveredUsage", true)
        .put("supportedModes", JSONArray(listOf("single", "hourly24", "customPeriods", "timer")))
        .put(
            "weekdayOrder",
            JSONArray(listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"))
        )
        .put(
            "effectiveScheduledDose",
            JSONObject().put("available", true).put("minDoseMl", 0.05).put("maxDoseMl", 100.0)
        )

    private fun globalChannel(
        key: String,
        name: String,
        revision: Long,
        programEnabled: Boolean
    ): JSONObject = JSONObject()
        .put("channelKey", key)
        .put("effectiveName", name)
        .put("revision", revision)
        .put("runtimeEnabled", programEnabled)
        .put("runtimeReason", if (programEnabled) "none" else "programDisabled")
        .put("programEnabled", programEnabled)
        .put("programMode", if (programEnabled) "single" else "none")
        .put("deliveryAccountingCertain", true)
        .put("usageToday", usageToday())
        .put(
            "reservoir",
            JSONObject().put("trackingEnabled", true).put("remainingMl", 400.0).put("accountingCertain", true)
        )
        .put("active", false)

    @Suppress("LongParameterList")
    private fun channelDetail(
        revision: Long = 7L,
        displayName: String = "Nutrients",
        program: JSONObject? = singleProgram(),
        runtimeEnabled: Boolean = program != null,
        runtimeReason: String = if (runtimeEnabled) "none" else "programDisabled",
        calibrationState: String = DeviceDosingRuntimeContract.Literal.CALIBRATION_STATE_IDLE,
        calibrationDurationMs: Long = 0L,
        measuredMl: Double = 0.0,
        pendingDoseMsPerMl: Long = 0L,
        verificationDoseStarted: Boolean = false,
        verificationDoseComplete: Boolean = false,
        active: Boolean = false,
        activeSource: String = "none",
        targetAmountMl: Double = 0.0,
        remainingMs: Long = 0L,
        reservoirRemainingMl: Double = 400.0,
        accountingCertain: Boolean = true,
        lastCalibratedAt: Long = 100L,
        doseMsPerMl: Long = 1_000L
    ): JSONObject = JSONObject()
        .put("channelKey", "channel1")
        .put("revision", revision)
        .put("runtimeEnabled", runtimeEnabled)
        .put("runtimeReason", runtimeReason)
        .put("program", program ?: JSONObject.NULL)
        .put("usageToday", usageToday())
        .put("index", 0)
        .put("defaultName", "Channel 1")
        .put("displayName", displayName)
        .put("effectiveName", displayName.ifBlank { "Channel 1" })
        .put("profileManaged", true)
        .put("deliveryAccountingCertain", accountingCertain)
        .put(
            "hardware",
            JSONObject()
                .put("channelType", "gpio")
                .put("gpio", 4)
                .put("ledcChannel", 0)
                .put("resolutionBits", 10)
                .put("frequencyHz", 5_000)
        )
        .put(
            "calibration",
            JSONObject()
                .put("confirmed", doseMsPerMl > 0L && lastCalibratedAt > 0L)
                .put("doseMsPerMl", doseMsPerMl)
                .put("lastCalibratedAt", lastCalibratedAt)
                .put("state", calibrationState)
                .put("durationMs", calibrationDurationMs)
                .put("measuredMl", measuredMl)
                .put("pendingDoseMsPerMl", pendingDoseMsPerMl)
                .put("verificationDoseStarted", verificationDoseStarted)
                .put("verificationDoseComplete", verificationDoseComplete)
        )
        .put(
            "reservoir",
            JSONObject()
                .put("trackingEnabled", true)
                .put("capacityMl", 500.0)
                .put("remainingMl", reservoirRemainingMl)
                .put("accountingCertain", accountingCertain)
                .put("remainingPercent", reservoirRemainingMl / 5.0)
        )
        .put(
            "activeRun",
            JSONObject()
                .put("active", active)
                .put("source", activeSource)
                .put("targetAmountMl", targetAmountMl)
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

    private fun usageToday(): JSONObject = JSONObject()
        .put("dateValid", true)
        .put("localDate", "2026-08-13")
        .put("scheduledDeliveredMl", 3.0)
        .put("manualDeliveredMl", 1.0)
        .put("totalDeliveredMl", 4.0)

    private fun runtimeEvent(
        valid: Boolean = false,
        sequence: Long = 0L,
        kind: String = "none",
        source: String = "none"
    ): JSONObject = JSONObject()
        .put("valid", valid)
        .put("sequence", sequence)
        .put("occurredAtMs", if (valid) 11_000L else 0L)
        .put("kind", kind)
        .put("reason", "none")
        .put("source", source)

    private fun runtimeCapabilities(): JSONObject = JSONObject()
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

    private fun resources(): JSONObject = JSONObject()
        .put("freeHeapBytes", 100_000L)
        .put("minimumFreeHeapBytes", 80_000L)
        .put("largestFreeBlockBytes", 60_000L)
        .put("taskStackHighWaterBytes", 4_096L)
        .put("checkpointWritesThisBoot", 5L)
        .put("canonicalConfigBytes", 1_024L)
        .put("programServiceBytes", 2_048L)
        .put("runtimeSnapshotBytes", 1_024L)
        .put("statusSnapshotBytes", 2_048L)

    private fun persistedMutation(operation: String, channel: JSONObject): JSONObject = JSONObject()
        .put("operation", operation)
        .put("channelKey", "channel1")
        .put("saved", true)
        .put("event", DeviceDosingRuntimeContract.STATUS_EVENT)
        .put("channel", channel)
}
