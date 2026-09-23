package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R

private const val DECORATION_PRODUCT_COUNT = 233
private const val DECORATION_BRAND_COUNT = 10

private data class DecorationCatalogResource(
    val id: String,
    val brandRes: Int,
    val nameRes: Int,
    val keywordRes: List<Int>
)

private val decorationStoneKeywords = listOf(
    R.string.catalog_keyword_decoration,
    R.string.catalog_keyword_stone,
    R.string.catalog_keyword_rock,
    R.string.catalog_keyword_hardscape
)

private val decorationWoodKeywords = listOf(
    R.string.catalog_keyword_decoration,
    R.string.catalog_keyword_wood,
    R.string.catalog_keyword_driftwood,
    R.string.catalog_keyword_hardscape
)

private val decorationRootKeywords = listOf(
    R.string.catalog_keyword_decoration,
    R.string.catalog_keyword_wood,
    R.string.catalog_keyword_root,
    R.string.catalog_keyword_driftwood,
    R.string.catalog_keyword_hardscape
)

private val decorationBonsaiKeywords = listOf(
    R.string.catalog_keyword_decoration,
    R.string.catalog_keyword_wood,
    R.string.catalog_keyword_root,
    R.string.catalog_keyword_hardscape
)

private val decorationHardscapeKeywords = listOf(
    R.string.catalog_keyword_decoration,
    R.string.catalog_keyword_hardscape
)

private val wioDecorationResources = listOf(
    decoration(
        id = "decoration_wio_adder_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0001_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_bumblebee_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0002_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_dark_ryuoh_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0003_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_seiryu,
            R.string.catalog_decoration_alias_seiryu_stone
        )
    ),
    decoration(
        id = "decoration_wio_dragon_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0004_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ohko,
            R.string.catalog_decoration_alias_ohko_stone
        )
    ),
    decoration(
        id = "decoration_wio_elderly_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0005_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_elderly_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0006_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_fossil_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0007_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_hellboy_dragon_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0008_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ohko,
            R.string.catalog_decoration_alias_ohko_stone
        )
    ),
    decoration(
        id = "decoration_wio_hulk_dragon_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0009_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ohko,
            R.string.catalog_decoration_alias_ohko_stone
        )
    ),
    decoration(
        id = "decoration_wio_inferno_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0010_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_inferno_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0011_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_knight_dragon_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0012_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ohko,
            R.string.catalog_decoration_alias_ohko_stone
        )
    ),
    decoration(
        id = "decoration_wio_mcdonald_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0013_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_midnight_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0014_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_midnight_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0015_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_millennium_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0016_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_mist_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0017_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_paleo_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0018_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_ryuoh_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0019_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_seiryu,
            R.string.catalog_decoration_alias_seiryu_stone
        )
    ),
    decoration(
        id = "decoration_wio_ryuoh_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0020_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_seiryu,
            R.string.catalog_decoration_alias_seiryu_stone
        )
    ),
    decoration(
        id = "decoration_wio_savage_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0021_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_storm_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0022_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_titan_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0023_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_titan_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0024_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_venom_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0025_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_venom_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0026_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_web_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0027_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_wild_stone",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0028_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_zombie_boulder",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0029_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_wio_black_tree_trunk_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0030_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_blondspider_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0031_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_canopy_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0032_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_centurion_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0033_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_dragonscale_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0034_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_isengard_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0035_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_longhorn_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0036_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_neptune_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0037_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_petite_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0038_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_rebel_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0039_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_sinking_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0040_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_stone_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0041_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_striped_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0042_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_tree_trunk_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0043_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_worn_wood",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0044_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_wio_amber_root",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0045_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_wio_elder_root",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0046_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_wio_mini_root",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0047_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_wio_mix_root",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0048_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_wio_spider_twigs_root",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0049_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_wio_twisted_root",
        brandRes = R.string.catalog_decoration_brand_wio,
        nameRes = R.string.catalog_material_decoration_0050_name,
        keywordRes = decorationRootKeywords
    )
)

