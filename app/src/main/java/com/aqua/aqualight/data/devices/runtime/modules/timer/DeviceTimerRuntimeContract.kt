package com.aqua.aqualight.data.devices.runtime.modules.timer

/** Exact Android mirror of the authenticated standalone Timer firmware contract. */
object DeviceTimerRuntimeContract {
    const val MODULE = "timer"
    const val STATUS_EVENT = "timer.status.changed"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val CHANNEL_SET = "channel.set"
    }

    object Field {
        const val SUPPORTED = "supported"
        const val CHANNEL_COUNT = "channelCount"
        const val SCHEDULE_COUNT = "scheduleCount"
        const val LOCK_LOOP = "lockLoop"
        const val SCHEMA = "schema"
        const val ROOT_NAME = "rootName"
        const val UPTIME_MS = "uptimeMs"
        const val CHANNELS = "channels"
        const val SCHEDULES = "schedules"
        const val RUNTIME = "runtime"
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
        const val AMOUNT_ML = "amountMl"
    }

    object Limit {
        const val MAX_SCHEDULES = 24
        const val MAX_CHANNELS = 8
        const val MAX_CHANNEL_DISPLAY_NAME_BYTES = 32
        const val MAX_SCHEDULE_NAME_BYTES = 64
        const val LAST_MILLISECOND_OF_DAY = 86_399_999L
    }

    object Literal {
        const val STATUS_SCHEMA = "aqualight.timer.v1"
        const val STATUS_ROOT = MODULE
        const val CONFIG_APPLY_OPERATION = "configApply"
        const val CHANNEL_SET_OPERATION = "channelSet"
        const val RUNTIME_TRANSPORT = "websocket"
        const val CHANNEL_KIND_GPIO = "gpio"
        const val CHANNEL_KIND_DIGITAL = "digital"
        const val CHANNEL_KIND_NONE = "none"
    }
}
