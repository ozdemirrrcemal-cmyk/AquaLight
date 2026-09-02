package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R


object DosingCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "dosing_chihiros_dosing_pump",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_dosing_chihiros_dosing_pump_name,
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitleRes = R.string.catalog_material_category_dosing_title,
            keywordRes = listOf(
                R.string.catalog_keyword_dosing,
                R.string.catalog_keyword_pump,
                R.string.catalog_keyword_fertilizer,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "dosing_chihiros_dosing_system",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_dosing_chihiros_dosing_system_name,
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitleRes = R.string.catalog_material_category_dosing_title,
            keywordRes = listOf(
                R.string.catalog_keyword_dosing,
                R.string.catalog_keyword_pump,
                R.string.catalog_keyword_system,
                R.string.catalog_keyword_fertilizer,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "dosing_jebao_dp_4",
            brandRes = R.string.catalog_brand_jebao,
            nameRes = R.string.catalog_material_dosing_jebao_dp_4_name,
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitleRes = R.string.catalog_material_category_dosing_title,
            keywordRes = listOf(
                R.string.catalog_keyword_dosing,
                R.string.catalog_keyword_pump,
                R.string.catalog_keyword_jebao
            )
        ),
        AquariumMaterialDefinition(
            id = "dosing_kamoer_x1",
            brandRes = R.string.catalog_brand_kamoer,
            nameRes = R.string.catalog_material_dosing_kamoer_x1_name,
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitleRes = R.string.catalog_material_category_dosing_title,
            keywordRes = listOf(
                R.string.catalog_keyword_dosing,
                R.string.catalog_keyword_pump,
                R.string.catalog_keyword_kamoer
            )
        ),
        AquariumMaterialDefinition(
            id = "dosing_kamoer_f4",
            brandRes = R.string.catalog_brand_kamoer,
            nameRes = R.string.catalog_material_dosing_kamoer_f4_name,
            categoryKey = MaterialCategoryKey.DOSING,
            categoryTitleRes = R.string.catalog_material_category_dosing_title,
            keywordRes = listOf(
                R.string.catalog_keyword_dosing,
                R.string.catalog_keyword_pump,
                R.string.catalog_keyword_kamoer
            )
        )
    )
}
