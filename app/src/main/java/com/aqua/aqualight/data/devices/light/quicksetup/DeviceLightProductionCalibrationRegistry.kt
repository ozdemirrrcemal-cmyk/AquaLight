package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibration
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationRequest
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationStatus

/**
 * One physically measured production fixture profile.
 *
 * The geometry domain and measurement provenance are part of the calibration identity. Requests
 * outside the measured domain fail closed instead of extrapolating unverified optical output.
 */
internal data class DeviceLightMeasuredCalibrationProfile(
    val productKey: String,
    val calibrationRevision: Int,
    val channelKeys: Set<String>,
    val waterHeightCmRange: IntRange,
    val fixtureHeightAboveWaterCmRange: IntRange,
    val tankWidthCmRange: IntRange,
    val tankLengthCmRange: IntRange,
    val measurementSetId: String,
    val measurementDataSha256: String,
    val horizontalMeasurementPointCount: Int,
    val measuredPpfdSampleCount: Int,
    val coverageModelRevision: Int,
    private val measuredCalibration: DeviceLightFixtureCalibration
) : DeviceLightFixtureCalibration {
    init {
        require(productKey.isNotBlank())
        require(calibrationRevision > 0)
        require(channelKeys.isNotEmpty())
        require(channelKeys.all { key -> CHANNEL_KEY.matches(key) })
        require(!waterHeightCmRange.isEmpty())
        require(!fixtureHeightAboveWaterCmRange.isEmpty())
        require(!tankWidthCmRange.isEmpty())
        require(!tankLengthCmRange.isEmpty())
        require(measurementSetId.isNotBlank())
        require(SHA256.matches(measurementDataSha256))
        require(horizontalMeasurementPointCount >= MIN_HORIZONTAL_POINTS)
        require(measuredPpfdSampleCount >= horizontalMeasurementPointCount)
        require(coverageModelRevision > 0)
    }

    @Suppress("ReturnCount")
    override fun solve(
        request: DeviceLightFixtureCalibrationRequest
    ): DeviceLightFixtureCalibrationResult? {
        if (request.productKey != productKey) return null
        if (request.channelKeys.toSet() != channelKeys) return null
        if (request.waterHeightCm !in waterHeightCmRange) return null
        if (request.fixtureHeightAboveWaterCm !in fixtureHeightAboveWaterCmRange) return null
        if (request.tankWidthCm !in tankWidthCmRange) return null
        if (request.tankLengthCm !in tankLengthCmRange) return null

        val result = measuredCalibration.solve(request) ?: return null
        require(result.status == DeviceLightFixtureCalibrationStatus.CALIBRATED)
        require(result.calibrationRevision == calibrationRevision)
        require(result.channelScenePercent.keys == channelKeys)
        return result
    }

    private companion object {
        const val MIN_HORIZONTAL_POINTS = 3
        val CHANNEL_KEY = Regex("^[a-z][a-z0-9]*$")
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * Production calibration registry.
 *
 * This list remains empty until physical AquaLight PAR/coverage measurements are reviewed and
 * committed. The production release guard requires an exact record for every catalog product that
 * advertises LIGHT_QUICK_SETUP, so an empty registry is intentionally not releasable.
 */
internal object DeviceLightProductionCalibrationRegistry {
    val profiles: List<DeviceLightMeasuredCalibrationProfile> = emptyList()

    val calibrations: Map<String, DeviceLightFixtureCalibration> =
        profiles.associateBy(DeviceLightMeasuredCalibrationProfile::productKey)

    init {
        require(profiles.map { profile -> profile.productKey }.distinct().size == profiles.size)
    }
}
