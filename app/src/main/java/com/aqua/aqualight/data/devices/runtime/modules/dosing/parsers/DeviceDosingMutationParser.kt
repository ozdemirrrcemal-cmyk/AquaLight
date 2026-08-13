package com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.normalizeDosingChannelKey
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationCancelResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationConfirmResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationState
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelConfigApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelResetResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingPumpCommandResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirRefillResult
import org.json.JSONObject

internal object DeviceDosingMutationParser {
    fun parseChannelConfigApply(data: JSONObject): DeviceDosingChannelConfigApplyResult {
        data.requireDosingKeys(CONFIG_APPLY_KEYS, "dosing.config.apply result")
        return DeviceDosingChannelConfigApplyResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.CHANNEL_CONFIG_APPLY_OPERATION),
            channelKey = channelKey(data),
            saved = data.requireDosingBoolean("saved").also { require(it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    fun parseProgramApply(data: JSONObject): DeviceDosingProgramApplyResult {
        data.requireDosingKeys(PROGRAM_APPLY_KEYS, "dosing.program.apply result")
        return DeviceDosingProgramApplyResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.PROGRAM_APPLY_OPERATION),
            channelKey = channelKey(data),
            saved = data.requireDosingBoolean("saved").also { require(it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    fun parseChannelReset(data: JSONObject): DeviceDosingChannelResetResult {
        data.requireDosingKeys(CHANNEL_RESET_KEYS, "dosing.channel.reset result")
        return DeviceDosingChannelResetResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.CHANNEL_RESET_OPERATION),
            channelKey = channelKey(data),
            saved = data.requireDosingBoolean("saved").also { require(it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { result ->
            require(result.channel.channelKey == result.channelKey)
            require(result.channel.program == null)
        }
    }

    fun parsePrimeStart(data: JSONObject): DeviceDosingPumpCommandResult =
        parseRunStart(data, DeviceDosingRuntimeContract.Literal.PRIME_START_OPERATION)

    fun parsePrimeStop(data: JSONObject): DeviceDosingPumpCommandResult =
        parseRunStop(data, DeviceDosingRuntimeContract.Literal.PRIME_STOP_OPERATION)

    fun parseDoseStop(data: JSONObject): DeviceDosingPumpCommandResult =
        parseRunStop(data, DeviceDosingRuntimeContract.Literal.DOSE_STOP_OPERATION)

    fun parseDoseNow(data: JSONObject): DeviceDosingDoseNowResult {
        data.requireDosingKeys(DOSE_NOW_KEYS, "dosing.dose.now result")
        return DeviceDosingDoseNowResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.DOSE_NOW_OPERATION),
            channelKey = channelKey(data),
            amountMl = data.requireDosingDouble("amountMl", minimum = 0.0).also { require(it > 0.0) },
            durationMs = data.requireDosingLong("durationMs", minimum = 1L),
            doseMsPerMl = data.requireDosingLong("doseMsPerMl", minimum = 1L),
            usePendingCalibration = data.requireDosingBoolean("usePendingCalibration"),
            manualActive = data.requireDosingBoolean("manualActive").also { require(it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    fun parseCalibrationStart(data: JSONObject): DeviceDosingCalibrationStartResult {
        data.requireDosingKeys(CALIBRATION_START_KEYS, "dosing.calibration.start result")
        return DeviceDosingCalibrationStartResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.CALIBRATION_START_OPERATION),
            channelKey = channelKey(data),
            durationMs = data.requireDosingLong("durationMs", minimum = 1L),
            calibrationState = calibrationState(data).also {
                require(it == DeviceDosingCalibrationState.RUNNING)
            },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    fun parseCalibrationFinish(data: JSONObject): DeviceDosingCalibrationFinishResult {
        data.requireDosingKeys(CALIBRATION_FINISH_KEYS, "dosing.calibration.finish result")
        return DeviceDosingCalibrationFinishResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.CALIBRATION_FINISH_OPERATION),
            channelKey = channelKey(data),
            measuredMl = data.requireDosingDouble("measuredMl", minimum = 0.0).also { require(it > 0.0) },
            durationMs = data.requireDosingLong("durationMs", minimum = 1L),
            pendingDoseMsPerMl = data.requireDosingLong("pendingDoseMsPerMl", minimum = 1L),
            calibrationState = calibrationState(data).also {
                require(it == DeviceDosingCalibrationState.PENDING_VERIFICATION)
            },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    fun parseCalibrationConfirm(data: JSONObject): DeviceDosingCalibrationConfirmResult {
        data.requireDosingKeys(CALIBRATION_CONFIRM_KEYS, "dosing.calibration.confirm result")
        return DeviceDosingCalibrationConfirmResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.CALIBRATION_CONFIRM_OPERATION),
            channelKey = channelKey(data),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingRuntimeContract.Limit.MAX_UINT32
            ),
            doseMsPerMl = data.requireDosingLong("doseMsPerMl", minimum = 1L),
            lastCalibratedAt = data.requireDosingLong("lastCalibratedAt", minimum = 1L),
            calibrationState = calibrationState(data).also {
                require(it == DeviceDosingCalibrationState.IDLE)
            },
            saved = data.requireDosingBoolean("saved").also { require(it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { result ->
            require(result.channel.channelKey == result.channelKey)
            require(result.channel.revision == result.revision)
        }
    }

    fun parseCalibrationCancel(data: JSONObject): DeviceDosingCalibrationCancelResult {
        data.requireDosingKeys(CALIBRATION_CANCEL_KEYS, "dosing.calibration.cancel result")
        return DeviceDosingCalibrationCancelResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.CALIBRATION_CANCEL_OPERATION),
            channelKey = channelKey(data),
            discardedPendingCalibration = data.requireDosingBoolean("discardedPendingCalibration"),
            calibrationState = calibrationState(data).also {
                require(it == DeviceDosingCalibrationState.IDLE)
            },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    fun parseReservoirRefill(data: JSONObject): DeviceDosingReservoirRefillResult {
        data.requireDosingKeys(RESERVOIR_REFILL_KEYS, "dosing.reservoir.refill result")
        return DeviceDosingReservoirRefillResult(
            operation = operation(data, DeviceDosingRuntimeContract.Literal.RESERVOIR_REFILL_OPERATION),
            channelKey = channelKey(data),
            reservoirRemainingMlBefore = data.requireDosingDouble(
                "reservoirRemainingMlBefore",
                minimum = 0.0
            ),
            reservoirRemainingMl = data.requireDosingDouble("reservoirRemainingMl", minimum = 0.0),
            persisted = data.requireDosingBoolean("persisted").also { require(it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    private fun parseRunStart(data: JSONObject, expectedOperation: String): DeviceDosingPumpCommandResult {
        data.requireDosingKeys(RUN_START_KEYS, "Dosing run start result")
        return DeviceDosingPumpCommandResult(
            operation = operation(data, expectedOperation),
            channelKey = channelKey(data),
            durationMs = data.requireDosingLong("durationMs", minimum = 1L),
            doseMsPerMl = data.requireDosingLong("doseMsPerMl", minimum = 0L),
            manualActive = data.requireDosingBoolean("manualActive").also { require(it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    private fun parseRunStop(data: JSONObject, expectedOperation: String): DeviceDosingPumpCommandResult {
        data.requireDosingKeys(RUN_STOP_KEYS, "Dosing run stop result")
        return DeviceDosingPumpCommandResult(
            operation = operation(data, expectedOperation),
            channelKey = channelKey(data),
            durationMs = null,
            doseMsPerMl = null,
            manualActive = data.requireDosingBoolean("manualActive").also { require(!it) },
            event = statusEvent(data),
            channel = channel(data)
        ).also { require(it.channel.channelKey == it.channelKey) }
    }

    private fun operation(data: JSONObject, expected: String): String =
        data.requireDosingText("operation").also { require(it == expected) }

    private fun channelKey(data: JSONObject): String =
        normalizeDosingChannelKey(data.requireDosingText("channelKey"))

    private fun statusEvent(data: JSONObject): String = data.requireDosingText("event").also {
        require(it == DeviceDosingRuntimeContract.STATUS_EVENT)
    }

    private fun calibrationState(data: JSONObject): DeviceDosingCalibrationState =
        DeviceDosingCalibrationState.fromWire(data.requireDosingText("calibrationState"))

    private fun channel(data: JSONObject) = DeviceDosingComponentParsers.parseChannelDetail(
        data.requireDosingObject("channel")
    )

    private val CONFIG_APPLY_KEYS = setOf("operation", "channelKey", "saved", "event", "channel")
    private val PROGRAM_APPLY_KEYS = CONFIG_APPLY_KEYS
    private val CHANNEL_RESET_KEYS = CONFIG_APPLY_KEYS
    private val RUN_START_KEYS = setOf(
        "operation", "channelKey", "durationMs", "doseMsPerMl", "manualActive", "event", "channel"
    )
    private val RUN_STOP_KEYS = setOf("operation", "channelKey", "manualActive", "event", "channel")
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
        "operation", "channelKey", "discardedPendingCalibration", "calibrationState", "event", "channel"
    )
    private val RESERVOIR_REFILL_KEYS = setOf(
        "operation", "channelKey", "reservoirRemainingMlBefore", "reservoirRemainingMl",
        "persisted", "event", "channel"
    )
}
