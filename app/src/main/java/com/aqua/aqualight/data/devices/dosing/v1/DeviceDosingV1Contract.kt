package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.contract.AqlWsContract

/**
 * Android mirror of the final firmware-owned aqualight.dosing.v1 wire contract.
 *
 * This package is intentionally not registered in DeviceRuntimeModuleProvider. It can be
 * exercised by contract tests while the production UI remains on application fixtures.
 */
object DeviceDosingV1Contract {
    const val MODULE = AqlWsContract.MODULE_DOSING
    const val SCHEMA = "aqualight.dosing.v1"
    const val SCHEMA_VERSION = 1L
    const val UNIT = "ml"
    const val STATUS_CHANGED_EVENT = "dosing.status.changed"

    object Action {
        const val STATUS_GET = "status.get"
        const val PROGRESS_GET = "progress.get"
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

        val ALL: Set<String> = linkedSetOf(
            STATUS_GET,
            PROGRESS_GET,
            CONFIG_APPLY,
            PROGRAM_APPLY,
            CHANNEL_RESET,
            PRIME_START,
            PRIME_STOP,
            CALIBRATION_START,
            CALIBRATION_FINISH,
            CALIBRATION_CONFIRM,
            CALIBRATION_CANCEL,
            DOSE_NOW,
            DOSE_STOP,
            RESERVOIR_REFILL
        )
    }

    object Limit {
        const val MAX_CHANNEL_KEY_BYTES = 24
        const val MAX_DISPLAY_NAME_BYTES = 32
        const val WEEKDAY_COUNT = 7
        const val MAX_EVENTS_PER_CHANNEL = 24
        const val MAX_CUSTOM_PERIODS_PER_CHANNEL = 24
        const val MILLIS_PER_DAY = 86_400_000L
        const val AMOUNT_QUANTA_PER_ML = 1_000L
        const val AMOUNT_RESOLUTION_ML = 0.001
        const val MIN_CALIBRATION_DURATION_MS = 1_000L
        const val DEFAULT_CALIBRATION_DURATION_MS = 5_000L
        const val MAX_CALIBRATION_DURATION_MS = 60_000L
        const val MIN_MEASURED_ML = 0.05
        const val MAX_MEASURED_ML = 1_000.0
        const val MAX_MANUAL_DOSE_ML = 1_000.0
        const val MAX_UNSIGNED_INT = 4_294_967_295L
        const val MAX_RESPONSE_BYTES = 4_096
        const val MIN_RESPONSE_HEADROOM_BYTES = 200
    }

    object Literal {
        const val CHANNEL_CONFIG_APPLY = "channelConfigApply"
        const val PROGRAM_APPLY = "programApply"
        const val CHANNEL_RESET = "channelReset"
        const val PRIME_START = "primeStart"
        const val PRIME_STOP = "primeStop"
        const val DOSE_NOW = "doseNow"
        const val DOSE_STOP = "doseStop"
        const val CALIBRATION_START = "calibrationStart"
        const val CALIBRATION_FINISH = "calibrationFinish"
        const val CALIBRATION_CONFIRM = "calibrationConfirm"
        const val CALIBRATION_CANCEL = "calibrationCancel"
        const val RESERVOIR_REFILL = "reservoirRefill"
    }
}
