package com.aqua.aqualight.data.devices.runtime.modules.light

/** Exact, case-sensitive Android mirror of the firmware Light V1 wire surface. */
object DeviceLightRuntimeContract {
    const val FIRMWARE_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight-Firmware"
    const val PINNED_FIRMWARE_COMMIT = "7df97ce807ebb1e90ff63cc36206d6ce479a62fc"
    const val MODULE = "light"
    const val SCHEMA = "aqualight.light.v1"
    const val STORAGE_VERSION = 1

    object Product {
        const val WRGB_PRO_ELITE = "LIGHT_WRGB_PRO_ELITE"
        const val RGB_PRO_SLIM = "LIGHT_RGB_PRO_SLIM"
    }

    object Action {
        const val STATUS_GET = "status.get"
        const val CONTROL_SET = "control.set"
        const val MANUAL_SET = "manual.set"
        const val MANUAL_OFF = "manual.off"
        const val AUTO_PROGRAMS_GET = "auto.programs.get"
        const val AUTO_PROGRAM_CREATE = "auto.program.create"
        const val AUTO_PROGRAM_UPDATE = "auto.program.update"
        const val AUTO_PROGRAM_ENABLED_SET = "auto.program.enabled.set"
        const val AUTO_PROGRAM_DELETE = "auto.program.delete"
        const val CUSTOM_GET = "custom.get"
        const val CUSTOM_INSTALL = "custom.install"
        const val ACCLIMATION_STATUS_GET = "acclimation.status.get"
        const val ACCLIMATION_START = "acclimation.start"
        const val ACCLIMATION_STOP = "acclimation.stop"
        const val GRAPH_GET = "graph.get"
        const val PREVIEW_SET = "preview.set"
        const val PREVIEW_CLEAR = "preview.clear"
        const val TEMPERATURE_PROTECTION_STATUS_GET = "temperature-protection.status.get"
        const val TEMPERATURE_PROTECTION_SET = "temperature-protection.set"

        val COMMON_V1 = linkedSetOf(
            STATUS_GET,
            CONTROL_SET,
            MANUAL_SET,
            MANUAL_OFF,
            AUTO_PROGRAMS_GET,
            AUTO_PROGRAM_CREATE,
            AUTO_PROGRAM_UPDATE,
            AUTO_PROGRAM_ENABLED_SET,
            AUTO_PROGRAM_DELETE,
            CUSTOM_GET,
            CUSTOM_INSTALL,
            GRAPH_GET,
            PREVIEW_SET,
            PREVIEW_CLEAR
        )
        val ACCLIMATION_V1 = linkedSetOf(
            ACCLIMATION_STATUS_GET,
            ACCLIMATION_START,
            ACCLIMATION_STOP
        )
    }

    object Field {
        const val MODE = "mode"
        const val SCENE = "scene"
        const val EXPECTED_REVISION = "expectedRevision"
        const val REVISION = "revision"
        const val PROGRAM_ID = "programId"
        const val ENABLED = "enabled"
        const val WEEKDAYS_MASK = "weekdaysMask"
        const val START_TIME_MS = "startTimeMs"
        const val END_TIME_MS = "endTimeMs"
        const val RAMP_DURATION_MS = "rampDurationMs"
        const val POINTS = "points"
        const val START_PERCENT = "startPercent"
        const val DURATION_DAYS = "durationDays"
        const val VIRTUAL_TIME_MS = "virtualTimeMs"
        const val DURATION_MS = "durationMs"
        const val EVENT = "event"
        const val THRESHOLD_C = "thresholdC"
        const val SAVE = "save"
        const val SUPPORTED = "supported"
        const val ACTIVE = "active"
        const val TEMPERATURE_PROTECTION = "temperatureProtection"
        const val THRESHOLD_EDITABLE = "thresholdEditable"
        const val MINIMUM_C = "minimumC"
        const val MAXIMUM_C = "maximumC"
        const val RUNTIME = "runtime"
        const val MODULE = "module"
        const val READ_ONLY = "readOnly"
        const val SUPPORTS_STATUS_GET = "supportsStatusGet"
        const val SUPPORTS_SET = "supportsSet"
        const val OPERATION = "operation"
        const val CHANGED = "changed"
        const val SAVED = "saved"
        const val SAVE_REQUESTED = "saveRequested"
        const val RUNTIME_TRANSPORT = "runtimeTransport"
        const val COMMAND = "command"
        const val STATUS = "status"
    }

    object Operation {
        const val TEMPERATURE_PROTECTION_SET = "temperatureProtectionSet"
    }

    object QualifiedCommand {
        const val TEMPERATURE_PROTECTION_SET = "light.temperature-protection.set"
    }

    object Event {
        const val STATUS_CHANGED = "light.status.changed"
        const val THERMAL_STATUS_CHANGED = "light.thermal.status.changed"
    }

    object Transport {
        const val WEBSOCKET = "websocket"
    }

    object Limit {
        const val UINT32_MAX = 4_294_967_295L
        const val PERCENT_MIN = 0
        const val PERCENT_MAX = 100
        const val PERMILLE_MIN = 0
        const val PERMILLE_MAX = 1000
        const val WEEKDAY_MASK_MIN = 1
        const val WEEKDAY_MASK_MAX = 127
        const val DAYS_PER_WEEK = 7
        const val RGB_COMPONENT_MAX = 255
        const val DISPLAY_COLOR_RGB_MAX = 0xFFFFFF
        const val MILLIS_IN_DAY = 86_400_000L
        const val LAST_DAY_MILLISECOND = MILLIS_IN_DAY - 1L
        const val AUTO_PROGRAM_CAPACITY = 16
        const val SCHEDULE_TIME_STEP_MS = 60_000L
        const val RAMP_DISABLED_MS = 0L
        const val RAMP_30_MINUTES_MS = 1_800_000L
        const val RAMP_60_MINUTES_MS = 3_600_000L
        const val RAMP_90_MINUTES_MS = 5_400_000L
        const val RAMP_120_MINUTES_MS = 7_200_000L
        const val RAMP_150_MINUTES_MS = 9_000_000L
        const val CUSTOM_POINT_CAPACITY = 96
        const val GRAPH_SPAN_TUPLE_SIZE = 3
        const val GRAPH_SPAN_PROGRAM_ID_INDEX = 2
        const val DEFAULT_PREVIEW_DURATION_MS = 3_000L
        const val MAX_PREVIEW_DURATION_MS = 10_000L
        const val ACCLIMATION_START_PERCENT_MIN = 20
        const val ACCLIMATION_START_PERCENT_MAX = 90
        const val ACCLIMATION_START_PERCENT_STEP = 5
        const val ACCLIMATION_DEFAULT_START_PERCENT = 50
        const val ACCLIMATION_DURATION_DAYS_MIN = 7
        const val ACCLIMATION_DURATION_DAYS_MAX = 90
        const val ACCLIMATION_DURATION_DAYS_STEP = 1
        const val ACCLIMATION_DEFAULT_DURATION_DAYS = 30
        const val ACCLIMATION_TARGET_PERCENT = PERCENT_MAX
        const val MIN_TEMPERATURE_PROTECTION_C = 50.0
        const val DEFAULT_TEMPERATURE_PROTECTION_C = 60.0
        const val MAX_TEMPERATURE_PROTECTION_C = 70.0
    }
}
