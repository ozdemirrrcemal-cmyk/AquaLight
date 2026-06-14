package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.TodayLightPlanGraphState

data class DeviceLightDashboardUiState(
    val activeProgramName: String = "No active program",
    val runStatus: String = "Create or load a program",

    val liveMode: LightDashboardMode = LightDashboardMode.IDLE,

    val currentWattText: String = "-- W",
    val outputPercentText: String = "0%",

    val redChannelText: String = "R --",
    val greenChannelText: String = "G --",
    val blueChannelText: String = "B --",
    val whiteChannelText: String = "W --",

    val deviceTimeText: String = "--:--",
    val nextEventText: String = "No upcoming event",

    val healthTemperatureText: String = "-- °C",
    val healthTemperatureStatusText: String = "Syncing",
    val healthFanText: String = "Syncing",
    val healthFanStatusText: String = "Syncing",

    val timelineStatusText: String = "No active plan",
    val todayPlanGraphState: TodayLightPlanGraphState =
        TodayLightPlanGraphState.empty(
            LightCurvePoint.of(0, 0)
        ),

    val isDeviceOnline: Boolean = false,
    val controlsEnabled: Boolean = false,
    val connectionStatusText: String = "Checking device connection"
)
