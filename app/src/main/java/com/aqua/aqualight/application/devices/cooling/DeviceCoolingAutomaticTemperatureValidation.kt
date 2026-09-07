package com.aqua.aqualight.application.devices.cooling

/** Pure validation for automatic temperature edits using the authoritative device policy. */
object DeviceCoolingAutomaticTemperatureValidation {
    fun isValidStartTemperature(
        value: Double,
        policy: DeviceCoolingAutomaticTemperaturePolicy,
        maximumSpeedTemperatureC: Double
    ): Boolean = value.isFinite() &&
        value in policy.startMinimumC..policy.startMaximumC &&
        maximumSpeedTemperatureC - value >=
        policy.minimumGapC - TEMPERATURE_EPSILON

    fun isValidMaximumSpeedTemperature(
        value: Double,
        policy: DeviceCoolingAutomaticTemperaturePolicy,
        startTemperatureC: Double
    ): Boolean = value.isFinite() &&
        value in policy.maximumSpeedMinimumC..policy.maximumSpeedMaximumC &&
        value - startTemperatureC >= policy.minimumGapC - TEMPERATURE_EPSILON

    private const val TEMPERATURE_EPSILON = 0.000_001
}
