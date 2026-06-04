package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

enum class ManualLightScene(
    val title: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    PLANT_GROWTH(
        title = "Plant",
        red = 90,
        green = 75,
        blue = 45,
        white = 90
    ),

    FISH_DISPLAY(
        title = "Fish",
        red = 100,
        green = 100,
        blue = 100,
        white = 10
    ),

    SHRIMP_SAFE(
        title = "Shrimp",
        red = 35,
        green = 50,
        blue = 20,
        white = 30
    ),

    BLUE_ACCENT(
        title = "Blue",
        red = 5,
        green = 5,
        blue = 100,
        white = 20
    ),

    RED_ACCENT(
        title = "Red",
        red = 100,
        green = 40,
        blue = 70,
        white = 35
    ),

    FULL_SPECTRUM(
        title = "Full",
        red = 100,
        green = 100,
        blue = 100,
        white = 100
    )
}