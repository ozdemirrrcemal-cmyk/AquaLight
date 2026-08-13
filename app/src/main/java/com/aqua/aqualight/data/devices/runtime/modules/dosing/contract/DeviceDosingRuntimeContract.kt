package com.aqua.aqualight.data.devices.runtime.modules.dosing.contract

/** Exact Android mirror of the final unreleased `aqualight.dosing.v1` firmware contract. */
object DeviceDosingRuntimeContract {
    const val MODULE = "dosing"
    const val STATUS_EVENT = "dosing.status.changed"
    const val SCHEMA = "aqualight.dosing.v1"
    const val SCHEMA_VERSION = 1
    const val UNIT_ML = "ml"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val PROGRAM_APPLY = "program.apply"
        const val CHANNEL_RESET = "channel.reset"
        const val PRIME_START = "prime.start"
        const val PRIME_STOP = "prime.stop"
        const val CALIBRATION_START = "calibration.start"
        const val CALIBRATION_FINISH = "calibration.finish"
        const val CALIBRATION_CONFIRM = "calibration.confirm"
        const val CALIBRATION_CANCEL = "calibration.cancel"
        const val DOSE_NOW = "dose.now"
        const val DOSE_STOP = "dose.stop"
        const val RESERVOIR_REFILL = "reservoir.refill"
    }

    object Field {
        const val CHANNEL_KEY = "channelKey"
        const val EXPECTED_REVISION = "expectedRevision"
        const val DISPLAY_NAME = "displayName"
        const val RESERVOIR = "reservoir"
        const val TRACKING_ENABLED = "trackingEnabled"
        const val CAPACITY_ML = "capacityMl"

        const val PROGRAM = "program"
        const val ENABLED = "enabled"
        const val WEEKDAYS = "weekdays"
        const val MODE = "mode"
        const val MISSED_DOSE_RECOVERY_ENABLED = "missedDoseRecoveryEnabled"
        const val CONFIG = "config"
        const val DAILY_DOSE_ML = "dailyDoseMl"
        const val START_TIME_MS = "startTimeMs"
        const val PERIODS = "periods"
        const val END_TIME_MS = "endTimeMs"
        const val DOSE_COUNT = "doseCount"
        const val EVENTS = "events"
        const val TIME_MS = "timeMs"
        const val AMOUNT_ML = "amountMl"

        const val DURATION_MS = "durationMs"
        const val MEASURED_ML = "measuredMl"
        const val USE_PENDING_CALIBRATION = "usePendingCalibration"
    }

    object Limit {
        const val MAX_CHANNELS = 4
        const val MAX_CHANNEL_DISPLAY_NAME_BYTES = 32
        const val WEEKDAY_COUNT = 7
        const val MILLIS_PER_DAY = 86_400_000L
        const val LAST_MILLISECOND_OF_DAY = MILLIS_PER_DAY - 1L
        const val MAX_UINT32 = 0xFFFF_FFFFL
        const val MIN_CALIBRATION_MEASURED_ML = 0.05
        const val MAX_CALIBRATION_MEASURED_ML = 1_000.0
        const val MIN_CALIBRATION_DURATION_MS = 1_000L
        const val DEFAULT_CALIBRATION_DURATION_MS = 5_000L
        const val MAX_CALIBRATION_DURATION_MS = 60_000L
        const val VERIFICATION_DOSE_ML = 4.0
    }

    object Literal {
        const val CHANNEL_CONFIG_APPLY_OPERATION = "channelConfigApply"
        const val PROGRAM_APPLY_OPERATION = "programApply"
        const val CHANNEL_RESET_OPERATION = "channelReset"
        const val PRIME_START_OPERATION = "primeStart"
        const val PRIME_STOP_OPERATION = "primeStop"
        const val CALIBRATION_START_OPERATION = "calibrationStart"
        const val CALIBRATION_FINISH_OPERATION = "calibrationFinish"
        const val CALIBRATION_CONFIRM_OPERATION = "calibrationConfirm"
        const val CALIBRATION_CANCEL_OPERATION = "calibrationCancel"
        const val DOSE_NOW_OPERATION = "doseNow"
        const val DOSE_STOP_OPERATION = "doseStop"
        const val RESERVOIR_REFILL_OPERATION = "reservoirRefill"

        const val PROGRAM_SINGLE = "single"
        const val PROGRAM_HOURLY_24 = "hourly24"
        const val PROGRAM_CUSTOM_PERIODS = "customPeriods"
        const val PROGRAM_TIMER = "timer"
        const val PROGRAM_NONE = "none"

        const val CALIBRATION_STATE_IDLE = "idle"
        const val CALIBRATION_STATE_RUNNING = "running"
        const val CALIBRATION_STATE_PENDING_VERIFICATION = "pendingVerification"
    }
}

internal const val DOSING_NON_NEGATIVE_LONG = 0L
internal const val DOSING_UNSET_RESERVOIR = -1.0
internal const val DOSING_PERCENT_MAX = 100.0
internal const val DOSING_VALUE_EPSILON = 0.000_001

internal fun normalizeDosingChannelKey(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Dosing channelKey must not be empty." }
    require(normalized == normalized.lowercase()) { "Dosing channelKey must be canonical lowercase." }
    require(normalized.all { char -> char.isLetterOrDigit() || char == '_' || char == '-' }) {
        "Dosing channelKey contains unsupported characters."
    }
    return normalized
}

internal fun isNewerDosingSample(candidate: Long, current: Long): Boolean {
    require(candidate in 0L..DeviceDosingRuntimeContract.Limit.MAX_UINT32)
    require(current in 0L..DeviceDosingRuntimeContract.Limit.MAX_UINT32)
    if (candidate == current) return true
    return ((candidate - current) and DeviceDosingRuntimeContract.Limit.MAX_UINT32) < 0x8000_0000L
}

internal fun dosingValuesEquivalent(first: Double, second: Double): Boolean =
    kotlin.math.abs(first - second) <= DOSING_VALUE_EPSILON
