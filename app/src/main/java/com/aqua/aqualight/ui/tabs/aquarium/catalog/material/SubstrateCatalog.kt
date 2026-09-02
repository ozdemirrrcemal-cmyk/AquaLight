package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R


object SubstrateCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "substrate_chihiros_aquasoil_9l",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_substrate_chihiros_aquasoil_9l_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_aquasoil,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "substrate_chihiros_aquasoil_3l",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_substrate_chihiros_aquasoil_3l_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_aquasoil,
                R.string.catalog_keyword_chihiros
            )
        ),
        AquariumMaterialDefinition(
            id = "substrate_ada_tourmaline_bc",
            brandRes = R.string.catalog_brand_ada,
            nameRes = R.string.catalog_material_substrate_ada_tourmaline_bc_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_ada,
                R.string.catalog_keyword_additive
            )
        ),
        AquariumMaterialDefinition(
            id = "substrate_dennerle_deponitmix_4_8kg",
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_substrate_dennerle_deponitmix_4_8kg_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_base_layer,
                R.string.catalog_keyword_dennerle
            )
        )
    )
}
