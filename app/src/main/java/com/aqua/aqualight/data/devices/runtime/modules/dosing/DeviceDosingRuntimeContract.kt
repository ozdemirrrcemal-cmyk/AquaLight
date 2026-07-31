package com.aqua.aqualight.data.devices.runtime.modules.dosing

object DeviceDosingRuntimeContract {

    const val MODULE = "dosing"

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
        const val DISPLAY_NAME_BYTES = 32
        const val MAX_MANUAL_DOSE_ML = 1000.0
        const val MIN_CALIBRATION_MEASURED_ML = 0.05
        const val MAX_CALIBRATION_MEASURED_ML = 1000.0
        const val MIN_CALIBRATION_DURATION_MS = 1000L
        const val DEFAULT_CALIBRATION_DURATION_MS = 5000L
        const val MAX_CALIBRATION_DURATION_MS = 60000L
    }
}
