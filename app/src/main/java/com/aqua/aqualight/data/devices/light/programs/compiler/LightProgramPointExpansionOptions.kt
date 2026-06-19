package com.aqua.aqualight.data.devices.light.programs.compiler

data class LightProgramPointExpansionOptions(
    val strategy: LightProgramDeviceTransitionStrategy = LightProgramDeviceTransitionStrategy.EXPANDED_POINTS,
    val rampStepMinutes: Int = DEFAULT_RAMP_STEP_MINUTES,
    val minimumRampPoints: Int = DEFAULT_MINIMUM_RAMP_POINTS,
    val maximumRampPoints: Int = DEFAULT_MAXIMUM_RAMP_POINTS
) {
    init {
        require(rampStepMinutes > 0) { "rampStepMinutes must be positive." }
        require(minimumRampPoints > 0) { "minimumRampPoints must be positive." }
        require(maximumRampPoints >= minimumRampPoints) {
            "maximumRampPoints must be greater than or equal to minimumRampPoints."
        }
    }

    companion object {
        const val DEFAULT_RAMP_STEP_MINUTES = 10
        const val DEFAULT_MINIMUM_RAMP_POINTS = 6
        const val DEFAULT_MAXIMUM_RAMP_POINTS = 24
    }
}
