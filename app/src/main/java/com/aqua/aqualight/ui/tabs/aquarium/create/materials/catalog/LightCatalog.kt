package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object LightCatalog {

    private const val CATEGORY_TITLE = "Light"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "light_chihiros_wrgb_ii",
            brand = "Chihiros",
            name = "Chihiros WRGB II",
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("light", "led", "rgb", "wrgb", "chihiros")
        ),
        AquariumMaterial(
            id = "light_chihiros_wrgb_ii_slim",
            brand = "Chihiros",
            name = "Chihiros WRGB II Slim",
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("light", "led", "rgb", "slim", "chihiros")
        ),
        AquariumMaterial(
            id = "light_chihiros_magnetic_lamp_led",
            brand = "Chihiros",
            name = "Chihiros Magnetic Lamp - LED Light",
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("light", "led", "magnetic", "lamp", "chihiros")
        ),
        AquariumMaterial(
            id = "light_twinstar_s_series",
            brand = "Twinstar",
            name = "Twinstar S Series",
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("light", "led", "rgb", "twinstar")
        )
    )
}