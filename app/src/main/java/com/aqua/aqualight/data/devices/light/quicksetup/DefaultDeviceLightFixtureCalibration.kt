package com.aqua.aqualight.data.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibration
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationRequest
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibrationStatus
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCoverageStatus

/**
 * Development-only optical placeholder. It never claims measured AquaLight PAR calibration.
 */
internal class DefaultDeviceLightFixtureCalibration(
    private val placeholderEnabled: Boolean
) : DeviceLightFixtureCalibration {

    override fun solve(
        request: DeviceLightFixtureCalibrationRequest
    ): DeviceLightFixtureCalibrationResult? {
        if (!placeholderEnabled) return null
        if (request.tankWidthCm <= 0 || request.tankLengthCm <= 0) return null
        if (request.waterHeightCm <= 0 || request.fixtureHeightAboveWaterCm < 0) return null
        val scene = when (request.productKey) {
            WRGB_PRODUCT -> wrgbScene(request.channelKeys)
            RGB_PRODUCT -> rgbScene(request.channelKeys)
            else -> null
        } ?: return null
        return DeviceLightFixtureCalibrationResult(
            status = DeviceLightFixtureCalibrationStatus.PLACEHOLDER,
            calibrationRevision = PLACEHOLDER_REVISION,
            channelScenePercent = scene,
            estimatedPpfd = request.targetPpfd,
            coverageStatus = DeviceLightFixtureCoverageStatus.COVERED,
            initialStartPercent = PLACEHOLDER_INITIAL_START_PERCENT
        )
    }

    private fun wrgbScene(channelKeys: List<String>): Map<String, Int>? {
        if (channelKeys.toSet() != WRGB_CHANNELS) return null
        return channelKeys.associateWith { channel ->
            when (channel) {
                "red" -> 60
                "green" -> 50
                "blue" -> 65
                "white" -> 55
                else -> return null
            }
        }
    }

    private fun rgbScene(channelKeys: List<String>): Map<String, Int>? {
        if (channelKeys.toSet() != RGB_CHANNELS) return null
        return channelKeys.associateWith { channel ->
            when (channel) {
                "red" -> 60
                "green" -> 50
                "blue" -> 65
                else -> return null
            }
        }
    }

    private companion object {
        const val WRGB_PRODUCT = "LIGHT_WRGB_PRO_ELITE"
        const val RGB_PRODUCT = "LIGHT_RGB_PRO_SLIM"
        const val PLACEHOLDER_REVISION = 1
        const val PLACEHOLDER_INITIAL_START_PERCENT = 50
        val WRGB_CHANNELS = setOf("white", "red", "green", "blue")
        val RGB_CHANNELS = setOf("red", "green", "blue")
    }
}
