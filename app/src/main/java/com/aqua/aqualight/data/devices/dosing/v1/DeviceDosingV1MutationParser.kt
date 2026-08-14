package com.aqua.aqualight.data.devices.dosing.v1

import org.json.JSONObject

@Suppress("LongMethod", "TooManyFunctions")
object DeviceDosingV1MutationParser {
    fun parseConfigApply(data: JSONObject): DeviceDosingV1SavedMutationResult =
        parseSaved(data, DeviceDosingV1Contract.Literal.CHANNEL_CONFIG_APPLY)

    fun parseProgramApply(data: JSONObject): DeviceDosingV1SavedMutationResult =
        parseSaved(data, DeviceDosingV1Contract.Literal.PROGRAM_APPLY)

    fun parseChannelReset(data: JSONObject): DeviceDosingV1SavedMutationResult =
        parseSaved(data, DeviceDosingV1Contract.Literal.CHANNEL_RESET)

    fun parsePrimeStart(data: JSONObject): DeviceDosingV1PrimeStartResult {
        data.requireDosingKeys(PRIME_START_KEYS, "prime.start result")
        return DeviceDosingV1PrimeStartResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            durationMillis = data.requireDosingLong("durationMs", minimum = 1L),
            doseMillisPerMilliliter = data.requireDosingDouble("doseMsPerMl", minimum = 0.0),
            manualActive = data.requireDosingBoolean("manualActive"),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = DeviceDosingV1Contract.Literal.PRIME_START,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.manualActive)
        }
    }

    fun parsePrimeStop(data: JSONObject): DeviceDosingV1SimpleStopResult =
        parseSimpleStop(data, DeviceDosingV1Contract.Literal.PRIME_STOP)

    fun parseDoseStop(data: JSONObject): DeviceDosingV1SimpleStopResult =
        parseSimpleStop(data, DeviceDosingV1Contract.Literal.DOSE_STOP)

    fun parseDoseNow(data: JSONObject): DeviceDosingV1DoseNowResult {
        data.requireDosingKeys(DOSE_NOW_KEYS, "dose.now result")
        return DeviceDosingV1DoseNowResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            amountMilliliters = data.requireDosingDouble("amountMl", minimum = 0.0),
            durationMillis = data.requireDosingLong("durationMs", minimum = 1L),
            doseMillisPerMilliliter = data.requireDosingDouble("doseMsPerMl", minimum = 0.0),
            usePendingCalibration = data.requireDosingBoolean("usePendingCalibration"),
            manualActive = data.requireDosingBoolean("manualActive"),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = DeviceDosingV1Contract.Literal.DOSE_NOW,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.manualActive)
        }
    }

    fun parseCalibrationStart(data: JSONObject): DeviceDosingV1CalibrationStartResult {
        data.requireDosingKeys(CALIBRATION_START_KEYS, "calibration.start result")
        return DeviceDosingV1CalibrationStartResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            durationMillis = data.requireDosingLong("durationMs", minimum = 1L),
            calibrationState = dosingWireValue(data.requireDosingString("calibrationState")),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = DeviceDosingV1Contract.Literal.CALIBRATION_START,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.calibrationState.raw == "running")
        }
    }

    fun parseCalibrationFinish(data: JSONObject): DeviceDosingV1CalibrationFinishResult {
        data.requireDosingKeys(CALIBRATION_FINISH_KEYS, "calibration.finish result")
        return DeviceDosingV1CalibrationFinishResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            measuredMilliliters = data.requireDosingDouble("measuredMl", minimum = 0.0),
            durationMillis = data.requireDosingLong("durationMs", minimum = 1L),
            pendingDoseMillisPerMilliliter =
                data.requireDosingDouble("pendingDoseMsPerMl", minimum = 0.0),
            calibrationState = dosingWireValue(data.requireDosingString("calibrationState")),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = DeviceDosingV1Contract.Literal.CALIBRATION_FINISH,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.calibrationState.raw == "pendingVerification")
        }
    }

    fun parseCalibrationConfirm(data: JSONObject): DeviceDosingV1CalibrationConfirmResult {
        data.requireDosingKeys(CALIBRATION_CONFIRM_KEYS, "calibration.confirm result")
        return DeviceDosingV1CalibrationConfirmResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            doseMillisPerMilliliter = data.requireDosingDouble("doseMsPerMl", minimum = 0.0),
            lastCalibratedAt = data.requireDosingLong("lastCalibratedAt", minimum = 0L),
            calibrationState = dosingWireValue(data.requireDosingString("calibrationState")),
            saved = data.requireDosingBoolean("saved"),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = DeviceDosingV1Contract.Literal.CALIBRATION_CONFIRM,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.revision == result.channel.revision)
            require(result.calibrationState.raw == "idle")
            require(result.saved)
        }
    }

    fun parseCalibrationCancel(data: JSONObject): DeviceDosingV1CalibrationCancelResult {
        data.requireDosingKeys(CALIBRATION_CANCEL_KEYS, "calibration.cancel result")
        return DeviceDosingV1CalibrationCancelResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            discardedPendingCalibration =
                data.requireDosingBoolean("discardedPendingCalibration"),
            calibrationState = dosingWireValue(data.requireDosingString("calibrationState")),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = DeviceDosingV1Contract.Literal.CALIBRATION_CANCEL,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.calibrationState.raw == "idle")
        }
    }

    fun parseReservoirRefill(data: JSONObject): DeviceDosingV1ReservoirRefillResult {
        data.requireDosingKeys(RESERVOIR_REFILL_KEYS, "reservoir.refill result")
        return DeviceDosingV1ReservoirRefillResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            reservoirRemainingMillilitersBefore =
                data.requireDosingDouble("reservoirRemainingMlBefore", minimum = 0.0),
            reservoirRemainingMilliliters =
                data.requireDosingDouble("reservoirRemainingMl", minimum = 0.0),
            persisted = data.requireDosingBoolean("persisted"),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = DeviceDosingV1Contract.Literal.RESERVOIR_REFILL,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.persisted)
        }
    }

    private fun parseSaved(
        data: JSONObject,
        expectedOperation: String
    ): DeviceDosingV1SavedMutationResult {
        data.requireDosingKeys(SAVED_KEYS, "$expectedOperation result")
        return DeviceDosingV1SavedMutationResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            saved = data.requireDosingBoolean("saved"),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = expectedOperation,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(result.saved)
        }
    }

    private fun parseSimpleStop(
        data: JSONObject,
        expectedOperation: String
    ): DeviceDosingV1SimpleStopResult {
        data.requireDosingKeys(SIMPLE_STOP_KEYS, "$expectedOperation result")
        return DeviceDosingV1SimpleStopResult(
            operation = data.requireDosingString("operation"),
            channelKey = data.requireDosingChannelKey("channelKey"),
            manualActive = data.requireDosingBoolean("manualActive"),
            event = data.requireDosingString("event"),
            channel = DeviceDosingV1StatusParser.parseChannelDetail(
                data.requireDosingObject("channel")
            )
        ).also { result ->
            validateCommon(
                operation = result.operation,
                expectedOperation = expectedOperation,
                channelKey = result.channelKey,
                event = result.event,
                channel = result.channel
            )
            require(!result.manualActive)
        }
    }

    private fun validateCommon(
        operation: String,
        expectedOperation: String,
        channelKey: DeviceDosingV1ChannelKey,
        event: String,
        channel: DeviceDosingV1ChannelDetail
    ) {
        require(operation == expectedOperation)
        require(event == DeviceDosingV1Contract.STATUS_CHANGED_EVENT)
        require(channelKey == channel.channelKey)
    }

    private val SAVED_KEYS = setOf("operation", "channelKey", "saved", "event", "channel")
    private val PRIME_START_KEYS = setOf(
        "operation", "channelKey", "durationMs", "doseMsPerMl", "manualActive",
        "event", "channel"
    )
    private val SIMPLE_STOP_KEYS = setOf(
        "operation", "channelKey", "manualActive", "event", "channel"
    )
    private val DOSE_NOW_KEYS = setOf(
        "operation", "channelKey", "amountMl", "durationMs", "doseMsPerMl",
        "usePendingCalibration", "manualActive", "event", "channel"
    )
    private val CALIBRATION_START_KEYS = setOf(
        "operation", "channelKey", "durationMs", "calibrationState", "event", "channel"
    )
    private val CALIBRATION_FINISH_KEYS = setOf(
        "operation", "channelKey", "measuredMl", "durationMs", "pendingDoseMsPerMl",
        "calibrationState", "event", "channel"
    )
    private val CALIBRATION_CONFIRM_KEYS = setOf(
        "operation", "channelKey", "revision", "doseMsPerMl", "lastCalibratedAt",
        "calibrationState", "saved", "event", "channel"
    )
    private val CALIBRATION_CANCEL_KEYS = setOf(
        "operation", "channelKey", "discardedPendingCalibration", "calibrationState",
        "event", "channel"
    )
    private val RESERVOIR_REFILL_KEYS = setOf(
        "operation", "channelKey", "reservoirRemainingMlBefore", "reservoirRemainingMl",
        "persisted", "event", "channel"
    )
}
