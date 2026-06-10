package com.aqua.aqualight.data.aquarium.catalog.material


object DecorationCatalog {

    private const val CATEGORY_TITLE = "Decoration"

    val products: List<AquariumMaterial> = listOf(
        AquariumMaterial(
            id = "decoration_seiryu_stone",
            brand = "",
            name = "Seiryu Stone",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "stone", "rock", "hardscape", "seiryu")
        ),
        AquariumMaterial(
            id = "decoration_dragon_stone",
            brand = "",
            name = "Dragon Stone",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "stone", "rock", "hardscape", "dragon")
        ),
        AquariumMaterial(
            id = "decoration_lava_rock",
            brand = "",
            name = "Lava Rock",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "stone", "rock", "lava", "hardscape")
        ),
        AquariumMaterial(
            id = "decoration_manten_stone",
            brand = "",
            name = "Manten Stone",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "stone", "rock", "hardscape", "manten")
        ),
        AquariumMaterial(
            id = "decoration_ohko_stone",
            brand = "",
            name = "Ohko Stone",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "stone", "rock", "dragon", "ohko")
        ),
        AquariumMaterial(
            id = "decoration_spider_wood",
            brand = "",
            name = "Spider Wood",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "wood", "driftwood", "hardscape", "spider")
        ),
        AquariumMaterial(
            id = "decoration_mopani_wood",
            brand = "",
            name = "Mopani Wood",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "wood", "driftwood", "hardscape", "mopani")
        ),
        AquariumMaterial(
            id = "decoration_red_moor_wood",
            brand = "",
            name = "Red Moor Wood",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "wood", "driftwood", "hardscape", "moor")
        ),
        AquariumMaterial(
            id = "decoration_mangrove_root",
            brand = "",
            name = "Mangrove Root",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "wood", "root", "driftwood", "mangrove")
        ),
        AquariumMaterial(
            id = "decoration_cholla_wood",
            brand = "",
            name = "Cholla Wood",
            categoryKey = MaterialCategoryKey.DECORATION,
            categoryTitle = CATEGORY_TITLE,
            keywords = listOf("decoration", "wood", "shrimp", "cholla")
        )
    )
}
