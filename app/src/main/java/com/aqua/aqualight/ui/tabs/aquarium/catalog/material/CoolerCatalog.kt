package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R


object CoolerCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "cooler_aquael_fan_mini",
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_cooler_aquael_fan_mini_name,
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitleRes = R.string.catalog_material_category_cooler_title,
            keywordRes = listOf(
                R.string.catalog_keyword_cooler,
                R.string.catalog_keyword_fan,
                R.string.catalog_keyword_temperature,
                R.string.catalog_keyword_aquael
            )
        ),
        AquariumMaterialDefinition(
            id = "cooler_chihiros_cooling_fan",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_cooler_chihiros_cooling_fan_name,
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitleRes = R.string.catalog_material_category_cooler_title,
            keywordRes = listOf(
                R.string.catalog_keyword_cooler,
                R.string.catalog_keyword_fan,
                R.string.catalog_keyword_temperature,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "cooler_ista_cooling_fan",
            brandRes = R.string.catalog_brand_ista,
            nameRes = R.string.catalog_material_cooler_ista_cooling_fan_name,
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitleRes = R.string.catalog_material_category_cooler_title,
            keywordRes = listOf(
                R.string.catalog_keyword_cooler,
                R.string.catalog_keyword_fan,
                R.string.catalog_keyword_temperature,
                R.string.catalog_keyword_ista
            )
        ),
        AquariumMaterialDefinition(
            id = "cooler_jbl_cooler_100",
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_cooler_jbl_cooler_100_name,
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitleRes = R.string.catalog_material_category_cooler_title,
            keywordRes = listOf(
                R.string.catalog_keyword_cooler,
                R.string.catalog_keyword_fan,
                R.string.catalog_keyword_temperature,
                R.string.catalog_keyword_jbl
            )
        ),
        AquariumMaterialDefinition(
            id = "cooler_jbl_cooler_200",
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_cooler_jbl_cooler_200_name,
            categoryKey = MaterialCategoryKey.COOLER,
            categoryTitleRes = R.string.catalog_material_category_cooler_title,
            keywordRes = listOf(
                R.string.catalog_keyword_cooler,
                R.string.catalog_keyword_fan,
                R.string.catalog_keyword_temperature,
                R.string.catalog_keyword_jbl
            )
        )
    )
}
