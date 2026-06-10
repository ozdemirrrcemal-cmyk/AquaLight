package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model

data class LightPresetItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: LightPresetCategory,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    val isCustom: Boolean
        get() = category == LightPresetCategory.CUSTOM
}