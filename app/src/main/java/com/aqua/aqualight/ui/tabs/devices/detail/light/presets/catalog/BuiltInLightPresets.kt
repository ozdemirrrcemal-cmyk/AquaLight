package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.catalog

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.catalog.LightBuiltInPreset
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.catalog.LightBuiltInPresetCatalog
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetCategory
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem

object BuiltInLightPresets {

    val presets: List<LightPresetItem> =
        LightBuiltInPresetCatalog.presets.map { preset ->
            preset.toPresetItem()
        }

    private fun LightBuiltInPreset.toPresetItem(): LightPresetItem {
        return LightPresetItem(
            id = id,
            title = title,
            subtitle = subtitle,
            category = LightPresetCategory.BUILT_IN,
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }
}
