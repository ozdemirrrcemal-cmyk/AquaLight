package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues

data class QuickSetupLightProfile(
    val title: String,
    val profileLabel: String,
    val goalLabel: String,
    val baseDurationMinutes: Int,
    val baseChannels: LightCurveChannelValues,
    val baseIntensityMultiplier: Double
)