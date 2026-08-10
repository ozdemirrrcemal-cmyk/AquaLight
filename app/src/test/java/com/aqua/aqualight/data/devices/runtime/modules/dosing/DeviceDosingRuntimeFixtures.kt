package com.aqua.aqualight.data.devices.runtime.modules.dosing

import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions")
internal object DeviceDosingRuntimeFixtures {
    @Suppress("LongParameterList")
    fun status(
        uptimeMs: Long = 12_000L,
        schedules: JSONArray = JSONArray().put(statusSchedule()),
        channelOneDisplayName: String = "Nutrients",
        calibrationState: String = "idle",
        calibrationDurationMs: Long = if (calibrationState == "idle") 0L else 5_000L,
        measuredMl: Double = 0.0,
        pendingDoseMsPerMl: Long = -1L,
        verificationDoseStarted: Boolean = false,
        verificationDoseComplete: Boolean = false,
        verificationDoseRemainingMs: Long = 0L
    ): JSONObject = JSONObject()
        .put("supported", true)
        .put("channelCount", 2)
        .put("scheduleCount", schedules.length())
        .put("lockLoop", false)
        .put("schema", "aqualight.dosing.v1")
        .put("rootName", "dosing")
        .put("unit", "ml")
        .put("uptimeMs", uptimeMs)
        .put(
            "channels",
            JSONArray()
                .put(
                    channel(
                        0,
                        "channel1",
                        "Channel 1",
                        channelOneDisplayName,
                        manualActive = calibrationState == "running" ||
                            verificationDoseRemainingMs > 0L,
                        calibrationState = calibrationState,
                        calibrationDurationMs = calibrationDurationMs,
                        measuredMl = measuredMl,
                        pendingDoseMsPerMl = pendingDoseMsPerMl,
                        verificationDoseStarted = verificationDoseStarted,
                        verificationDoseComplete = verificationDoseComplete,
                        verificationDoseRemainingMs = verificationDoseRemainingMs
                    )
                )
                .put(channel(1, "channel2", "Channel 2", "Channel 2"))
        )
        .put("schedules", schedules)
        .put(
            "runtime",
            JSONObject()
                .put("module", "dosing")
                .put("readOnly", false)
                .put("supportsConfigApply", true)
                .put("supportsSchedules", true)
                .put("supportsChannels", true)
                .put("supportsPrime", true)
                .put("supportsManualDose", true)
                .put("supportsCalibrationWorkflow", true)
                .put("supportsCalibrationSessionState", true)
                .put("supportsReservoirRefill", true)
                .put("event", "dosing.status.changed")
        )

    @Suppress("LongParameterList")
    fun configApply(
        save: Boolean = true,
        appliedChannels: Boolean = true,
        appliedSchedules: Boolean = true,
        channelOneDisplayNameOverride: String? = "Nutrients",
        schedules: JSONArray = JSONArray().put(configSchedule()),
        doseMsPerMl: Long = 1_000L,
        lastCalibratedAt: Long = 100L
    ): JSONObject = JSONObject()
        .put("operation", "configApply")
        .put("changed", true)
        .put("saved", save)
        .put("saveRequested", save)
        .put("runtimeTransport", "websocket")
        .put("command", "dosing.config.apply")
        .put("event", "dosing.status.changed")
        .put("appliedChannels", appliedChannels)
        .put("appliedSchedules", appliedSchedules)
        .put(
            "config",
            JSONObject()
                .put(
                    "channels",
                    JSONArray()
                        .put(
                            configChannel(
                                "channel1",
                                channelOneDisplayNameOverride,
                                doseMsPerMl,
                                lastCalibratedAt
                            )
                        )
                        .put(configChannel("channel2", null, 1_000L, 100L))
                )
                .put("schedules", schedules)
        )

    fun pump(
        action: String,
        active: Boolean,
        displayName: String = "Nutrients"
    ): JSONObject {
        val operation = when (action) {
            DeviceDosingRuntimeContract.Action.PRIME_START -> "primeStart"
            DeviceDosingRuntimeContract.Action.PRIME_STOP -> "primeStop"
            DeviceDosingRuntimeContract.Action.DOSE_STOP -> "doseStop"
            else -> error("Unsupported Dosing pump fixture action: $action")
        }
        return mutationBase(operation, action)
            .put("channelKey", "channel1")
            .put("manualActive", active)
            .also { result ->
                if (!active) result.put("verificationReset", false)
            }
            .put(
                "channel",
                channel(
                    index = 0,
                    key = "channel1",
                    name = "Channel 1",
                    displayName = displayName,
                    manualActive = active
                ).put("listIndex", 0)
            )
    }

