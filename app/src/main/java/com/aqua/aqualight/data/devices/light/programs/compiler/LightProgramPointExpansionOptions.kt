package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.catalog.light.LightDeviceDefinition

/**
 * Controls how editor transition intent is converted into controller-safe LP points.
 *
 * Current ESP32 firmware has no native transition field. Smooth/Natural therefore
 * remain user-facing editor choices, but they are uploaded as bounded concrete
 * points that the firmware can linearly interpolate.
 */
data class LightProgramPointExpansionOptions(
    val strategy: LightProgramDeviceTransitionStrategy = LightProgramDeviceTransitionStrategy.EXPANDED_POINTS,
    val rampStepMinutes: Int = DEFAULT_RAMP_STEP_MINUTES,
    val minimumIntermediatePointsPerRamp: Int = DEFAULT_MINIMUM_INTERMEDIATE_POINTS_PER_RAMP,
    val maximumPointsPerChannel: Int = DEFAULT_MAXIMUM_POINTS_PER_CHANNEL
) {
    init {
        require(rampStepMinutes > 0) { "rampStepMinutes must be positive." }
        require(minimumIntermediatePointsPerRamp >= 0) {
            "minimumIntermediatePointsPerRamp must be zero or positive."
        }
        require(maximumPointsPerChannel >= MINIMUM_ANCHOR_POINTS_PER_CHANNEL) {
            "maximumPointsPerChannel must leave room for the four editor anchors."
        }
    }

    companion object {
        const val DEFAULT_RAMP_STEP_MINUTES = 10
        const val DEFAULT_MINIMUM_INTERMEDIATE_POINTS_PER_RAMP = 4
        const val MINIMUM_ANCHOR_POINTS_PER_CHANNEL = 4
        const val DEFAULT_MAXIMUM_POINTS_PER_CHANNEL = 24

        fun forDeviceDefinition(
            definition: LightDeviceDefinition
        ): LightProgramPointExpansionOptions {
            return LightProgramPointExpansionOptions(
                maximumPointsPerChannel = definition.maxSchedulePointCount
            )
        }
    }
}
