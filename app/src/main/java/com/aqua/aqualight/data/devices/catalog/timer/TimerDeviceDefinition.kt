package com.aqua.aqualight.data.devices.catalog.timer

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition

data class TimerDeviceDefinition(
    val base: AquaDeviceDefinition,
    val timerFeatures: Set<TimerFeature>,
    val maxTimerCount: Int,
    val maxOutputCount: Int = 1
) {
    init {
        require(maxTimerCount > 0) {
            "maxTimerCount must be > 0."
        }

        require(maxOutputCount > 0) {
            "maxOutputCount must be > 0."
        }
    }

    fun supports(
        feature: TimerFeature
    ): Boolean {
        return timerFeatures.contains(feature)
    }
}