    fun doseNow(
        amountMl: Double = 10.0,
        doseMsPerMl: Long = 1_000L,
        usePendingCalibration: Boolean = false
    ): JSONObject = mutationBase("doseNow", DeviceDosingRuntimeContract.Action.DOSE_NOW)
        .put("channelKey", "channel1")
        .put("amountMl", amountMl)
        .put("durationMs", (amountMl * doseMsPerMl).toLong())
        .put("doseMsPerMl", doseMsPerMl)
        .put("usePendingCalibration", usePendingCalibration)
        .put(
            "calibrationState",
            if (usePendingCalibration) "pendingVerification" else "idle"
        )
        .put("manualActive", true)
        .put(
            "channel",
            channel(
                0,
                "channel1",
                "Channel 1",
                "Nutrients",
                manualActive = true,
                calibrationState = if (usePendingCalibration) {
                    "pendingVerification"
                } else {
                    "idle"
                },
                pendingDoseMsPerMl = if (usePendingCalibration) doseMsPerMl else -1L,
                measuredMl = if (usePendingCalibration) 5.0 else 0.0,
                verificationDoseStarted = usePendingCalibration,
                verificationDoseRemainingMs = if (usePendingCalibration) {
                    (amountMl * doseMsPerMl).toLong()
                } else {
                    0L
                }
            )
                .put("listIndex", 0)
        )

    fun calibrationStart(durationMs: Long = 5_000L): JSONObject =
        mutationBase("calibrationStart", DeviceDosingRuntimeContract.Action.CALIBRATION_START)
            .put("channelKey", "channel1")
            .put("durationMs", durationMs)
            .put("calibrationState", "running")
            .put("manualActive", true)

    fun calibrationFinish(
        measuredMl: Double = 5.0,
        durationMs: Long = 5_000L
    ): JSONObject {
        val pendingDoseMsPerMl = (durationMs.toDouble() / measuredMl).roundToLong()
        return mutationBase(
            "calibrationFinish",
            DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH
        )
            .put("channelKey", "channel1")
            .put("measuredMl", measuredMl)
            .put("durationMs", durationMs)
            .put("pendingDoseMsPerMl", pendingDoseMsPerMl)
            .put("pending", true)
            .put("calibrationState", "pendingVerification")
            .put(
                "channel",
                channel(
                    0,
                    "channel1",
                    "Channel 1",
                    "Nutrients",
                    calibrationState = "pendingVerification",
                    pendingDoseMsPerMl = pendingDoseMsPerMl,
                    measuredMl = measuredMl,
                    calibrationDurationMs = durationMs
                ).put("listIndex", 0)
            )
    }

    fun calibrationConfirm(
        doseMsPerMl: Long = 1_000L,
        lastCalibratedAt: Long = 12_100L
    ): JSONObject = mutationBase(
        "calibrationConfirm",
        DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
        saved = true
    )
        .put("channelKey", "channel1")
        .put("doseMsPerMl", doseMsPerMl)
        .put("lastCalibratedAt", lastCalibratedAt)
        .put("calibrationState", "idle")
        .put(
            "channel",
            channel(
                0,
                "channel1",
                "Channel 1",
                "Nutrients",
                doseMsPerMl = doseMsPerMl,
                lastCalibratedAt = lastCalibratedAt
            ).put("listIndex", 0)
        )

    fun calibrationCancel(): JSONObject = mutationBase(
        "calibrationCancel",
        DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL
    )
        .put("channelKey", "channel1")
        .put("discardedPendingCalibration", true)
        .put("restoredPreviousCalibration", false)
        .put("calibrationState", "idle")
        .put(
            "channel",
            channel(0, "channel1", "Channel 1", "Nutrients").put("listIndex", 0)
        )

    fun reservoirRefill(
        beforeMl: Double = 400.0,
        capacityMl: Double = 500.0
    ): JSONObject = mutationBase(
        "reservoirRefill",
        DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL
    )
        .put("channelKey", "channel1")
        .put("changed", beforeMl != capacityMl)
        .put("reservoirRemainingMlBefore", beforeMl)
        .put("reservoirRemainingMl", capacityMl)
        .put("reservoirCapacityMl", capacityMl)
        .put("persisted", true)
        .put(
            "channel",
            channel(
                0,
                "channel1",
                "Channel 1",
                "Nutrients",
                reservoirRemainingMl = capacityMl
            ).put("listIndex", 0)
        )

    fun schedulePayload(
        name: String = "Morning Nutrients",
        startTimeMs: Long = 28_800_000L,
        amountMl: Double = 10.0
    ): DeviceDosingScheduleConfig = DeviceDosingScheduleConfig(
        enabled = true,
        name = name,
        channelKey = "channel1",
        weekdays = List(DOSING_WEEKDAY_COUNT) { true },
        startTimeMs = startTimeMs,
        intervalOffMs = 60_000L,
        repeatCount = 1,
        amountMl = amountMl
    )

