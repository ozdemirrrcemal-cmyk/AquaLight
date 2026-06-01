package com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model

data class LightProgramSummary(
    val id: String?,
    val name: String,
    val mode: String,
    val isEnabled: Boolean,
    val scheduleLabel: String,
    val channelsLabel: String,
    val nowLabel: String,
    val nextLabel: String
)