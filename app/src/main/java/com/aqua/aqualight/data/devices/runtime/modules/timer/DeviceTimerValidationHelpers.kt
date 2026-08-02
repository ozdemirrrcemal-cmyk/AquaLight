package com.aqua.aqualight.data.devices.runtime.modules.timer

internal fun validateReturnedTimerSchedules(
    requested: List<DeviceTimerScheduleConfig>?,
    config: DeviceTimerConfigSnapshot
) {
    if (requested == null) return
    val expected = requested.mapIndexed { index, schedule ->
        DeviceTimerScheduleConfigSnapshot(
            listIndex = index,
            enabled = schedule.enabled,
            name = schedule.normalizedName,
            channelKey = schedule.normalizedChannelKey,
            weekdays = schedule.weekdays.toList(),
            startTimeMs = schedule.startTimeMs,
            intervalOnMs = schedule.intervalOnMs,
            intervalOffMs = schedule.intervalOffMs,
            repeatCount = schedule.repeatCount
        )
    }
    require(config.schedules == expected) {
        "Firmware Timer schedule snapshot differs from the requested replacement."
    }
}

internal fun DeviceTimerChannelStatus.sameTimerChannelIdentity(
    other: DeviceTimerChannelStatus
): Boolean = copy(
    regime = other.regime,
    valueNow = other.valueNow,
    valueAuto = other.valueAuto,
    valueManual = other.valueManual,
    manualTimeoutMs = other.manualTimeoutMs
) == other
