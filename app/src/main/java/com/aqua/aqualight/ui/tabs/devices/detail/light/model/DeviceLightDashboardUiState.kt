package com.aqua.aqualight.ui.tabs.devices.detail.light.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState

data class DeviceLightDashboardUiState(
    val activeProgramName: String = "No active program",
    val runStatus: String = "Create or load a program",
    val onlineStatusText: String = "ONLINE",

    val currentWattText: String = "-- W",
    val outputPercentText: String = "0%",

    val deviceTimeText: String = "--:--",
    val nextEventText: String = "No upcoming event",

    val timelineStatusText: String = "No active program",
    val graphState: LightCurveGraphState
)