package com.aqua.aqualight.data.devices.runtime.modules.light

object DeviceLightRuntimeContract {

    const val MODULE = "light"

    object Action {
        const val STATUS_GET = "status.get"
        const val MANUAL_SET = "manual.set"
        const val CHANNEL_REGIME_SET = "channel.regime.set"
        const val PROGRAM_APPLY = "program.apply"
        const val PROGRAM_DELETE = "program.delete"
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
    }

    object Limit {
        const val MAX_MANUAL_CHANNELS = 16
        const val DEFAULT_MANUAL_DURATION_MS = 15L * 60L * 1000L
        const val MIN_MANUAL_DURATION_MS = 1000L
        const val MAX_MANUAL_DURATION_MS = 86_400_000L

        const val MAX_PROGRAM_POINTS = 96
        const val MILLIS_IN_DAY = 86_400_000L
    }
}
