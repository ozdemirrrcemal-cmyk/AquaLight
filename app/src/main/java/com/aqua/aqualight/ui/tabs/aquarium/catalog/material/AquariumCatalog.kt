package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R


object AquariumCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "aquarium_chihiros_tiny_terrarium_egg_wabi_kusa",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_aquarium_chihiros_tiny_terrarium_egg_wabi_kusa_name,
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitleRes = R.string.catalog_material_category_aquarium_title,
            keywordRes = listOf(
                R.string.catalog_keyword_aquarium,
                R.string.catalog_keyword_tank,
                R.string.catalog_keyword_set,
                R.string.catalog_keyword_wabi_kusa,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "aquarium_chihiros_wabi_kusa_magnetic_light_base_glass_air",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_aquarium_chihiros_wabi_kusa_magnetic_light_base_glass_air_name,
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitleRes = R.string.catalog_material_category_aquarium_title,
            keywordRes = listOf(
                R.string.catalog_keyword_aquarium,
                R.string.catalog_keyword_tank,
                R.string.catalog_keyword_set,
                R.string.catalog_keyword_glass,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "aquarium_chihiros_wabi_kusa_magnetic_light_base_glass_pot",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_aquarium_chihiros_wabi_kusa_magnetic_light_base_glass_pot_name,
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitleRes = R.string.catalog_material_category_aquarium_title,
            keywordRes = listOf(
                R.string.catalog_keyword_aquarium,
                R.string.catalog_keyword_tank,
                R.string.catalog_keyword_set,
                R.string.catalog_keyword_glass,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "aquarium_chihiros_magnetic_base",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_aquarium_chihiros_magnetic_base_name,
            categoryKey = MaterialCategoryKey.AQUARIUM,
            categoryTitleRes = R.string.catalog_material_category_aquarium_title,
            keywordRes = listOf(
                R.string.catalog_keyword_aquarium,
                R.string.catalog_keyword_base,
                R.string.catalog_keyword_magnetic,
                R.string.catalog_keyword_chihiros
            )
        )
    )
}
