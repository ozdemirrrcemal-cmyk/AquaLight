package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

import java.io.Serializable

data class LightQuickSetupDraft(
    val sunriseStartMinutes: Int = DEFAULT_SUNRISE_START_MINUTES,
    val sunsetEndMinutes: Int = DEFAULT_SUNSET_END_MINUTES,
    val rampMinutes: Int = DEFAULT_RAMP_MINUTES,
    val peakIntensityPercent: Int = DEFAULT_PEAK_INTENSITY,
    val balancePreset: QuickSetupChannelBalancePreset = QuickSetupChannelBalancePreset.NATURAL,
    val selectedDays: Set<Int> = LightQuickSetupDays.all
) : Serializable {

    val peakStartMinutes: Int
        get() = sunriseStartMinutes + rampMinutes

    val peakEndMinutes: Int
        get() = sunsetEndMinutes - rampMinutes

    companion object {
        private const val DEFAULT_SUNRISE_START_MINUTES = 9 * 60
        private const val DEFAULT_SUNSET_END_MINUTES = (19 * 60) + 15
        private const val DEFAULT_RAMP_MINUTES = 60
        private const val DEFAULT_PEAK_INTENSITY = 100
    }
}