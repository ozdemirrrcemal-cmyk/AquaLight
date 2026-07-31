package com.aqua.aqualight.data.devices.runtime.modules.timer

/** Exact Android mirror of the authenticated firmware timer API. */
object DeviceTimerRuntimeContract {

    const val MODULE = "timer"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val CHANNEL_SET = "channel.set"
    }

    object Field {
        const val CHANNELS = "channels"
        const val SCHEDULES = "schedules"
        const val SAVE = "save"

        const val CHANNEL_KEY = "channelKey"
        const val DISPLAY_NAME = "displayName"
        const val REGIME = "regime"

        const val INDEX = "index"
        const val KEY = "key"
        const val NAME = "name"
        const val ENABLED = "enabled"
        const val WEEKDAYS = "weekdays"
        const val START_TIME_MS = "startTimeMs"
        const val INTERVAL_ON_MS = "intervalOnMs"
        const val INTERVAL_OFF_MS = "intervalOffMs"
        const val REPEAT_COUNT = "repeatCount"
    }

    object Limit {
        const val MAX_SCHEDULES = 24
        const val DISPLAY_NAME_BYTES = 32
    }
}
