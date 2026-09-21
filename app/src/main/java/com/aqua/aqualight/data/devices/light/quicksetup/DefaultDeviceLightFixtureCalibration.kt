package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibration
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationRequest
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationResult

/**
 * Production fixture-calibration boundary.
 *
 * AquaLight Quick Setup must never synthesize optical output from percentages, another brand's PAR
 * chart, or a fallback profile. Until measured AquaLight calibration records are committed, every
 * request fails closed and the recommendation engine reports missing calibration.
 */
internal class DefaultDeviceLightFixtureCalibration(
    private val calibratedProfiles: Map<String, DeviceLightFixtureCalibration> = emptyMap()
) : DeviceLightFixtureCalibration {

    override fun solve(
        request: DeviceLightFixtureCalibrationRequest
    ): DeviceLightFixtureCalibrationResult? =
        calibratedProfiles[request.productKey]?.solve(request)
}
