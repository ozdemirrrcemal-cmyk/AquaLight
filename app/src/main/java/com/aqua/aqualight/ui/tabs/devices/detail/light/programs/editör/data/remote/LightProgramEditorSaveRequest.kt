package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.data.remote

data class LightProgramEditorSaveRequest(
    val programId: String?,
    val programName: String,
    val mode: String,
    val repeatDays: List<Int>,
    val rampSmoothing: String,
    val simpleCurve: RemoteCurve?,
    val proCurves: List<RemoteCurve>,
    val channelBalance: RemoteChannelBalance,
    val acclimation: RemoteAcclimation
)