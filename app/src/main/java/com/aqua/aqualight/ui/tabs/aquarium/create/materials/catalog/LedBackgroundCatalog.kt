package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object LedBackgroundCatalog {

    private const val CATEGORY_TITLE = "LED Background"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "led_background_chihiros_vivid_background",
            brand = "Chihiros",
            name = "Chihiros Vivid Background",
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("led", "background", "light", "chihiros")
        ),
        AquariumMaterial(
            id = "led_background_chihiros_shades",
            brand = "Chihiros",
            name = "Chihiros LED Background Shades",
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("led", "background", "shade", "chihiros")
        ),
        AquariumMaterial(
            id = "led_background_twinstar_light_screen",
            brand = "Twinstar",
            name = "Twinstar Light Screen",
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("led", "background", "screen", "twinstar")
        ),
        AquariumMaterial(
            id = "led_background_week_aqua_led_screen",
            brand = "Week Aqua",
            name = "Week Aqua LED Background Screen",
            categoryKey = MaterialCategoryKey.LED_BACKGROUND,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("led", "background", "screen", "week aqua")
        )
    )
}