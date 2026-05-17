package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object AquariumCatalog {

    private const val CATEGORY_TITLE = "Aquarium"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "aquarium_chihiros_tiny_terrarium_egg_wabi_kusa",
            brand = "Chihiros",
            name = "Chihiros Tiny Terrarium Egg - Wabi Kusa Set (10W, 550 ml)",
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("aquarium", "tank", "set", "wabi kusa", "chihiros")
        ),
        AquariumMaterial(
            id = "aquarium_chihiros_wabi_kusa_magnetic_light_base_glass_air",
            brand = "Chihiros",
            name = "Chihiros Wabi Kusa Set (Magnetic Light, Base, Glass Air)",
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("aquarium", "tank", "set", "glass", "chihiros")
        ),
        AquariumMaterial(
            id = "aquarium_chihiros_wabi_kusa_magnetic_light_base_glass_pot",
            brand = "Chihiros",
            name = "Chihiros Wabi Kusa Set (Magnetic Light, Base, Glass Pot)",
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("aquarium", "tank", "set", "glass", "chihiros")
        ),
        AquariumMaterial(
            id = "aquarium_chihiros_magnetic_base",
            brand = "Chihiros",
            name = "Chihiros Magnetic Base",
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("aquarium", "base", "magnetic", "chihiros")
        )
    )
}