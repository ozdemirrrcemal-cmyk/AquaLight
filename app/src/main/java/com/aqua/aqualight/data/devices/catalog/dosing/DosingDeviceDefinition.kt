package com.aqua.aqualight.data.devices.catalog.dosing

import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition

data class DosingDeviceDefinition(
    val base: AquaDeviceDefinition,
    val dosingFeatures: Set<DosingFeature>,
    val pumpCount: Int,
    val maxScheduleCount: Int
) {
    init {
        require(pumpCount > 0) {
            "pumpCount must be > 0."
        }

        require(maxScheduleCount > 0) {
            "maxScheduleCount must be > 0."
        }
    }

    fun supports(
        feature: DosingFeature
    ): Boolean {
        return dosingFeatures.contains(feature)
    }
}