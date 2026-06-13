package com.aqua.aqualight.data.devices.api.timer

data class TimerStatus(
    val isRunning: Boolean = false,
    val activeChannel: Int? = null,
    val nextEventText: String = ""
)

data class TimerSchedule(
    val id: String,
    val channelIndex: Int,
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val repeatDays: Set<Int> = emptySet()
)
