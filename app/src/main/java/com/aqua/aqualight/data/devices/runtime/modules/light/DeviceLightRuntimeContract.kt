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
        const val CHANNEL = "channel"
        const val CLEAR = "clear"
        const val DURATION_MS = "durationMs"
        const val PERCENT = "percent"
        const val VALUE = "value"
        const val REGIME = "regime"
        const val SAVE = "save"
        const val MANUAL_ACTIVE = "manualActive"
        const val AFFECTED_CHANNEL_COUNT = "affectedChannelCount"

        const val PROGRAM_INDEX = "programIndex"
        const val PROGRAM_COUNT = "programCount"
        const val PROGRAM = "program"
        const val INDEX = "index"
        const val POINTS = "points"
        const val TIME_MS = "timeMs"
        const val TIME = "time"
        const val CREATED = "created"
        const val DELETED = "deleted"
        const val CHANNEL_LIST_INDEX = "channelListIndex"
        const val DELETED_LIST_INDEX = "deletedListIndex"
        const val DELETED_POINT_COUNT = "deletedPointCount"

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
        const val MANUAL_STATE = "manualState"
        const val CLEAR_MANUAL = "clearManual"
        const val CHANNEL_REGIME_SET = "channelRegimeSet"
        const val PROGRAM_APPLY = "programApply"
        const val PROGRAM_DELETE = "programDelete"
        const val TEMPERATURE_PROTECTION_SET = "temperatureProtectionSet"
    }

    object QualifiedCommand {
        const val MANUAL_SET = "light.manual.set"
        const val CHANNEL_REGIME_SET = "light.channel.regime.set"
        const val PROGRAM_APPLY = "light.program.apply"
        const val PROGRAM_DELETE = "light.program.delete"
        const val TEMPERATURE_PROTECTION_SET = "light.temperature-protection.set"
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

        const val MIN_TEMPERATURE_PROTECTION_C = 50.0
        const val DEFAULT_TEMPERATURE_PROTECTION_C = 60.0
        const val MAX_TEMPERATURE_PROTECTION_C = 70.0
    }
}
