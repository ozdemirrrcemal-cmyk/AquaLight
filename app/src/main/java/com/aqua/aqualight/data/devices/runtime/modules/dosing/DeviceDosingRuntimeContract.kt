package com.aqua.aqualight.data.devices.runtime.modules.dosing

/** Exact Android mirror of the authenticated commercial Dosing firmware contract. */
object DeviceDosingRuntimeContract {
    const val MODULE = "dosing"
    const val STATUS_EVENT = "dosing.status.changed"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
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
        const val CHANNELS = "channels"
        const val SCHEDULES = "schedules"
        const val SAVE = "save"

        const val CHANNEL_KEY = "channelKey"
        const val DISPLAY_NAME = "displayName"
        const val REGIME = "regime"
        const val DOSING = "dosing"

        const val DOSE_MS_PER_ML = "doseMsPerMl"
        const val LAST_CALIBRATED_AT = "lastCalibratedAt"
        const val RESERVOIR_TRACKING_ENABLED = "reservoirTrackingEnabled"
        const val RESERVOIR_CAPACITY_ML = "reservoirCapacityMl"

        const val ENABLED = "enabled"
        const val NAME = "name"
        const val WEEKDAYS = "weekdays"
        const val START_TIME_MS = "startTimeMs"
        const val INTERVAL_ON_MS = "intervalOnMs"
        const val INTERVAL_OFF_MS = "intervalOffMs"
        const val REPEAT_COUNT = "repeatCount"
        const val AMOUNT_ML = "amountMl"

        const val DURATION_MS = "durationMs"
        const val MEASURED_ML = "measuredMl"
        const val USE_PENDING_CALIBRATION = "usePendingCalibration"
    }

    object Limit {
        const val MAX_SCHEDULES = 24
        const val MAX_CHANNELS = 8
        const val MAX_CHANNEL_DISPLAY_NAME_BYTES = 32
        const val MAX_SCHEDULE_NAME_BYTES = 64
        const val MAX_MANUAL_DOSE_ML = 1000.0
        const val MIN_CALIBRATION_MEASURED_ML = 0.05
        const val MAX_CALIBRATION_MEASURED_ML = 1000.0
        const val MIN_CALIBRATION_DURATION_MS = 1_000L
        const val DEFAULT_CALIBRATION_DURATION_MS = 5_000L
        const val MAX_CALIBRATION_DURATION_MS = 60_000L
        const val MIN_MANUAL_DOSE_DURATION_MS = 100L
        const val MAX_MANUAL_DOSE_DURATION_MS = 3_600_000L
        const val MAX_DOSE_MS_PER_ML = 3_600_000L
        const val LAST_MILLISECOND_OF_DAY = 86_399_999L
    }

    object Literal {
        const val STATUS_SCHEMA = "aqualight.dosing.v1"
        const val STATUS_ROOT = MODULE
        const val UNIT_ML = "ml"
        const val CONFIG_APPLY_OPERATION = "configApply"
        const val PRIME_START_OPERATION = "primeStart"
        const val PRIME_STOP_OPERATION = "primeStop"
        const val CALIBRATION_START_OPERATION = "calibrationStart"
        const val CALIBRATION_FINISH_OPERATION = "calibrationFinish"
        const val CALIBRATION_CONFIRM_OPERATION = "calibrationConfirm"
        const val CALIBRATION_CANCEL_OPERATION = "calibrationCancel"
        const val DOSE_NOW_OPERATION = "doseNow"
        const val DOSE_STOP_OPERATION = "doseStop"
        const val RESERVOIR_REFILL_OPERATION = "reservoirRefill"
        const val RUNTIME_TRANSPORT = "websocket"
        const val CHANNEL_KIND_GPIO = "gpio"
        const val CHANNEL_KIND_DIGITAL = "digital"
        const val CHANNEL_KIND_NONE = "none"
    }
}