private val adaDecorationResources = listOf(
    decoration(
        id = "decoration_ada_ryuoh_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0051_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_seiryu,
            R.string.catalog_decoration_alias_seiryu_stone
        )
    ),
    decoration(
        id = "decoration_ada_unzan_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0052_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_sansui_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0053_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_yamaya_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0054_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_lichen_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0055_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_jagure_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0056_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_gatto_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0057_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_tangerine_layer_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0058_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_bling_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0059_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_ohko_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0060_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_dragon_stone
        )
    ),
    decoration(
        id = "decoration_ada_red_slate_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0061_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_buff_layer_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0062_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_red_lava_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0063_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_black_lava_stone",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0064_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_ada_branch_wood",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0065_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_ada_horn_wood",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0066_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_ada_slim_wood",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0067_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_ada_root_branch",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0068_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_ada_horn_wood_chip",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0069_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_ada_horn_wood_pieces",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_decoration_0070_name,
        keywordRes = decorationWoodKeywords
    )
)

private val creaquaDecorationResources = listOf(
    decoration(
        id = "decoration_creaqua_frodo_stone",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0071_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_creaqua_mossrock",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0072_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_creaqua_gray_moon_stone",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0073_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_creaqua_orange_moon_stone",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0074_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_creaqua_galapagos_rock",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0075_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_creaqua_keitir_stone",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0076_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_creaqua_ribbed_wood",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0077_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_creaqua_spotted_wood",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0078_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_creaqua_black_flame",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0079_name,
        keywordRes = decorationWoodKeywords + listOf(
            R.string.catalog_decoration_alias_flamewood,
            R.string.catalog_decoration_alias_flame_wood,
            R.string.catalog_decoration_alias_black_flamewood
        )
    ),
    decoration(
        id = "decoration_creaqua_red_velt",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0080_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_creaqua_arbour_wood",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0081_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_creaqua_twiggy",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0082_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_creaqua_bucelog",
        brandRes = R.string.catalog_decoration_brand_creaqua,
        nameRes = R.string.catalog_material_decoration_0083_name,
        keywordRes = decorationWoodKeywords
    )
)

