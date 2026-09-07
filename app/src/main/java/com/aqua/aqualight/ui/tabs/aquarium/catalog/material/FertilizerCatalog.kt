package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R


object FertilizerCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "fertilizer_green_aqua_carbon_plant_wellness_1000",
            brandRes = R.string.catalog_brand_green_aqua,
            nameRes = R.string.catalog_material_fertilizer_green_aqua_carbon_plant_wellness_1000_name,
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitleRes = R.string.catalog_material_category_fertilizer_title,
            keywordRes = listOf(
                R.string.catalog_keyword_fertilizer,
                R.string.catalog_keyword_liquid,
                R.string.catalog_keyword_plant,
                R.string.catalog_keyword_carbon,
                R.string.catalog_keyword_green_aqua
            )
        ),
        AquariumMaterialDefinition(
            id = "fertilizer_green_aqua_carbon_plant_wellness_500",
            brandRes = R.string.catalog_brand_green_aqua,
            nameRes = R.string.catalog_material_fertilizer_green_aqua_carbon_plant_wellness_500_name,
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitleRes = R.string.catalog_material_category_fertilizer_title,
            keywordRes = listOf(
                R.string.catalog_keyword_fertilizer,
                R.string.catalog_keyword_liquid,
                R.string.catalog_keyword_plant,
                R.string.catalog_keyword_carbon,
                R.string.catalog_keyword_green_aqua
            )
        ),
        AquariumMaterialDefinition(
            id = "fertilizer_green_aqua_carbon_plant_wellness_250",
            brandRes = R.string.catalog_brand_green_aqua,
            nameRes = R.string.catalog_material_fertilizer_green_aqua_carbon_plant_wellness_250_name,
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitleRes = R.string.catalog_material_category_fertilizer_title,
            keywordRes = listOf(
                R.string.catalog_keyword_fertilizer,
                R.string.catalog_keyword_liquid,
                R.string.catalog_keyword_plant,
                R.string.catalog_keyword_carbon,
                R.string.catalog_keyword_green_aqua
            )
        ),
        AquariumMaterialDefinition(
            id = "fertilizer_tropica_specialised_nutrition",
            brandRes = R.string.catalog_brand_tropica,
            nameRes = R.string.catalog_material_fertilizer_tropica_specialised_nutrition_name,
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitleRes = R.string.catalog_material_category_fertilizer_title,
            keywordRes = listOf(
                R.string.catalog_keyword_fertilizer,
                R.string.catalog_keyword_macro,
                R.string.catalog_keyword_nitrate,
                R.string.catalog_keyword_phosphate,
                R.string.catalog_keyword_plant
            )
        ),
        AquariumMaterialDefinition(
            id = "fertilizer_tropica_premium_nutrition",
            brandRes = R.string.catalog_brand_tropica,
            nameRes = R.string.catalog_material_fertilizer_tropica_premium_nutrition_name,
            categoryKey = MaterialCategoryKey.FERTILIZER,
            categoryTitleRes = R.string.catalog_material_category_fertilizer_title,
            keywordRes = listOf(
                R.string.catalog_keyword_fertilizer,
                R.string.catalog_keyword_micro,
                R.string.catalog_keyword_iron,
                R.string.catalog_keyword_plant
            )
        )
    )
}
