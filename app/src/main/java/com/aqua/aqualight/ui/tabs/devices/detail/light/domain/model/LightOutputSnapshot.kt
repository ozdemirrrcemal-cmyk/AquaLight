package com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model

data class LightOutputSnapshot(
    val masterPercent: Int?,
    val redPercent: Int?,
    val greenPercent: Int?,
    val bluePercent: Int?,
    val whitePercent: Int?
)