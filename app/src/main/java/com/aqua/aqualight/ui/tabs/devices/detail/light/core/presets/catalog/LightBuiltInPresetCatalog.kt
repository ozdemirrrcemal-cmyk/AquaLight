package com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.catalog

import com.aqua.aqualight.data.devices.light.model.LightRgbwChannels

object LightBuiltInPresetCatalog {

    val PLANT_GROWTH = LightBuiltInPreset(
        id = "built_in_plant_growth",
        title = "Plant Growth",
        manualTitle = "Plant",
        subtitle = "Balanced spectrum for planted tanks",
        channels = LightRgbwChannels(
            red = 90,
            green = 75,
            blue = 45,
            white = 90
        )
    )

    val NATURE_DAY = LightBuiltInPreset(
        id = "built_in_nature_day",
        title = "Nature Day",
        manualTitle = "Nature",
        subtitle = "Natural daylight look for daily viewing",
        channels = LightRgbwChannels(
            red = 75,
            green = 85,
            blue = 70,
            white = 80
        )
    )

    val FISH_DISPLAY = LightBuiltInPreset(
        id = "built_in_fish_display",
        title = "Fish Display",
        manualTitle = "Fish",
        subtitle = "Enhances fish color and contrast",
        channels = LightRgbwChannels(
            red = 100,
            green = 100,
            blue = 100,
            white = 10
        )
    )

    val SHRIMP_SAFE = LightBuiltInPreset(
        id = "built_in_shrimp_safe",
        title = "Shrimp Safe",
        manualTitle = "Shrimp",
        subtitle = "Soft low-stress viewing profile",
        channels = LightRgbwChannels(
            red = 35,
            green = 50,
            blue = 20,
            white = 30
        )
    )

    val BLUE_ACCENT = LightBuiltInPreset(
        id = "built_in_blue_accent",
        title = "Blue Accent",
        manualTitle = "Blue",
        subtitle = "Cool blue tone for vivid highlights",
        channels = LightRgbwChannels(
            red = 5,
            green = 5,
            blue = 100,
            white = 20
        )
    )

    val RED_PLANT = LightBuiltInPreset(
        id = "built_in_red_plant",
        title = "Red Plant",
        manualTitle = "Red",
        subtitle = "Enhances red stem plants",
        channels = LightRgbwChannels(
            red = 100,
            green = 40,
            blue = 70,
            white = 35
        )
    )

    val MOONLIGHT = LightBuiltInPreset(
        id = "built_in_moonlight",
        title = "Moonlight",
        manualTitle = "Moon",
        subtitle = "Very low blue night viewing",
        channels = LightRgbwChannels(
            red = 0,
            green = 0,
            blue = 12,
            white = 4
        )
    )

    val PHOTO_MODE = LightBuiltInPreset(
        id = "built_in_photo_mode",
        title = "Photo Mode",
        manualTitle = "Photo",
        subtitle = "Bright neutral light for photos",
        channels = LightRgbwChannels(
            red = 85,
            green = 85,
            blue = 85,
            white = 100
        )
    )

    val FULL_SPECTRUM = LightBuiltInPreset(
        id = "built_in_full_spectrum",
        title = "Full Spectrum",
        manualTitle = "Full",
        subtitle = "Maximum balanced WRGB output",
        channels = LightRgbwChannels(
            red = 100,
            green = 100,
            blue = 100,
            white = 100
        )
    )

    val LOW_ALGAE = LightBuiltInPreset(
        id = "built_in_low_algae",
        title = "Low Algae",
        manualTitle = "Low",
        subtitle = "Reduced intensity for easier control",
        channels = LightRgbwChannels(
            red = 35,
            green = 55,
            blue = 35,
            white = 45
        )
    )

    val presets: List<LightBuiltInPreset> = listOf(
        PLANT_GROWTH,
        NATURE_DAY,
        FISH_DISPLAY,
        SHRIMP_SAFE,
        BLUE_ACCENT,
        RED_PLANT,
        MOONLIGHT,
        PHOTO_MODE,
        FULL_SPECTRUM,
        LOW_ALGAE
    )
}