private val unsDecorationResources = listOf(
    decoration(
        id = "decoration_uns_ancient",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0084_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_ancient_pagoda",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0085_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_ancient_petrified_wood",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0086_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_arbor",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0087_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_ash",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0088_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_black_lava",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0089_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_black_lava_cave",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0090_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_black_lava_mound",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0091_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_black_mountain_seiryu",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0092_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_uns_black_river_slate",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0093_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_black_slate",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0094_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_black_star",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0095_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_blood",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0096_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_blue_seiryu",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0097_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_uns_boutique_seiryu",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0098_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_uns_boutique_seiryu_accent",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0099_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_uns_brook",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0100_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_burmese_petrified_wood",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0101_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_canyon_rock",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0102_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_dark_blue_slate",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0103_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_dark_pagoda",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0104_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_dragon_ohko",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0105_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_dragon_stone,
            R.string.catalog_decoration_alias_ohko_stone
        )
    ),
    decoration(
        id = "decoration_uns_dragon_accent",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0106_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_elephant_skin",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0107_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_fire",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0108_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_gobi_desert",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0109_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_goliath",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0110_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_grey_river_slate",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0111_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_hakkai",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0112_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_icelandic_lava",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0113_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_jade",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0114_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_lagoon",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0115_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_manten",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0116_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_maple_leaf",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0117_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_marsh",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0118_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_mountain",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0119_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_petrified_wood",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0120_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_prism",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0121_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_red_lava",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0122_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_red_petrified_wood",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0123_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_red_slate",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0124_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_rhino",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0125_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_sand",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0126_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_striped_river",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0127_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_white_elephant_skin",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0128_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_wolf",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0129_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_uns_amazon",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0130_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_amazon_basin",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0131_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_black_forest_spider",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0132_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_blistered_sticks",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0133_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_cholla_sticks",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0134_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_cholla_tunnel",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0135_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_cork_bark",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0136_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_dragon",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0137_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_driftwood_stumps",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0138_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_forest",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0139_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_ghost",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0140_name,
        keywordRes = decorationWoodKeywords + listOf(
            R.string.catalog_decoration_alias_ghost_wood
        )
    ),
    decoration(
        id = "decoration_uns_grape",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0141_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_grape_wood_hollow_log",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0142_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_knitted_logs",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0143_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_log",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0144_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_malaysian",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0145_name,
        keywordRes = decorationWoodKeywords + listOf(
            R.string.catalog_decoration_alias_malaysian_driftwood
        )
    ),
    decoration(
        id = "decoration_uns_manzanita",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0146_name,
        keywordRes = decorationWoodKeywords + listOf(
            R.string.catalog_decoration_alias_manzanita_wood
        )
    ),
    decoration(
        id = "decoration_uns_mopani",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0147_name,
        keywordRes = decorationWoodKeywords + listOf(
            R.string.catalog_decoration_alias_mopani_wood
        )
    ),
    decoration(
        id = "decoration_uns_pacific",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0148_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_spider",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0149_name,
        keywordRes = decorationWoodKeywords + listOf(
            R.string.catalog_decoration_alias_spiderwood,
            R.string.catalog_decoration_alias_spider_wood
        )
    ),
    decoration(
        id = "decoration_uns_spider_slate",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0150_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_tangled_tree_root",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0151_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_thorn",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0152_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_tiger",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0153_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_tree_root",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0154_name,
        keywordRes = decorationWoodKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_1",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0155_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_2",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0156_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_3",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0157_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_4",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0158_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_5",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0159_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_6",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0160_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_7",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0161_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_8",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0162_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_9",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0163_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_10",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0164_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_11",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0165_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_12",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0166_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_13",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0167_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_14",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0168_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_15",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0169_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_16",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0170_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_bonsai_17",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0171_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_uns_strata",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0172_name,
        keywordRes = decorationHardscapeKeywords + listOf(
            R.string.catalog_decoration_alias_artificial,
            R.string.catalog_decoration_alias_resin
        )
    ),
    decoration(
        id = "decoration_uns_strata_pro",
        brandRes = R.string.catalog_decoration_brand_uns,
        nameRes = R.string.catalog_material_decoration_0173_name,
        keywordRes = decorationHardscapeKeywords + listOf(
            R.string.catalog_decoration_alias_artificial,
            R.string.catalog_decoration_alias_resin
        )
    )
)

private val greenworksDecorationResources = listOf(
    decoration(
        id = "decoration_greenworks_premium_dark_seiryu_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0174_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_greenworks_frodo_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0175_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_seiryu_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0176_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_greenworks_dragon_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0177_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ohko,
            R.string.catalog_decoration_alias_ohko_stone
        )
    ),
    decoration(
        id = "decoration_greenworks_wild_rhino_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0178_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_petrified_wood_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0179_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_manten_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0180_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_light_pagoda_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0181_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_ice_age_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0182_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_dracula_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0183_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_black_pebble",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0184_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_sensei_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0185_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_lava_stone",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0186_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_greenworks_dark_iron_wood",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0187_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_greenworks_red_moor_wood",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0188_name,
        keywordRes = decorationRootKeywords + listOf(
            R.string.catalog_decoration_alias_redmoor,
            R.string.catalog_decoration_alias_red_moorwood,
            R.string.catalog_decoration_alias_moorwood,
            R.string.catalog_decoration_alias_spiderwood,
            R.string.catalog_decoration_alias_spider_wood,
            R.string.catalog_decoration_alias_fingerwood
        )
    ),
    decoration(
        id = "decoration_greenworks_red_moor_wood_mix",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0189_name,
        keywordRes = decorationRootKeywords + listOf(
            R.string.catalog_decoration_alias_redmoor,
            R.string.catalog_decoration_alias_red_moorwood,
            R.string.catalog_decoration_alias_moorwood,
            R.string.catalog_decoration_alias_spiderwood,
            R.string.catalog_decoration_alias_spider_wood,
            R.string.catalog_decoration_alias_fingerwood
        )
    ),
    decoration(
        id = "decoration_greenworks_riverscape_detailing_branches",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0190_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_greenworks_riverscape_root",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0191_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_greenworks_greenworks_wood_mix",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0192_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_greenworks_bonsai_tree",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0193_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_greenworks_bonsai_tree_banyan",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0194_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_greenworks_bonsai_tree_layout_family",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0195_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_greenworks_bonsai_tree_pagoda",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0196_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_greenworks_bonsai_tree_small_family",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0197_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_greenworks_rasamala_bonsai_wood",
        brandRes = R.string.catalog_decoration_brand_greenworks,
        nameRes = R.string.catalog_material_decoration_0198_name,
        keywordRes = decorationBonsaiKeywords
    )
)

