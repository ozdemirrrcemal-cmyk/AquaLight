package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

data class LightProgramListItem(
    val id: String,
    val title: String,
    val subtitle: String,

    val startTime: String,
    val sunriseEndTime: String,
    val peakEndTime: String,
    val endTime: String,

    val rampLabel: String,
    val repeatLabel: String,

    val peakPercent: Int,
    val photoperiodLabel: String,

    val redPercent: Int,
    val greenPercent: Int,
    val bluePercent: Int,
    val whitePercent: Int,

    val startIntensity: Int,
    val sunriseEndIntensity: Int,
    val peakEndIntensity: Int,
    val endIntensity: Int,

    val isEnabled: Boolean
) {

    val scheduleSummary: String
        get() = "$startTime → $endTime · $repeatLabel"

    val peakLabel: String
        get() = "Peak $peakPercent · "

    val redLabel: String
        get() = "R$redPercent "

    val greenLabel: String
        get() = "G$greenPercent "

    val blueLabel: String
        get() = "B$bluePercent "

    val whiteLabel: String
        get() = "W$whitePercent"
}