package com.aqua.aqualight.data.aquarium.catalog.material


object GravelCatalog {

    private const val CATEGORY_TITLE = "Gravel"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "gravel_ada_aqua_gravel_s",
            brand = "ADA",
            name = "ADA Aqua Gravel S",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "sand", "stone", "ada", "aqua gravel")
        ),
        AquariumMaterial(
            id = "gravel_ada_aqua_gravel_m",
            brand = "ADA",
            name = "ADA Aqua Gravel M",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "sand", "stone", "ada", "aqua gravel")
        ),
        AquariumMaterial(
            id = "gravel_dennerle_nano_gravel_black",
            brand = "Dennerle",
            name = "Dennerle Nano Gravel Black",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "black", "nano", "dennerle")
        ),
        AquariumMaterial(
            id = "gravel_dennerle_nano_gravel_natural",
            brand = "Dennerle",
            name = "Dennerle Nano Gravel Natural",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "natural", "nano", "dennerle")
        ),
        AquariumMaterial(
            id = "gravel_jbl_sansibar_dark",
            brand = "JBL",
            name = "JBL Sansibar Dark",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "sand", "black", "dark", "jbl")
        ),
        AquariumMaterial(
            id = "gravel_jbl_sansibar_white",
            brand = "JBL",
            name = "JBL Sansibar White",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "sand", "white", "jbl")
        ),
        AquariumMaterial(
            id = "gravel_aquael_basaltsand",
            brand = "Aquael",
            name = "Aquael Basalt Sand",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "sand", "basalt", "black", "aquael")
        ),
        AquariumMaterial(
            id = "gravel_natural_river_sand",
            brand = "",
            name = "Natural River Sand",
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("gravel", "sand", "river", "natural")
        )
    )
}
