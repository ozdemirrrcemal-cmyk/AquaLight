package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

data class LightProgramListItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val isActive: Boolean,
    val startTime: String,
    val endTime: String,
    val rampText: String,
    val pointText: String,
    val peakText: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
)