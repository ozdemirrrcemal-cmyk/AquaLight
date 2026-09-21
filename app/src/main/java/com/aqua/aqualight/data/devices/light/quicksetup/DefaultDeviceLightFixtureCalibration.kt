package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibration
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationRequest
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationResult

/**
 * Production fixture-calibration boundary.
 *
 * AquaLight Quick Setup must never synthesize optical output from percentages, another brand's PAR
 * chart, or a development fallback. Until measured AquaLight calibration records are committed,
 * every request fails closed and the recommendation engine reports missing calibration.
 */
internal class DefaultDeviceLightFixtureCalibration : DeviceLightFixtureCalibration {
    override fun solve(
        request: DeviceLightFixtureCalibrationRequest
    ): DeviceLightFixtureCalibrationResult? {
        @Suppress("UNUSED_VARIABLE")
        val validatedRequest = request
        return null
    }
}
