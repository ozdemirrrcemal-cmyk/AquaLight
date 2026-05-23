package com.aqua.aqualight.data.devices.catalog.cooling

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition

data class CoolingDeviceDefinition(
    val base: AquaDeviceDefinition,
    val coolingFeatures: Set<CoolingFeature>,
    val maxFanChannelCount: Int,
    val maxSensorCount: Int
) {
    init {
        require(maxFanChannelCount > 0) {
            "maxFanChannelCount must be > 0."
        }

        require(maxSensorCount >= 0) {
            "maxSensorCount must be >= 0."
        }
    }

    fun supports(
        feature: CoolingFeature
    ): Boolean {
        return coolingFeatures.contains(feature)
    }
}