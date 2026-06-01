package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.model

data class ManualLightOutput(
    val masterPercent: Int,
    val redPercent: Int,
    val greenPercent: Int,
    val bluePercent: Int,
    val whitePercent: Int
)