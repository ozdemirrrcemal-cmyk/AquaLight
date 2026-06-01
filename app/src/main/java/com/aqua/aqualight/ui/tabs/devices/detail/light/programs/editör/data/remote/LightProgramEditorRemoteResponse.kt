package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote

data class LightProgramEditorRemoteResponse(
    val programId: String?,
    val programName: String?,
    val mode: String?,
    val repeatDays: List<Int>?,
    val rampSmoothing: String?,
    val simpleCurve: RemoteCurve?,
    val proCurves: List<RemoteCurve>?,
    val channelBalance: RemoteChannelBalance?,
    val acclimation: RemoteAcclimation?
)

data class RemoteCurve(
    val channel: String?,
    val points: List<RemoteCurvePoint>?
)

data class RemoteCurvePoint(
    val id: String?,
    val role: String?,
    val label: String?,
    val minuteOfDay: Int?,
    val intensityPercent: Int?,
    val canRename: Boolean?,
    val canDelete: Boolean?
)

data class RemoteChannelBalance(
    val redPercent: Int?,
    val greenPercent: Int?,
    val bluePercent: Int?,
    val whitePercent: Int?
)

data class RemoteAcclimation(
    val enabled: Boolean?,
    val durationDays: Int?,
    val startIntensityPercent: Int?
)