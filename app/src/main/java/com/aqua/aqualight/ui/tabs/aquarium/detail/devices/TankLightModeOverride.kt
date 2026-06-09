package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

data class TankLightModeOverride(
    val mode: TankLightCardMode,
    val title: String = "",
    val outputPercent: Int? = null,
    val red: Int? = null,
    val green: Int? = null,
    val blue: Int? = null,
    val white: Int? = null,
    val leftText: String? = null,
    val rightText: String? = null,
    val timelineProgressPercent: Int? = null
)