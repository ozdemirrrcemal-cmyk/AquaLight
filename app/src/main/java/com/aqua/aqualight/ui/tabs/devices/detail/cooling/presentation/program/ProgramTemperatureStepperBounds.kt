package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

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
    val policy = state.policy?.fanOnTemperature
    val slot = state.slotItems.getOrNull(slotIndex)?.slot
    return if (policy != null && slot != null) {
        val minimum = policy.minimumC.toProgramDisplayUnits()
        val maximum = policy.maximumC.toProgramDisplayUnits()
        ProgramTemperatureStepperBounds(
            initialValue = slot.fanOnTemperatureC.toProgramDisplayUnits().coerceIn(minimum, maximum),
            minimumValue = minimum,
            maximumValue = maximum,
            step = policy.stepC.toProgramDisplayUnits().coerceAtLeast(1)
        )
    } else {
        null
    }
}

private fun Double.toProgramDisplayUnits(): Int =
    (this / PROGRAM_TEMPERATURE_DISPLAY_SCALE).roundToInt()

internal const val PROGRAM_TEMPERATURE_DISPLAY_SCALE = 0.1
