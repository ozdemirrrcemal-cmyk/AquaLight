package com.aqua.aqualight.data.devices.runtime.modules.timer

/**
 * Firmware verified Android mirror.
 *
 * Firmware:
 * AquaLight-Firmware / feature/ble-qr-wifi-provisioning
 *
 * module: timer
 * actions:
 * - status.get
 * - config.apply
 * - channel.set
 */
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
        /** Firmware rejects timer schedules above this count. */
        const val MAX_SCHEDULES = 24
    }
}
