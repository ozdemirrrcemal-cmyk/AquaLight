package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

data class LightQuickSetupDraft(
    val sunriseStartMinutes: Int = 9 * 60,
    val sunsetEndMinutes: Int = (19 * 60) + 15,
    val rampMinutes: Int = 60,
    val peakIntensityPercent: Int = 100,
    val balancePreset: QuickSetupChannelBalancePreset = QuickSetupChannelBalancePreset.NATURAL,
    val selectedDays: Set<Int> = LightQuickSetupDays.all
)