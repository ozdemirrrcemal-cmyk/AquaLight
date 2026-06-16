package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.catalog.light.LightDeviceDefinition

/**
 * Controls how editor transition intent is converted into controller-safe LP points.
 *
 * Current ESP32 firmware has no native transition field. Smooth/Natural therefore
 * remain user-facing editor choices, but they are uploaded as a small, adaptive set
 * of concrete points that the firmware can linearly interpolate. The device limit
 * is treated as a hard safety ceiling, not as a target to fill on every ramp.
 */
data class LightProgramPointExpansionOptions(
    val strategy: LightProgramDeviceTransitionStrategy = LightProgramDeviceTransitionStrategy.EXPANDED_POINTS,
    val smoothPointSpacingMinutes: Int = DEFAULT_SMOOTH_POINT_SPACING_MINUTES,
    val naturalPointSpacingMinutes: Int = DEFAULT_NATURAL_POINT_SPACING_MINUTES,
    val maximumSmoothIntermediatePointsPerRamp: Int = DEFAULT_MAXIMUM_SMOOTH_INTERMEDIATE_POINTS_PER_RAMP,
    val maximumNaturalIntermediatePointsPerRamp: Int = DEFAULT_MAXIMUM_NATURAL_INTERMEDIATE_POINTS_PER_RAMP,
    val maximumPointsPerChannel: Int = DEFAULT_MAXIMUM_POINTS_PER_CHANNEL
) {
    init {
        require(smoothPointSpacingMinutes > 0) {
            "smoothPointSpacingMinutes must be positive."
        }
        require(naturalPointSpacingMinutes > 0) {
            "naturalPointSpacingMinutes must be positive."
        }
        require(maximumSmoothIntermediatePointsPerRamp >= 0) {
            "maximumSmoothIntermediatePointsPerRamp must be zero or positive."
        }
        require(maximumNaturalIntermediatePointsPerRamp >= 0) {
            "maximumNaturalIntermediatePointsPerRamp must be zero or positive."
        }
        require(maximumPointsPerChannel >= MINIMUM_ANCHOR_POINTS_PER_CHANNEL) {
            "maximumPointsPerChannel must leave room for the four editor anchors."
        }
    }

    companion object {
        /**
         * Smooth is intentionally sparse: it should soften the ramp without turning
         * the graph into a sample cloud. A two-hour ramp produces three generated
         * points per side.
         */
        const val DEFAULT_SMOOTH_POINT_SPACING_MINUTES = 40

        /**
         * Natural uses slightly denser points than Smooth so sunrise/sunset has a
         * visibly different shape, while still staying clean on compact graphs.
         */
        const val DEFAULT_NATURAL_POINT_SPACING_MINUTES = 28

        const val DEFAULT_MAXIMUM_SMOOTH_INTERMEDIATE_POINTS_PER_RAMP = 3
        const val DEFAULT_MAXIMUM_NATURAL_INTERMEDIATE_POINTS_PER_RAMP = 4
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
