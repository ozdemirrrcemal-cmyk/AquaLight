package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

data class TankLightModeOverride(
    val mode: TankLightCardMode,
    val title: String = "",
    val leftText: String? = null,
    val rightText: String? = null,
    val timelineProgressPercent: Int? = null
)
