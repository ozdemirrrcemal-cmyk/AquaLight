package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

/** Validated display-unit contract for an automatic-temperature stepper. */
internal data class AutomaticTemperatureStepperBounds(
    val initialValue: Int,
    val minimumValue: Int,
    val maximumValue: Int,
    val step: Int
)
