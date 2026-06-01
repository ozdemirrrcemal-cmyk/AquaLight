package com.aqua.aqualight.ui.tabs.devices.detail.light.data.remote

data class LightOverviewRemoteResponse(
    val programId: String?,
    val programName: String?,
    val mode: String?,
    val enabled: Boolean?,
    val scheduleLabel: String?,
    val channelsLabel: String?,
    val nowLabel: String?,
    val nextLabel: String?,
    val syncLabel: String?,
    val temperatureLabel: String?,
    val fanLabel: String?,
    val deviceTimeLabel: String?,
    val firmwareLabel: String?,
    val masterPercent: Int?,
    val redPercent: Int?,
    val greenPercent: Int?,
    val bluePercent: Int?,
    val whitePercent: Int?
)