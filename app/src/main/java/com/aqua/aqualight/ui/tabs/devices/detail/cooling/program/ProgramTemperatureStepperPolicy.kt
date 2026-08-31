package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

import kotlin.math.roundToInt

internal data class ProgramTemperatureStepperBounds(
    val initialValue: Int,
    val minimumValue: Int,
    val maximumValue: Int,
    val step: Int
)

internal fun fanOnTemperatureStepperBounds(
    state: DeviceCoolingProgramSettingsUiState,
    slotIndex: Int
): ProgramTemperatureStepperBounds? {
    val policy = state.policy?.fanOnTemperature ?: return null
    val slot = state.slotItems.getOrNull(slotIndex)?.slot ?: return null
    val minimum = policy.minimumC.toProgramDisplayUnits()
    val maximum = policy.maximumC.toProgramDisplayUnits()
    return ProgramTemperatureStepperBounds(
        initialValue = slot.fanOnTemperatureC.toProgramDisplayUnits().coerceIn(minimum, maximum),
        minimumValue = minimum,
        maximumValue = maximum,
        step = policy.stepC.toProgramDisplayUnits().coerceAtLeast(1)
    )
}

private fun Double.toProgramDisplayUnits(): Int =
    (this / PROGRAM_TEMPERATURE_DISPLAY_SCALE).roundToInt()

internal const val PROGRAM_TEMPERATURE_DISPLAY_SCALE = 0.1
