package com.aqua.aqualight.data.aquarium.catalog.material


object SubstrateCatalog {

    private const val CATEGORY_TITLE = "Substrate"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "substrate_chihiros_aquasoil_9l",
            brand = "Chihiros",
            name = "Chihiros Aquasoil 9L",
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("substrate", "soil", "aquasoil", "chihiros")
        ),
        AquariumMaterial(
            id = "substrate_chihiros_aquasoil_3l",
            brand = "Chihiros",
            name = "Chihiros Aquasoil 3L",
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("substrate", "soil", "aquasoil", "chihiros")
        ),
        AquariumMaterial(
            id = "substrate_ada_tourmaline_bc",
            brand = "ADA",
            name = "ADA Tourmaline BC",
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("substrate", "soil", "ada", "additive")
        ),
        AquariumMaterial(
            id = "substrate_dennerle_deponitmix_4_8kg",
            brand = "Dennerle",
            name = "Dennerle DeponitMix Professional 10in1 - 4.8 kg",
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("substrate", "soil", "base layer", "dennerle")
        )
    )
}
