package com.aqua.aqualight.ui.tabs.devices.detail.settings

/** Presentation-stage mirror of the WRGB Pro Elite firmware protection limits. */
internal object LightTemperatureProtectionUiContract {
    const val DEFAULT_THRESHOLD_C = 60
    const val MINIMUM_THRESHOLD_C = 50
    const val MAXIMUM_THRESHOLD_C = 70
    const val THRESHOLD_STEP_C = 1

    fun isAllowedThreshold(value: Int): Boolean =
        value in MINIMUM_THRESHOLD_C..MAXIMUM_THRESHOLD_C
}
