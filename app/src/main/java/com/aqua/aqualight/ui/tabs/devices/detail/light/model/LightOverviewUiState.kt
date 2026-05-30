package com.aqua.aqualight.ui.tabs.devices.detail.light.model

data class LightOverviewUiState(
    val connectionLabel: String = "Connecting · WRGB",

    val programTitle: String = "Current Program",
    val programSubtitle: String = "Waiting for device data",
    val modeLabel: String = "SYNCING",

    val currentOutputLabel: String = NO_VALUE,

    val redLabel: String = NO_VALUE,
    val greenLabel: String = NO_VALUE,
    val blueLabel: String = NO_VALUE,
    val whiteLabel: String = NO_VALUE,

    val nowLabel: String = "Now · $NO_VALUE",
    val nextLabel: String = "Next · $NO_VALUE",

    val curveNowLabel: String = "Waiting for curve data",

    val timelineStartLabel: String = "00:00",
    val timelineMidLabel: String = "12:00",
    val timelineEndLabel: String = "24:00",

    val curveStartLabel: String = NO_VALUE,
    val curvePeakLabel: String = NO_VALUE,
    val curveSunsetLabel: String = NO_VALUE,
    val curveRampLabel: String = NO_VALUE,

    val activeProgramName: String = NO_VALUE,
    val activeProgramSchedule: String = NO_VALUE,
    val activeProgramChannels: String = NO_VALUE,
    val activeProgramStatusLabel: String = "Off",

    val healthLabel: String = "Connecting",
    val temperatureLabel: String = NO_VALUE,
    val fanLabel: String = NO_VALUE,
    val deviceTimeLabel: String = NO_VALUE,
    val firmwareLabel: String = NO_VALUE,

    val isProgramEnabled: Boolean = false
) {
    companion object {
        const val NO_VALUE = "—"
    }
}