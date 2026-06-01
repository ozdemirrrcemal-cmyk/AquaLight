package com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model

data class LightOverview(
    val program: LightProgramSummary?,
    val health: LightDeviceHealth?,
    val output: LightOutputSnapshot?
)