package com.aqua.aqualight.data.devices.model

data class DeviceLimits(
    val lightChannelCount: Int = 0,
    val fanOutputCount: Int = 0,
    val temperatureSensorCount: Int = 0,
    val timerChannelCount: Int = 0,
    val dosingChannelCount: Int = 0
)
