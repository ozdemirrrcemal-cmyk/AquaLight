package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.catalog

import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetCategory
import com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model.LightPresetItem

object BuiltInLightPresets {

    val presets: List<LightPresetItem> = listOf(
        LightPresetItem(
            id = "built_in_plant_growth",
            title = "Plant Growth",
            subtitle = "Balanced spectrum for planted tanks",
            category = LightPresetCategory.BUILT_IN,
            red = 90,
            green = 75,
            blue = 45,
            white = 90
        ),
        LightPresetItem(
            id = "built_in_nature_day",
            title = "Nature Day",
            subtitle = "Natural daylight look for daily viewing",
            category = LightPresetCategory.BUILT_IN,
            red = 75,
            green = 85,
            blue = 70,
            white = 80
        ),
        LightPresetItem(
            id = "built_in_fish_display",
            title = "Fish Display",
            subtitle = "Enhances fish color and contrast",
            category = LightPresetCategory.BUILT_IN,
            red = 100,
            green = 100,
            blue = 100,
            white = 10
        ),
        LightPresetItem(
            id = "built_in_shrimp_safe",
            title = "Shrimp Safe",
            subtitle = "Soft low-stress viewing profile",
            category = LightPresetCategory.BUILT_IN,
            red = 35,
            green = 50,
            blue = 20,
            white = 30
        ),
        LightPresetItem(
            id = "built_in_blue_accent",
            title = "Blue Accent",
            subtitle = "Cool blue tone for vivid highlights",
            category = LightPresetCategory.BUILT_IN,
            red = 5,
            green = 5,
            blue = 100,
            white = 20
        ),
        LightPresetItem(
            id = "built_in_red_plant",
            title = "Red Plant",
            subtitle = "Enhances red stem plants",
            category = LightPresetCategory.BUILT_IN,
            red = 100,
            green = 40,
            blue = 70,
            white = 35
        ),
        LightPresetItem(
            id = "built_in_moonlight",
            title = "Moonlight",
            subtitle = "Very low blue night viewing",
            category = LightPresetCategory.BUILT_IN,
            red = 0,
            green = 0,
            blue = 12,
            white = 4
        ),
        LightPresetItem(
            id = "built_in_photo_mode",
            title = "Photo Mode",
            subtitle = "Bright neutral light for photos",
            category = LightPresetCategory.BUILT_IN,
            red = 85,
            green = 85,
            blue = 85,
            white = 100
        ),
        LightPresetItem(
            id = "built_in_full_spectrum",
            title = "Full Spectrum",
            subtitle = "Maximum balanced WRGB output",
            category = LightPresetCategory.BUILT_IN,
            red = 100,
            green = 100,
            blue = 100,
            white = 100
        ),
        LightPresetItem(
            id = "built_in_low_algae",
            title = "Low Algae",
            subtitle = "Reduced intensity for easier control",
            category = LightPresetCategory.BUILT_IN,
            red = 35,
            green = 55,
            blue = 35,
            white = 45
        )
    )
}