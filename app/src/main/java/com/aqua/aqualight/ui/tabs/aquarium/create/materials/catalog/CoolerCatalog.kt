package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object CoolerCatalog {

    private const val CATEGORY_TITLE = "Cooler"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "cooler_aquael_fan_mini",
            brand = "Aquael",
            name = "Aquael Fan Mini",
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("cooler", "fan", "temperature", "aquael")
        ),
        AquariumMaterial(
            id = "cooler_chihiros_cooling_fan",
            brand = "Chihiros",
            name = "Chihiros Cooling Fan",
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("cooler", "fan", "temperature", "chihiros")
        ),
        AquariumMaterial(
            id = "cooler_ista_cooling_fan",
            brand = "ISTA",
            name = "ISTA Cooling Fan",
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("cooler", "fan", "temperature", "ista")
        ),
        AquariumMaterial(
            id = "cooler_jbl_cooler_100",
            brand = "JBL",
            name = "JBL Cooler 100",
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("cooler", "fan", "temperature", "jbl")
        ),
        AquariumMaterial(
            id = "cooler_jbl_cooler_200",
            brand = "JBL",
            name = "JBL Cooler 200",
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("cooler", "fan", "temperature", "jbl")
        )
    )
}