    fun configSchedule(
        name: String = "Morning Nutrients",
        startTimeMs: Long = 28_800_000L,
        amountMl: Double = 10.0
    ): JSONObject = JSONObject()
        .put("enabled", true)
        .put("name", name)
        .put("channelKey", "channel1")
        .put("weekdays", JSONArray(List(DOSING_WEEKDAY_COUNT) { true }))
        .put("startTimeMs", startTimeMs)
        .put("intervalOnMs", 0L)
        .put("intervalOffMs", 60_000L)
        .put("repeatCount", 1)
        .put("amountMl", amountMl)

    fun statusSchedule(
        name: String = "Morning Nutrients",
        startTimeMs: Long = 28_800_000L,
        amountMl: Double = 10.0
    ): JSONObject = JSONObject()
        .put("index", 0)
        .put("enabled", true)
        .put("runtimeEnabled", true)
        .put("name", name)
        .put("channelKey", "channel1")
        .put("bound", true)
        .put("group", -1)
        .put("weekdays", JSONArray(List(DOSING_WEEKDAY_COUNT) { true }))
        .put("startTimeMs", startTimeMs)
        .put("startTime", dosingTimeText(startTimeMs))
        .put("intervalOnMs", 0L)
        .put("intervalOn", "00:00")
        .put("intervalOffMs", 60_000L)
        .put("intervalOff", "00:01")
        .put("repeatCount", 1)
        .put("amountMl", amountMl)
        .put("pulseCountRuntime", -1)
        .put("pulseOffPending", false)
        .put("pulseRemainingMs", 0L)

    private fun mutationBase(
        operation: String,
        action: String,
        saved: Boolean = false
    ): JSONObject = JSONObject()
        .put("operation", operation)
        .put("saved", saved)
        .put("runtimeTransport", "websocket")
        .put("command", "dosing.$action")
        .put("event", "dosing.status.changed")

    @Suppress("LongParameterList")
    private fun channel(
        index: Int,
        key: String,
        name: String,
        displayName: String,
        manualActive: Boolean = false,
        doseMsPerMl: Long = 1_000L,
        lastCalibratedAt: Long = 100L,
        reservoirRemainingMl: Double = 400.0,
        calibrationState: String = "idle",
        calibrationDurationMs: Long = if (calibrationState == "idle") 0L else 5_000L,
        measuredMl: Double = 0.0,
        pendingDoseMsPerMl: Long = -1L,
        verificationDoseStarted: Boolean = false,
        verificationDoseComplete: Boolean = false,
        verificationDoseRemainingMs: Long = 0L
    ): JSONObject = JSONObject()
        .put("index", index)
        .put("key", key)
        .put("name", name)
        .put("displayName", displayName)
        .put("profileManaged", true)
        .put("regime", "Auto")
        .put("channelKind", "gpio")
        .put("gpio", 4 + index)
        .put("ledcChannel", index)
        .put("group", -1)
        .put("valueNow", if (manualActive) 1.0 else 0.0)
        .put("valueAuto", 0.0)
        .put("valueManual", if (manualActive) 1.0 else -1.0)
        .put("manualTimeoutMs", if (manualActive) 20_000L else 0L)
        .put("invert", false)
        .put("pwmResolutionBits", 10)
        .put("pwmFrequencyHz", 5_000)
        .put(
            "dosing",
            JSONObject()
                .put("unit", "ml")
                .put("doseMsPerMl", doseMsPerMl)
                .put("lastCalibratedAt", lastCalibratedAt)
                .put("calibrated", doseMsPerMl > 0L && lastCalibratedAt > 0L)
                .put(
                    "calibration",
                    JSONObject()
                        .put("state", calibrationState)
                        .put(
                            "startedAtUptimeMs",
                            if (calibrationState == "running") 10_000L else 0L
                        )
                        .put("durationMs", calibrationDurationMs)
                        .put("measuredMl", measuredMl)
                        .put("pendingDoseMsPerMl", pendingDoseMsPerMl)
                        .put("verificationDoseStarted", verificationDoseStarted)
                        .put("verificationDoseComplete", verificationDoseComplete)
                        .put("verificationDoseRemainingMs", verificationDoseRemainingMs)
                )
                .put("reservoirTrackingEnabled", true)
                .put("reservoirCapacityMl", 500.0)
                .put("reservoirRemainingMl", reservoirRemainingMl)
                .put("reservoirRemainingPercent", reservoirRemainingMl / 5.0)
        )
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", true)
                .put("hardwareCalibration", false)
                .put("dosingCalibration", true)
                .put("reservoir", true)
        )

    private fun configChannel(
        channelKey: String,
        displayNameOverride: String?,
        doseMsPerMl: Long,
        lastCalibratedAt: Long
    ): JSONObject = JSONObject()
        .put("channelKey", channelKey)
        .put("regime", "Auto")
        .put(
            "dosing",
            JSONObject()
                .put("doseMsPerMl", doseMsPerMl)
                .put("lastCalibratedAt", lastCalibratedAt)
                .put("reservoirTrackingEnabled", true)
                .put("reservoirCapacityMl", 500.0)
        )
        .also { channel ->
            displayNameOverride?.let { channel.put("displayName", it) }
        }
}
