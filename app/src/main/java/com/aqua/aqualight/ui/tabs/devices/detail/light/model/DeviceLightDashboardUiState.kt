package com.aqua.aqualight.ui.tabs.devices.detail.light.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.TodayLightPlanGraphState

data class DeviceLightDashboardUiState(
    val activeProgramName: String = "No active program",
    val runStatus: String = "Create or load a program",
    val onlineStatusText: String = "ONLINE",

    val currentWattText: String = "-- W",
    val outputPercentText: String = "0%",

    val deviceTimeText: String = "--:--",
    val nextEventText: String = "No upcoming event",

    val timelineStatusText: String = "No active plan",
    val todayPlanGraphState: TodayLightPlanGraphState =
        TodayLightPlanGraphState.empty(
            LightCurvePoint.of(0, 0)
        )
)