private val riverestDecorationResources = listOf(
    decoration(
        id = "decoration_riverest_seiryu_stones",
        brandRes = R.string.catalog_decoration_brand_riverest,
        nameRes = R.string.catalog_material_decoration_0199_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_riverest_dark_seiryu_stones",
        brandRes = R.string.catalog_decoration_brand_riverest,
        nameRes = R.string.catalog_material_decoration_0200_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ryuoh,
            R.string.catalog_decoration_alias_ryuoh_stone
        )
    ),
    decoration(
        id = "decoration_riverest_hakkai_stone",
        brandRes = R.string.catalog_decoration_brand_riverest,
        nameRes = R.string.catalog_material_decoration_0201_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_riverest_turly_stone",
        brandRes = R.string.catalog_decoration_brand_riverest,
        nameRes = R.string.catalog_material_decoration_0202_name,
        keywordRes = decorationStoneKeywords
    )
)

private val aquadecoDecorationResources = listOf(
    decoration(
        id = "decoration_aquadeco_ohko_dragon_stone",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0203_name,
        keywordRes = decorationStoneKeywords + listOf(
            R.string.catalog_decoration_alias_ohko,
            R.string.catalog_decoration_alias_ohko_stone
        )
    ),
    decoration(
        id = "decoration_aquadeco_hole_stones",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0204_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquadeco_glimmer_rocks",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0205_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquadeco_canyon_rocks",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0206_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquadeco_galapagos_rocks",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0207_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquadeco_zen_pebbles",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0208_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquadeco_black_slim_wood",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0209_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquadeco_mopani_wood",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0210_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquadeco_red_moor_wood",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0211_name,
        keywordRes = decorationRootKeywords + listOf(
            R.string.catalog_decoration_alias_redmoor,
            R.string.catalog_decoration_alias_red_moorwood,
            R.string.catalog_decoration_alias_moorwood,
            R.string.catalog_decoration_alias_spiderwood,
            R.string.catalog_decoration_alias_spider_wood,
            R.string.catalog_decoration_alias_fingerwood
        )
    ),
    decoration(
        id = "decoration_aquadeco_black_root",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0212_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquadeco_dragon_wood",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0213_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquadeco_savanna_wood",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0214_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquadeco_octopus_wood",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0215_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquadeco_aqua_bonsai",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0216_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_aquadeco_forest_bonsai",
        brandRes = R.string.catalog_decoration_brand_aquadeco,
        nameRes = R.string.catalog_material_decoration_0217_name,
        keywordRes = decorationBonsaiKeywords
    )
)

