package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R


object LightCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "light_chihiros_wrgb_ii",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_light_chihiros_wrgb_ii_name,
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitleRes = R.string.catalog_material_category_light_title,
            keywordRes = listOf(
                R.string.catalog_keyword_light,
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_rgb,
                R.string.catalog_keyword_wrgb,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "light_chihiros_wrgb_ii_slim",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_light_chihiros_wrgb_ii_slim_name,
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitleRes = R.string.catalog_material_category_light_title,
            keywordRes = listOf(
                R.string.catalog_keyword_light,
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_rgb,
                R.string.catalog_keyword_slim,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "light_chihiros_magnetic_lamp_led",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_light_chihiros_magnetic_lamp_led_name,
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitleRes = R.string.catalog_material_category_light_title,
            keywordRes = listOf(
                R.string.catalog_keyword_light,
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_magnetic,
                R.string.catalog_keyword_lamp,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "light_twinstar_s_series",
            brandRes = R.string.catalog_brand_twinstar,
            nameRes = R.string.catalog_material_light_twinstar_s_series_name,
            categoryKey = MaterialCategoryKey.LIGHT,
            categoryTitleRes = R.string.catalog_material_category_light_title,
            keywordRes = listOf(
                R.string.catalog_keyword_light,
                R.string.catalog_keyword_led,
                R.string.catalog_keyword_rgb,
                R.string.catalog_keyword_twinstar
            )
        )
    )
}
