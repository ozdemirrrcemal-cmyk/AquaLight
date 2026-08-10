package com.aqua.aqualight.data.devices.runtime.modules.dosing

import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject

/** Keeps one exact parser entry point per firmware Dosing mutation action. */
@Suppress("TooManyFunctions")
internal object DeviceDosingMutationParser {
    private val CONFIG_RESULT_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "runtimeTransport", "command",
        "event", "appliedChannels", "appliedSchedules", "config"
    )
    private val PUMP_START_RESULT_KEYS = setOf(
        "operation", "channelKey", "manualActive", "saved", "runtimeTransport", "command",
        "event", "channel"
    )
    private val PUMP_STOP_RESULT_KEYS = PUMP_START_RESULT_KEYS + "verificationReset"
    private val DOSE_NOW_KEYS = setOf(
        "operation", "channelKey", "amountMl", "durationMs", "doseMsPerMl",
        "usePendingCalibration", "calibrationState", "manualActive", "saved",
        "runtimeTransport", "command", "event", "channel"
    )
    private val CALIBRATION_START_KEYS = setOf(
        "operation", "channelKey", "durationMs", "calibrationState", "manualActive", "saved",
        "runtimeTransport", "command", "event"
    )
    private val CALIBRATION_FINISH_KEYS = setOf(
        "operation", "channelKey", "measuredMl", "durationMs", "pendingDoseMsPerMl",
        "pending", "calibrationState", "saved", "runtimeTransport", "command", "event", "channel"
    )
    private val CALIBRATION_CONFIRM_KEYS = setOf(
        "operation", "channelKey", "doseMsPerMl", "lastCalibratedAt", "calibrationState", "saved",
        "runtimeTransport", "command", "event", "channel"
    )
    private val CALIBRATION_CANCEL_KEYS = setOf(
        "operation", "channelKey", "discardedPendingCalibration",
        "restoredPreviousCalibration", "calibrationState", "saved", "runtimeTransport",
        "command", "event", "channel"
    )
    private val RESERVOIR_REFILL_KEYS = setOf(
        "operation", "channelKey", "changed", "reservoirRemainingMlBefore",
        "reservoirRemainingMl", "reservoirCapacityMl", "persisted", "saved",
        "runtimeTransport", "command", "event", "channel"
    )
    private val CONFIG_KEYS = setOf("channels", "schedules")

    fun parseConfigApply(data: JSONObject): DeviceDosingConfigApplyResult {
        data.requireDosingKeys(CONFIG_RESULT_KEYS, "dosing.config.apply result")
        return DeviceDosingConfigApplyResult(
            operation = data.requireDosingText("operation"),
            changed = data.requireDosingBoolean("changed"),
            saved = data.requireDosingBoolean("saved"),
            saveRequested = data.requireDosingBoolean("saveRequested"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event"),
            appliedChannels = data.requireDosingBoolean("appliedChannels"),
            appliedSchedules = data.requireDosingBoolean("appliedSchedules"),
            config = parseConfig(data.requireDosingObject("config"))
        ).also(::validateConfigResult)
    }

    fun parsePrimeStart(data: JSONObject): DeviceDosingPumpCommandResult =
        parsePump(
            data,
            DeviceDosingRuntimeContract.Literal.PRIME_START_OPERATION,
            DeviceDosingRuntimeContract.Action.PRIME_START,
            manualActive = true,
            includesVerificationReset = false
        )

    fun parsePrimeStop(data: JSONObject): DeviceDosingPumpCommandResult =
        parsePump(
            data,
            DeviceDosingRuntimeContract.Literal.PRIME_STOP_OPERATION,
            DeviceDosingRuntimeContract.Action.PRIME_STOP,
            manualActive = false,
            includesVerificationReset = true
        )

    fun parseDoseStop(data: JSONObject): DeviceDosingPumpCommandResult =
        parsePump(
            data,
            DeviceDosingRuntimeContract.Literal.DOSE_STOP_OPERATION,
            DeviceDosingRuntimeContract.Action.DOSE_STOP,
            manualActive = false,
            includesVerificationReset = true
        )

    fun parseDoseNow(data: JSONObject): DeviceDosingDoseNowResult {
        data.requireDosingKeys(DOSE_NOW_KEYS, "dosing.dose.now result")
        return DeviceDosingDoseNowResult(
            operation = data.requireDosingText("operation"),
            channelKey = data.requireDosingText("channelKey"),
            amountMl = data.requireDosingDouble(
                "amountMl",
                Double.MIN_VALUE,
                DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML
            ),
            durationMs = data.requireDosingLong(
                "durationMs",
                DeviceDosingRuntimeContract.Limit.MIN_MANUAL_DOSE_DURATION_MS,
                DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_DURATION_MS
            ),
            doseMsPerMl = data.requireDosingLong(
                "doseMsPerMl",
                1L,
                DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML
            ),
            usePendingCalibration = data.requireDosingBoolean("usePendingCalibration"),
            calibrationState = DeviceDosingCalibrationStateParser.parse(
                data.requireDosingText("calibrationState")
            ),
            manualActive = data.requireDosingBoolean("manualActive"),
            saved = data.requireDosingBoolean("saved"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event"),
            channel = DeviceDosingChannelParser.parseMutation(
                data.requireDosingObject("channel")
            )
        ).also(::validateDoseNow)
    }

    fun parseCalibrationStart(data: JSONObject): DeviceDosingCalibrationStartResult {
        data.requireDosingKeys(CALIBRATION_START_KEYS, "dosing.calibration.start result")
        return DeviceDosingCalibrationStartResult(
            operation = data.requireDosingText("operation"),
            channelKey = data.requireDosingText("channelKey"),
            durationMs = data.requireDosingLong(
                "durationMs",
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS,
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS
            ),
            calibrationState = DeviceDosingCalibrationStateParser.parse(
                data.requireDosingText("calibrationState")
            ),
            manualActive = data.requireDosingBoolean("manualActive"),
            saved = data.requireDosingBoolean("saved"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event")
        ).also { result ->
            validateCommon(
                result.operation,
                DeviceDosingRuntimeContract.Literal.CALIBRATION_START_OPERATION,
                result.saved,
                false,
                result.runtimeTransport,
                result.command,
                DeviceDosingRuntimeContract.Action.CALIBRATION_START,
                result.event
            )
            require(result.manualActive)
            require(result.calibrationState == DeviceDosingCalibrationState.RUNNING)
        }
    }

    fun parseCalibrationFinish(data: JSONObject): DeviceDosingCalibrationFinishResult {
        data.requireDosingKeys(CALIBRATION_FINISH_KEYS, "dosing.calibration.finish result")
        return DeviceDosingCalibrationFinishResult(
            operation = data.requireDosingText("operation"),
            channelKey = data.requireDosingText("channelKey"),
            measuredMl = data.requireDosingDouble(
                "measuredMl",
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML,
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML
            ),
            durationMs = data.requireDosingLong(
                "durationMs",
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS,
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS
            ),
            pendingDoseMsPerMl = data.requireDosingLong(
                "pendingDoseMsPerMl",
                1L,
                DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML
            ),
            pending = data.requireDosingBoolean("pending"),
            calibrationState = DeviceDosingCalibrationStateParser.parse(
                data.requireDosingText("calibrationState")
            ),
            saved = data.requireDosingBoolean("saved"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event"),
            channel = DeviceDosingChannelParser.parseMutation(
                data.requireDosingObject("channel")
            )
        ).also(::validateCalibrationFinish)
    }

    fun parseCalibrationConfirm(data: JSONObject): DeviceDosingCalibrationConfirmResult {
        data.requireDosingKeys(CALIBRATION_CONFIRM_KEYS, "dosing.calibration.confirm result")
        return DeviceDosingCalibrationConfirmResult(
            operation = data.requireDosingText("operation"),
            channelKey = data.requireDosingText("channelKey"),
            doseMsPerMl = data.requireDosingLong(
                "doseMsPerMl",
                1L,
                DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML
            ),
            lastCalibratedAt = data.requireDosingLong(
                "lastCalibratedAt",
                1L,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            calibrationState = DeviceDosingCalibrationStateParser.parse(
                data.requireDosingText("calibrationState")
            ),
            saved = data.requireDosingBoolean("saved"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event"),
            channel = DeviceDosingChannelParser.parseMutation(
                data.requireDosingObject("channel")
            )
        ).also(::validateCalibrationConfirm)
    }

    fun parseCalibrationCancel(data: JSONObject): DeviceDosingCalibrationCancelResult {
        data.requireDosingKeys(CALIBRATION_CANCEL_KEYS, "dosing.calibration.cancel result")
        return DeviceDosingCalibrationCancelResult(
            operation = data.requireDosingText("operation"),
            channelKey = data.requireDosingText("channelKey"),
            restoredPreviousCalibration = data.requireDosingBoolean(
                "restoredPreviousCalibration"
            ),
            discardedPendingCalibration = data.requireDosingBoolean(
                "discardedPendingCalibration"
            ),
            calibrationState = DeviceDosingCalibrationStateParser.parse(
                data.requireDosingText("calibrationState")
            ),
            saved = data.requireDosingBoolean("saved"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event"),
            channel = DeviceDosingChannelParser.parseMutation(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateChannelMutation(
                result.operation,
                DeviceDosingRuntimeContract.Literal.CALIBRATION_CANCEL_OPERATION,
                result.channelKey,
                result.saved,
                false,
                result.runtimeTransport,
                result.command,
                DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL,
                result.event,
                result.channel
            )
            require(!result.restoredPreviousCalibration)
            require(result.calibrationState == DeviceDosingCalibrationState.IDLE)
            require(
                result.channel.channel.dosing.calibration.state ==
                    DeviceDosingCalibrationState.IDLE
            )
        }
    }

    fun parseReservoirRefill(data: JSONObject): DeviceDosingReservoirRefillResult {
        data.requireDosingKeys(RESERVOIR_REFILL_KEYS, "dosing.reservoir.refill result")
        return DeviceDosingReservoirRefillResult(
            operation = data.requireDosingText("operation"),
            channelKey = data.requireDosingText("channelKey"),
            changed = data.requireDosingBoolean("changed"),
            reservoirRemainingMlBefore = data.requireDosingDouble(
                "reservoirRemainingMlBefore",
                DOSING_NON_NEGATIVE_LONG.toDouble()
            ),
            reservoirRemainingMl = data.requireDosingDouble(
                "reservoirRemainingMl",
                DOSING_NON_NEGATIVE_LONG.toDouble()
            ),
            reservoirCapacityMl = data.requireDosingDouble(
                "reservoirCapacityMl",
                DOSING_NON_NEGATIVE_LONG.toDouble()
            ),
            persisted = data.requireDosingBoolean("persisted"),
            saved = data.requireDosingBoolean("saved"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event"),
            channel = DeviceDosingChannelParser.parseMutation(
                data.requireDosingObject("channel")
            )
        ).also(::validateReservoirRefill)
    }

    private fun parsePump(
        data: JSONObject,
        expectedOperation: String,
        action: String,
        manualActive: Boolean,
        includesVerificationReset: Boolean
    ): DeviceDosingPumpCommandResult {
        data.requireDosingKeys(
            if (includesVerificationReset) PUMP_STOP_RESULT_KEYS else PUMP_START_RESULT_KEYS,
            "dosing.$action result"
        )
        return DeviceDosingPumpCommandResult(
            operation = data.requireDosingText("operation"),
            channelKey = data.requireDosingText("channelKey"),
            manualActive = data.requireDosingBoolean("manualActive"),
            verificationReset = if (includesVerificationReset) {
                data.requireDosingBoolean("verificationReset")
            } else {
                false
            },
            saved = data.requireDosingBoolean("saved"),
            runtimeTransport = data.requireDosingText("runtimeTransport"),
            command = data.requireDosingText("command"),
            event = data.requireDosingText("event"),
            channel = DeviceDosingChannelParser.parseMutation(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateChannelMutation(
                result.operation,
                expectedOperation,
                result.channelKey,
                result.saved,
                false,
                result.runtimeTransport,
                result.command,
                action,
                result.event,
                result.channel
            )
            require(result.manualActive == manualActive)
            require((result.channel.channel.valueManual >= DOSING_NORMALIZED_MIN) == manualActive)
        }
    }

    private fun parseConfig(data: JSONObject): DeviceDosingConfigSnapshot {
        data.requireDosingKeys(CONFIG_KEYS, "Dosing config snapshot")
        val channels = parseConfigChannels(data.requireDosingArray("channels"))
        val schedules = parseConfigSchedules(data.requireDosingArray("schedules"))
        return DeviceDosingConfigSnapshot(channels, schedules).also(::validateConfig)
    }

    private fun parseConfigChannels(data: JSONArray): List<DeviceDosingChannelConfigSnapshot> {
        require(data.length() <= DeviceDosingRuntimeContract.Limit.MAX_CHANNELS)
        return List(data.length()) { index ->
            DeviceDosingConfigChannelParser.parse(data.requireDosingObject(index), index)
        }
    }

    private fun parseConfigSchedules(data: JSONArray): List<DeviceDosingScheduleConfigSnapshot> {
        require(data.length() <= DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES)
        return List(data.length()) { index ->
            DeviceDosingConfigScheduleParser.parse(data.requireDosingObject(index), index)
        }
    }

    private fun validateConfigResult(result: DeviceDosingConfigApplyResult) {
        validateCommon(
            result.operation,
            DeviceDosingRuntimeContract.Literal.CONFIG_APPLY_OPERATION,
            result.saved,
            result.saveRequested,
            result.runtimeTransport,
            result.command,
            DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
            result.event
        )
        require(result.changed)
    }

    private fun validateDoseNow(result: DeviceDosingDoseNowResult) {
        validateChannelMutation(
            result.operation,
            DeviceDosingRuntimeContract.Literal.DOSE_NOW_OPERATION,
            result.channelKey,
            result.saved,
            false,
            result.runtimeTransport,
            result.command,
            DeviceDosingRuntimeContract.Action.DOSE_NOW,
            result.event,
            result.channel
        )
        require(result.manualActive)
        require(
            result.calibrationState == if (result.usePendingCalibration) {
                DeviceDosingCalibrationState.PENDING_VERIFICATION
            } else {
                DeviceDosingCalibrationState.IDLE
            }
        )
        require(result.channel.channel.valueManual >= DOSING_NORMALIZED_MIN)
        if (result.usePendingCalibration) {
            require(
                result.channel.channel.dosing.calibration.pendingDoseMsPerMl ==
                    result.doseMsPerMl
            )
        } else {
            require(result.channel.channel.dosing.doseMsPerMl == result.doseMsPerMl)
        }
        require(
            result.durationMs == (result.amountMl * result.doseMsPerMl.toDouble()).roundToLong()
        )
    }

    private fun validateCalibrationFinish(result: DeviceDosingCalibrationFinishResult) {
        validateChannelMutation(
            result.operation,
            DeviceDosingRuntimeContract.Literal.CALIBRATION_FINISH_OPERATION,
            result.channelKey,
            result.saved,
            false,
            result.runtimeTransport,
            result.command,
            DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH,
            result.event,
            result.channel
        )
        require(result.pending)
        require(result.calibrationState == DeviceDosingCalibrationState.PENDING_VERIFICATION)
        require(
            result.pendingDoseMsPerMl ==
                (result.durationMs.toDouble() / result.measuredMl).roundToLong()
        )
        require(
            result.channel.channel.dosing.calibration.pendingDoseMsPerMl ==
                result.pendingDoseMsPerMl
        )
        require(
            result.channel.channel.dosing.calibration.state ==
                DeviceDosingCalibrationState.PENDING_VERIFICATION
        )
        require(result.channel.channel.valueManual < DOSING_NORMALIZED_MIN)
    }

    private fun validateCalibrationConfirm(result: DeviceDosingCalibrationConfirmResult) {
        validateChannelMutation(
            result.operation,
            DeviceDosingRuntimeContract.Literal.CALIBRATION_CONFIRM_OPERATION,
            result.channelKey,
            result.saved,
            true,
            result.runtimeTransport,
            result.command,
            DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
            result.event,
            result.channel
        )
        require(result.channel.channel.dosing.doseMsPerMl == result.doseMsPerMl)
        require(result.channel.channel.dosing.lastCalibratedAt == result.lastCalibratedAt)
        require(result.channel.channel.dosing.calibrated)
        require(result.calibrationState == DeviceDosingCalibrationState.IDLE)
        require(
            result.channel.channel.dosing.calibration.state ==
                DeviceDosingCalibrationState.IDLE
        )
    }

    private fun validateReservoirRefill(result: DeviceDosingReservoirRefillResult) {
        validateChannelMutation(
            result.operation,
            DeviceDosingRuntimeContract.Literal.RESERVOIR_REFILL_OPERATION,
            result.channelKey,
            result.saved,
            false,
            result.runtimeTransport,
            result.command,
            DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL,
            result.event,
            result.channel
        )
        require(
            result.changed ==
                (result.reservoirRemainingMlBefore != result.reservoirRemainingMl)
        )
        require(dosingValuesEquivalent(result.reservoirCapacityMl, result.reservoirRemainingMl))
        require(
            dosingValuesEquivalent(
                result.channel.channel.dosing.reservoirRemainingMl,
                result.reservoirRemainingMl
            )
        )
    }

    @Suppress("LongParameterList")
    private fun validateChannelMutation(
        operation: String,
        expectedOperation: String,
        channelKey: String,
        saved: Boolean,
        expectedSaved: Boolean,
        runtimeTransport: String,
        command: String,
        action: String,
        event: String,
        channel: DeviceDosingChannelStatusSnapshot
    ) {
        validateCommon(
            operation,
            expectedOperation,
            saved,
            expectedSaved,
            runtimeTransport,
            command,
            action,
            event
        )
        require(channelKey == channel.channel.key)
    }

    @Suppress("LongParameterList")
    private fun validateCommon(
        operation: String,
        expectedOperation: String,
        saved: Boolean,
        expectedSaved: Boolean,
        runtimeTransport: String,
        command: String,
        action: String,
        event: String
    ) {
        require(operation == expectedOperation)
        require(saved == expectedSaved)
        require(runtimeTransport == DeviceDosingRuntimeContract.Literal.RUNTIME_TRANSPORT)
        require(command == "${DeviceDosingRuntimeContract.MODULE}.$action")
        require(event == DeviceDosingRuntimeContract.STATUS_EVENT)
    }

    private fun validateConfig(config: DeviceDosingConfigSnapshot) {
        require(config.channels.isNotEmpty())
        require(
            config.channels.map(DeviceDosingChannelConfigSnapshot::listIndex) ==
                config.channels.indices.toList()
        )
        require(
            config.schedules.map(DeviceDosingScheduleConfigSnapshot::listIndex) ==
                config.schedules.indices.toList()
        )
        require(
            config.channels.map(DeviceDosingChannelConfigSnapshot::channelKey).distinct().size ==
                config.channels.size
        )
        val channelKeys = config.channels
            .mapTo(linkedSetOf(), DeviceDosingChannelConfigSnapshot::channelKey)
        require(config.schedules.all { schedule -> schedule.channelKey in channelKeys })
    }
}
