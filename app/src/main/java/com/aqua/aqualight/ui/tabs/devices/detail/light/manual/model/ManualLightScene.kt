package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.catalog.LightBuiltInPreset
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.catalog.LightBuiltInPresetCatalog

enum class ManualLightScene(
    private val preset: LightBuiltInPreset
) {
    PLANT_GROWTH(
        LightBuiltInPresetCatalog.PLANT_GROWTH
    ),

    FISH_DISPLAY(
        LightBuiltInPresetCatalog.FISH_DISPLAY
    ),

    SHRIMP_SAFE(
        LightBuiltInPresetCatalog.SHRIMP_SAFE
    ),

    BLUE_ACCENT(
        LightBuiltInPresetCatalog.BLUE_ACCENT
    ),

    RED_ACCENT(
        LightBuiltInPresetCatalog.RED_PLANT
    ),

    FULL_SPECTRUM(
        LightBuiltInPresetCatalog.FULL_SPECTRUM
    );

    val title: String
        get() = preset.manualTitle

    val red: Int
        get() = preset.red

    val green: Int
        get() = preset.green

    val blue: Int
        get() = preset.blue

    val white: Int
        get() = preset.white
}
