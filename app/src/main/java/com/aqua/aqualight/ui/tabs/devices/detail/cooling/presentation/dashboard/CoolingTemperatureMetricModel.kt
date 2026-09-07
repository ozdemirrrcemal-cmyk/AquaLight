package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardIconKind

internal data class CoolingTemperatureMetricModel(
    val icon: AquaCoolingDashboardIconKind,
    val label: String,
    val value: String,
    val accent: Boolean
)
