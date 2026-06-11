package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model

data class SavedLightPreset(
    val id: String,
    val name: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toListItem(): LightPresetItem {
        return LightPresetItem(
            id = id,
            title = name,
            subtitle = "Custom saved preset",
            category = LightPresetCategory.CUSTOM,
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }
}