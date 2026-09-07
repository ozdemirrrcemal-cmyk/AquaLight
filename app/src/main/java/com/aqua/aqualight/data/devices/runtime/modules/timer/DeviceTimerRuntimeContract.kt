package com.aqua.aqualight.data.devices.runtime.modules.timer

/** Exact Android mirror of the Relay Pro Timer V1 firmware contract. */
object DeviceTimerRuntimeContract {
    const val FIRMWARE_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight-Firmware"
    const val PINNED_FIRMWARE_COMMIT = "90b6597216d0c697542d5dc12e26647625806d8f"
    const val MODULE = "timer"
    const val STATUS_EVENT = "timer.status.changed"
    const val SCHEMA = "aqualight.timer.v1"
    const val SCHEMA_VERSION = 1

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val CHANNEL_SET = "channel.set"
    }

    /** Error codes emitted by the pinned Timer V1 firmware command contract. */
    object Error {
        const val BAD_REQUEST = "BAD_REQUEST"
        const val MISSING_FIELD = "MISSING_FIELD"
        const val INVALID_VALUE = "INVALID_VALUE"
        const val NOT_FOUND = "NOT_FOUND"
        const val CONFLICT = "CONFLICT"
        const val HARDWARE_ERROR = "HARDWARE_ERROR"
        const val STORAGE_ERROR = "STORAGE_ERROR"
    }

    object Field {
        const val CHANNEL_KEY = "channelKey"
        const val EXPECTED_REVISION = "expectedRevision"
        const val DISPLAY_NAME = "displayName"
        const val SCHEDULES = "schedules"
        const val SAVE = "save"
        const val REGIME = "regime"
        const val DURATION_MS = "durationMs"

        const val SLOT_ID = "slotId"
        const val ENABLED = "enabled"
        const val NAME = "name"
        const val WEEKDAYS = "weekdays"
        const val START_TIME_MS = "startTimeMs"
        const val END_TIME_MS = "endTimeMs"
        const val SPANS_MIDNIGHT = "spansMidnight"
    }

    object Limit {
        const val UINT32_MAX = 4_294_967_295L
        const val MAX_CHANNELS = 4
        const val MAX_SCHEDULES_PER_CHANNEL = 8
        const val MAX_SCHEDULE_NAME_BYTES = 48
        const val MAX_CHANNEL_DISPLAY_NAME_BYTES = 48
        const val MAX_CHANNEL_KEY_BYTES = 24
        const val SLOT_ID_MINIMUM = 1
        const val SLOT_ID_MAXIMUM = 8
        const val DAY_MILLISECONDS = 86_400_000L
        const val LAST_MILLISECOND_OF_DAY = DAY_MILLISECONDS - 1L
        const val SCHEDULE_BOUNDARY_GRANULARITY_MS = 60_000L
        const val TEMPORARY_DURATION_MINIMUM_MS = 1L
        const val TEMPORARY_DURATION_MAXIMUM_MS = DAY_MILLISECONDS
        const val DECODED_DATA_MAXIMUM_BYTES = 4_096
        const val ENCODED_DATA_MAXIMUM_CHARACTERS = 5_464
        const val JSON_KEY_MAXIMUM = 128
    }

    object Literal {
        const val STATUS_ROOT = MODULE
        const val CONFIG_APPLY_OPERATION = "channelConfigApply"
        const val CHANNEL_SET_OPERATION = "channelSet"
        const val TEMPORARY_OVERRIDE_OPERATION = "temporaryOverride"
        const val RUNTIME_TRANSPORT = "websocket"
        const val CONFIG_APPLY_SCOPE = "channel"
        const val CHANNEL_KIND_GPIO = "gpio"
        const val CHANNEL_KIND_DIGITAL = "digital"
        const val CHANNEL_KIND_NONE = "none"
        const val OUTPUT_HEALTH_UNVERIFIED = "UNVERIFIED"
        const val OUTPUT_HEALTH_HARDWARE_FAULT = "HARDWARE_FAULT"
        const val NEXT_TRANSITION_NONE = "NONE"
        const val NEXT_TRANSITION_ON = "ON"
        const val NEXT_TRANSITION_OFF = "OFF"
    }
}
