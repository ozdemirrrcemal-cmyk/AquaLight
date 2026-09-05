package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

/** Exact Android mirror of firmware's strict Cool Pro 1F contract. */
object DeviceCoolingV1Contract {
    const val SCHEMA = "aql.cooling.v1"
    const val SCHEMA_VERSION = 1
    const val CATALOG_VERSION = 1
    const val CATALOG_SHA256 =
        "dac61fd3b16ad1f29df59f3c7b881bb562007f9e629e7a636d607fc3d84c0531"
    const val PRODUCT_KEY = "COOLING_COOL_PRO_1F"
    const val FAN_KEY = "fan1"
    const val WATER_SENSOR_KEY = "water"
    const val AMBIENT_SENSOR_KEY = "ambient"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val MANUAL_APPLY = "manual.apply"
        const val PROGRAM_GET = "program.get"
        const val PROGRAM_APPLY = "program.apply"
        const val HISTORY_GET = "history.get"
    }

    object Event {
        const val STATUS_CHANGED = "cooling.status.changed"
        const val TELEMETRY_CHANGED = "cooling.telemetry.changed"
    }

    /** Exact effective Cooling V1 rejection catalog: shared fixture plus command-local emissions. */
    object Error {
        const val BAD_REQUEST = "BAD_REQUEST"
        const val MISSING_FIELD = "MISSING_FIELD"
        const val INVALID_VALUE = "INVALID_VALUE"
        const val NOT_FOUND = "NOT_FOUND"
        const val CONFLICT = "CONFLICT"
        const val HARDWARE_ERROR = "HARDWARE_ERROR"
        const val STORAGE_ERROR = "STORAGE_ERROR"
        const val CLOCK_UNSYNCED = "CLOCK_UNSYNCED"
    }

    object Limit {
        const val UINT32_MAX = 4_294_967_295L
        const val ALIGNMENT_EPSILON = 0.0001
        const val FAN_OUTPUT_CAPACITY = 1
        const val SENSOR_SLOT_CAPACITY = 2
        const val PROGRAM_SLOT_CAPACITY = 8
        const val PROGRAM_SLOT_COUNT_MINIMUM = 1
        const val MINUTES_PER_DAY = 1_440
        const val MINUTE_MINIMUM = 0
        const val END_MINUTE_MINIMUM = 1
        const val PROGRAM_TIME_STEP_MINUTES = 5
        const val PROGRAM_MINIMUM_DURATION_MINUTES = 15
        const val TEMPERATURE_MINIMUM_C = 0.0
        const val TEMPERATURE_MAXIMUM_C = 40.0
        const val TEMPERATURE_STEP_C = 0.5
        const val SENSOR_READING_MINIMUM_C = -40.0
        const val SENSOR_READING_MAXIMUM_C = 125.0
        const val HUMIDITY_PERCENT_MINIMUM = 0.0
        const val HUMIDITY_PERCENT_MAXIMUM = 100.0
        const val MINIMUM_AUTOMATIC_GAP_C = 0.5
        const val FAN_PERCENT_MINIMUM = 0.0
        const val FAN_PERCENT_MAXIMUM = 100.0
        const val FAN_PERCENT_STEP = 1.0
        const val FAN_RPM_MINIMUM = 0.0
        const val SILENT_MODE_MAXIMUM_PERCENT = 50.0
        const val ACTIVE_ALARM_COUNT_MAXIMUM = 6
        const val SENSOR_STALE_AFTER_MS = 10_000L
    }
}

enum class DeviceCoolingV1ControlMode(val wireValue: String) {
    AUTOMATIC("AUTOMATIC"),
    MANUAL("MANUAL"),
    PROGRAM("PROGRAM")
}

enum class DeviceCoolingV1OperatingState {
    IDLE,
    COOLING,
    MANUAL,
    PROGRAM,
    FAULT
}

enum class DeviceCoolingV1HistoryRange(val wireValue: String) {
    HOURS_24("24h"),
    DAYS_7("7d"),
    DAYS_30("30d")
}

enum class DeviceCoolingV1ChartSource {
    SAMPLES,
    DAILY_AVERAGE
}
