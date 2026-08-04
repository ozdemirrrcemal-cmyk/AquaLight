package com.aqua.aqualight.ui.tabs.devices.detail.settings

import java.text.NumberFormat
import java.util.Locale

/**
 * Presentation-stage mirror of the WRGB Pro Elite firmware protection contract.
 *
 * Runtime values will replace the preview default when the data stage is connected. The bounds
 * intentionally match the firmware's fail-closed 50–70 °C validation.
 */
internal object LightTemperatureProtectionUiContract {
    const val DEFAULT_THRESHOLD_C = 60.0
    const val MINIMUM_THRESHOLD_C = 50.0
    const val MAXIMUM_THRESHOLD_C = 70.0
    const val THRESHOLD_STEP_C = 1.0

    fun isAllowedThreshold(value: Double): Boolean =
        value.isFinite() && value in MINIMUM_THRESHOLD_C..MAXIMUM_THRESHOLD_C

    fun formatDisplay(value: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(value)
}
