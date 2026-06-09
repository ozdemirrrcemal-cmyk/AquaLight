package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model

import com.aqua.aqualight.data.devices.light.runtime.LightOutputMath

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

    val outputPercent: Int
        get() =
            LightOutputMath.outputPercent(
                red = red,
                green = green,
                blue = blue,
                white = white
            )
}