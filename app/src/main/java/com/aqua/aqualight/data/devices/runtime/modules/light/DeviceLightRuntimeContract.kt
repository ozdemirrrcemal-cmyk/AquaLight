package com.aqua.aqualight.data.devices.runtime.modules.light

object DeviceLightRuntimeContract {

    const val MODULE = "light"

    object Action {
        const val STATUS_GET = "status.get"
        const val MANUAL_SET = "manual.set"
        const val CHANNEL_REGIME_SET = "channel.regime.set"
        const val PROGRAM_APPLY = "program.apply"
        const val PROGRAM_DELETE = "program.delete"
        const val TEMPERATURE_PROTECTION_STATUS_GET = "temperature-protection.status.get"
        const val TEMPERATURE_PROTECTION_SET = "temperature-protection.set"
    }

    object Field {
        const val CHANNEL_KEY = "channelKey"
        const val CHANNELS = "channels"
        const val CLEAR = "clear"
        const val DURATION_MS = "durationMs"
        const val PERCENT = "percent"
        const val VALUE = "value"
        const val REGIME = "regime"
        const val SAVE = "save"

        const val PROGRAM_INDEX = "programIndex"
        const val INDEX = "index"
        const val POINTS = "points"
        const val TIME_MS = "timeMs"
        const val TIME = "time"

        const val SUPPORTED = "supported"
        const val ACTIVE = "active"
        const val TEMPERATURE_PROTECTION = "temperatureProtection"
        const val THRESHOLD_EDITABLE = "thresholdEditable"
        const val THRESHOLD_C = "thresholdC"
        const val MINIMUM_C = "minimumC"
        const val MAXIMUM_C = "maximumC"
        const val RUNTIME = "runtime"
        const val MODULE = "module"
        const val READ_ONLY = "readOnly"
        const val SUPPORTS_STATUS_GET = "supportsStatusGet"
        const val SUPPORTS_SET = "supportsSet"
        const val EVENT = "event"
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

    object Event {
        const val STATUS_CHANGED = "light.status.changed"
    }

    object Transport {
        const val WEBSOCKET = "websocket"
    }

    object Limit {
        const val MAX_MANUAL_CHANNELS = 16
        const val DEFAULT_MANUAL_DURATION_MS = 15L * 60L * 1000L
        const val MIN_MANUAL_DURATION_MS = 1000L
        const val MAX_MANUAL_DURATION_MS = 86_400_000L

        const val MAX_PROGRAM_POINTS = 96
        const val MILLIS_IN_DAY = 86_400_000L

        const val MIN_TEMPERATURE_PROTECTION_C = 0.0
        const val MAX_TEMPERATURE_PROTECTION_C = 120.0
    }
}
