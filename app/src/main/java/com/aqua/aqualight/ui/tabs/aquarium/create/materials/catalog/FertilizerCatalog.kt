package com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog

import com.aqua.aqualight.ui.tabs.aquarium.create.materials.AquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialCategoryKey

object FertilizerCatalog {

    private const val CATEGORY_TITLE = "Fertilizer"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "fertilizer_green_aqua_carbon_plant_wellness_1000",
            brand = "Green Aqua",
            name = "Green Aqua Carbon Plant Wellness - Fertilizer Pack 1000 ml",
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("fertilizer", "liquid", "plant", "carbon", "green aqua")
        ),
        AquariumMaterial(
            id = "fertilizer_green_aqua_carbon_plant_wellness_500",
            brand = "Green Aqua",
            name = "Green Aqua Carbon Plant Wellness - Fertilizer Pack 500 ml",
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("fertilizer", "liquid", "plant", "carbon", "green aqua")
        ),
        AquariumMaterial(
            id = "fertilizer_green_aqua_carbon_plant_wellness_250",
            brand = "Green Aqua",
            name = "Green Aqua Carbon Plant Wellness - Fertilizer Pack 250 ml",
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("fertilizer", "liquid", "plant", "carbon", "green aqua")
        ),
        AquariumMaterial(
            id = "fertilizer_tropica_specialised_nutrition",
            brand = "Tropica",
            name = "Tropica Specialised Nutrition",
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("fertilizer", "macro", "nitrate", "phosphate", "plant")
        ),
        AquariumMaterial(
            id = "fertilizer_tropica_premium_nutrition",
            brand = "Tropica",
            name = "Tropica Premium Nutrition",
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("fertilizer", "micro", "iron", "plant")
        )
    )
}