private val aquaelDecorationResources = listOf(
    decoration(
        id = "decoration_aquael_lava_red_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0218_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquael_leopard_stone_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0219_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquael_dinosaur_bone_stone_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0220_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquael_black_quartz_rock_stone_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0221_name,
        keywordRes = decorationStoneKeywords
    ),
    decoration(
        id = "decoration_aquael_mangro_root",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0222_name,
        keywordRes = decorationRootKeywords + listOf(
            R.string.catalog_decoration_alias_mangrow,
            R.string.catalog_decoration_alias_mangrove,
            R.string.catalog_decoration_alias_mangrove_root
        )
    ),
    decoration(
        id = "decoration_aquael_root_driftwood",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0223_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquael_driftwood_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0224_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquael_shrimp_wood_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0225_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquael_red_driftwood_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0226_name,
        keywordRes = decorationRootKeywords
    ),
    decoration(
        id = "decoration_aquael_iron_driftwood_mix",
        brandRes = R.string.catalog_brand_aquael,
        nameRes = R.string.catalog_material_decoration_0227_name,
        keywordRes = decorationRootKeywords
    )
)

private val dennerleDecorationResources = listOf(
    decoration(
        id = "decoration_dennerle_spiderwood",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_decoration_0228_name,
        keywordRes = decorationWoodKeywords + listOf(
            R.string.catalog_decoration_alias_spider_wood,
            R.string.catalog_decoration_alias_redmoor,
            R.string.catalog_decoration_alias_red_moorwood,
            R.string.catalog_decoration_alias_fingerwood
        )
    ),
    decoration(
        id = "decoration_dennerle_decor_box_bonsai",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_decoration_0229_name,
        keywordRes = decorationBonsaiKeywords
    ),
    decoration(
        id = "decoration_dennerle_decor_ceramic",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_decoration_0230_name,
        keywordRes = decorationHardscapeKeywords + listOf(
            R.string.catalog_decoration_alias_artificial,
            R.string.catalog_decoration_alias_resin
        )
    )
)

private val istaDecorationResources = listOf(
    decoration(
        id = "decoration_ista_water_plant_cultivation_ceramic",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_decoration_0231_name,
        keywordRes = decorationHardscapeKeywords + listOf(
            R.string.catalog_decoration_alias_artificial,
            R.string.catalog_decoration_alias_resin
        )
    ),
    decoration(
        id = "decoration_ista_water_plant_rock_ceramic",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_decoration_0232_name,
        keywordRes = decorationHardscapeKeywords + listOf(
            R.string.catalog_decoration_alias_artificial,
            R.string.catalog_decoration_alias_resin
        )
    ),
    decoration(
        id = "decoration_ista_water_plant_stylish_rock_ceramic",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_decoration_0233_name,
        keywordRes = decorationHardscapeKeywords + listOf(
            R.string.catalog_decoration_alias_artificial,
            R.string.catalog_decoration_alias_resin
        )
    )
)

private val decorationCatalogResources =
    wioDecorationResources +
        adaDecorationResources +
        creaquaDecorationResources +
        unsDecorationResources +
        greenworksDecorationResources +
        riverestDecorationResources +
        aquadecoDecorationResources +
        aquaelDecorationResources +
        dennerleDecorationResources +
        istaDecorationResources

object DecorationCatalog {

    val definitions: List<AquariumMaterialDefinition> =
        decorationCatalogResources.map { resource ->
            AquariumMaterialDefinition(
                id = resource.id,
                brandRes = resource.brandRes,
                nameRes = resource.nameRes,
                categoryKey = MaterialCategoryKey.DECORATION,
                categoryTitleRes = R.string.catalog_material_category_decoration_title,
                keywordRes = resource.keywordRes
            )
        }

    init {
        check(definitions.size == DECORATION_PRODUCT_COUNT)
        check(definitions.map(AquariumMaterialDefinition::id).distinct().size == definitions.size)
        check(definitions.map(AquariumMaterialDefinition::nameRes).distinct().size == definitions.size)
        check(definitions.map(AquariumMaterialDefinition::brandRes).distinct().size == DECORATION_BRAND_COUNT)
        check(definitions.all { definition -> definition.id.startsWith("decoration_") })
    }
}

private fun decoration(
    id: String,
    brandRes: Int,
    nameRes: Int,
    keywordRes: List<Int>
): DecorationCatalogResource = DecorationCatalogResource(
    id = id,
    brandRes = brandRes,
    nameRes = nameRes,
    keywordRes = keywordRes
)
