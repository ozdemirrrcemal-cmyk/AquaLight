package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

data class ManualLightPreset(
    val id: String,
    val name: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int,
    val createdAtMillis: Long
)