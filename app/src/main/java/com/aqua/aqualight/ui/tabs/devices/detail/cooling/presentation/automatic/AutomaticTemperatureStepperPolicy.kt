package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal fun startTemperatureStepperBounds(
    state: DeviceCoolingAutomaticSettingsUiState
): AutomaticTemperatureStepperBounds? = state.editorPolicy?.let { policy ->
    val current = state.editorStartTemperatureC
    val maximum = state.editorMaximumSpeedTemperatureC
    if (!state.editable || current == null || maximum == null) {
        null
    } else {
        val minimumUnits = toDisplayUnits(policy.startMinimumC)
        val maximumUnits = floorToStepUnits(
            value = minOf(policy.startMaximumC, maximum - policy.minimumGapC),
            origin = policy.startMinimumC,
            step = policy.stepC
        )
        AutomaticTemperatureStepperBounds(
            initialValue = snapToStepUnits(
                value = current,
                minimum = policy.startMinimumC,
                step = policy.stepC
            ).coerceIn(minimumUnits, maximumUnits.coerceAtLeast(minimumUnits)),
            minimumValue = minimumUnits,
            maximumValue = maximumUnits,
            step = toDisplayUnits(policy.stepC).coerceAtLeast(1)
        ).takeIf { bounds -> bounds.maximumValue >= bounds.minimumValue }
    }
}

internal fun maximumTemperatureStepperBounds(
    state: DeviceCoolingAutomaticSettingsUiState
): AutomaticTemperatureStepperBounds? = state.editorPolicy?.let { policy ->
    val current = state.editorMaximumSpeedTemperatureC
    val start = state.editorStartTemperatureC
    if (!state.editable || current == null || start == null) {
        null
    } else {
        val minimumUnits = ceilToStepUnits(
            value = maxOf(policy.maximumSpeedMinimumC, start + policy.minimumGapC),
            origin = policy.maximumSpeedMinimumC,
            step = policy.stepC
        )
        val maximumUnits = toDisplayUnits(policy.maximumSpeedMaximumC)
        AutomaticTemperatureStepperBounds(
            initialValue = snapToStepUnits(
                value = current,
                minimum = policy.maximumSpeedMinimumC,
                step = policy.stepC
            ).coerceIn(minimumUnits.coerceAtMost(maximumUnits), maximumUnits),
            minimumValue = minimumUnits,
            maximumValue = maximumUnits,
            step = toDisplayUnits(policy.stepC).coerceAtLeast(1)
        ).takeIf { bounds -> bounds.minimumValue <= bounds.maximumValue }
    }
}

private fun toDisplayUnits(value: Double): Int =
    (value / AUTOMATIC_TEMPERATURE_DISPLAY_SCALE).roundToInt()

private fun snapToStepUnits(value: Double, minimum: Double, step: Double): Int {
    val steps = ((value - minimum) / step).roundToInt()
    return toDisplayUnits(minimum + steps * step)
}

private fun floorToStepUnits(value: Double, origin: Double, step: Double): Int {
    val steps = floor((value - origin) / step).toInt()
    return toDisplayUnits(origin + steps * step)
}

private fun ceilToStepUnits(value: Double, origin: Double, step: Double): Int {
    val steps = ceil((value - origin) / step).toInt()
    return toDisplayUnits(origin + steps * step)
}

internal const val AUTOMATIC_TEMPERATURE_DISPLAY_SCALE = 0.1
