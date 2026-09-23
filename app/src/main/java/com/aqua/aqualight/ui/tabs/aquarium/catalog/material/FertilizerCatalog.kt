package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R

private const val FERTILIZER_PRODUCT_COUNT = 704
private const val FERTILIZER_BRAND_COUNT = 43

private data class FertilizerCatalogResource(
    val id: String,
    val brandRes: Int,
    val nameRes: Int,
    val keywordRes: List<Int>
)

private val fertilizerBaseKeywords = listOf(
    R.string.catalog_keyword_fertilizer,
    R.string.catalog_keyword_plant
)

private val fertilizerLiquidKeywords = listOf(
    R.string.catalog_keyword_fertilizer,
    R.string.catalog_keyword_liquid,
    R.string.catalog_keyword_plant
)

private val fertilizerCarbonLiquidKeywords = listOf(
    R.string.catalog_keyword_fertilizer,
    R.string.catalog_keyword_liquid,
    R.string.catalog_keyword_plant,
    R.string.catalog_keyword_carbon
)

private val fertilizerResources0001To0088 = listOf(
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_1_zero_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0001_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_1_zero_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0002_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_1_zero_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0003_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_1_zero_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0004_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_1_zero_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0005_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_3_complete_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0006_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_3_complete_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0007_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_3_complete_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0008_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_3_complete_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0009_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_3_complete_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0010_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_e_estimative_index_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0011_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_e_estimative_index_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0012_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_e_estimative_index_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0013_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_e_estimative_index_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0014_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_e_estimative_index_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0015_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_dew_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0016_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_dew_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0017_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_jazz_18_caps",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0018_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_start_45_g",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0019_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_sky_150_g",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0020_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_2hr_aquarist_apt_sky_plus_150_g",
        brandRes = R.string.catalog_fertilizer_brand_2hr_aquarist,
        nameRes = R.string.catalog_material_fertilizer_0021_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_neutral_k_180_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0022_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_neutral_k_300_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0023_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_neutral_k_5000_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0024_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_brighty_k_180_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0025_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_brighty_k_300_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0026_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_brighty_k_5000_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0027_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_nitrogen_180_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0028_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_nitrogen_300_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0029_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_nitrogen_5000_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0030_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_mineral_180_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0031_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_mineral_300_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0032_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_mineral_5000_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0033_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_iron_180_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0034_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_iron_300_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0035_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_brighty_iron_5000_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0036_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_eca_plus_50_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0037_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_eca_plus_500_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0038_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_gain_plus_50_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0039_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_green_gain_plus_500_ml",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0040_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ada_bottom_plus_25_pcs",
        brandRes = R.string.catalog_brand_ada,
        nameRes = R.string.catalog_material_fertilizer_0041_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_macro_250_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0042_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_macro_500_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0043_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_macro_1000_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0044_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_micro_250_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0045_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_micro_500_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0046_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_micro_1000_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0047_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_carbon_250_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0048_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_carbon_500_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0049_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_carbon_1000_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0050_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_balance_250_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0051_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_balance_500_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0052_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_balance_1000_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0053_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_micro_macro_wellness_pack_250_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0054_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_micro_macro_wellness_pack_500_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0055_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_micro_macro_wellness_pack_1000_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0056_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_balance_wellness_pack_250_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0057_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_balance_wellness_pack_500_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0058_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_green_aqua_balance_wellness_pack_1000_ml",
        brandRes = R.string.catalog_brand_green_aqua,
        nameRes = R.string.catalog_material_fertilizer_0059_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0060_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0061_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0062_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0063_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0064_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0065_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_advance_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0066_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_advance_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0067_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_advance_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0068_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_advance_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0069_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_advance_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0070_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_advance_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0071_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_excel_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0072_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_excel_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0073_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_excel_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0074_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_excel_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0075_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_excel_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0076_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_excel_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0077_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_iron_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0078_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_iron_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0079_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_iron_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0080_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_iron_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0081_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_iron_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0082_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_iron_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0083_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_nitrogen_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0084_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_nitrogen_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0085_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_nitrogen_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0086_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_nitrogen_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0087_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_nitrogen_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0088_name,
        keywordRes = fertilizerLiquidKeywords
    )
)

private val fertilizerResources0089To0176 = listOf(
    fertilizer(
        id = "fertilizer_seachem_flourish_phosphorus_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0089_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_phosphorus_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0090_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_phosphorus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0091_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_phosphorus_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0092_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_phosphorus_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0093_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_potassium_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0094_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_potassium_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0095_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_potassium_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0096_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_potassium_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0097_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_potassium_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0098_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_potassium_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0099_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_trace_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0100_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_trace_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0101_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_trace_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0102_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_trace_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0103_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_trace_2_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0104_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_trace_4_l",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0105_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_tabs_10_tabs",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0106_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_flourish_tabs_40_tabs",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0107_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_equilibrium_300_g",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0108_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_equilibrium_600_g",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0109_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_equilibrium_4_kg",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0110_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_plant_pack_fundamentals_3_x_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0111_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_seachem_plant_pack_enhancer_npk_3_x_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_seachem,
        nameRes = R.string.catalog_material_fertilizer_0112_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_premium_nutrition_125_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0113_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_premium_nutrition_300_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0114_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_premium_nutrition_750_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0115_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_premium_nutrition_3_l",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0116_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_specialised_nutrition_125_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0117_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_specialised_nutrition_300_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0118_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_specialised_nutrition_750_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0119_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_specialised_nutrition_3_l",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0120_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_carbon_nutrition_125_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0121_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_carbon_nutrition_300_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0122_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_carbon_nutrition_750_ml",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0123_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_nutrition_capsules_3_pcs",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0124_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_nutrition_capsules_10_pcs",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0125_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_tropica_nutrition_capsules_50_pcs",
        brandRes = R.string.catalog_brand_tropica,
        nameRes = R.string.catalog_material_fertilizer_0126_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plants_liquid_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_dennerle_plants,
        nameRes = R.string.catalog_material_fertilizer_0127_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plants_liquid_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_dennerle_plants,
        nameRes = R.string.catalog_material_fertilizer_0128_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plants_liquid_plus_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_dennerle_plants,
        nameRes = R.string.catalog_material_fertilizer_0129_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plants_liquid_plus_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_dennerle_plants,
        nameRes = R.string.catalog_material_fertilizer_0130_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plants_power_caps_25_pcs",
        brandRes = R.string.catalog_fertilizer_brand_dennerle_plants,
        nameRes = R.string.catalog_material_fertilizer_0131_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_npk_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0132_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_npk_500_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0133_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_pro_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0134_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_pro_500_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0135_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_elixir_basic_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0136_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_elixir_basic_500_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0137_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_carbo_care_bio_100_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0138_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_carbo_care_bio_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0139_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_carbo_care_bio_500_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0140_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_carbo_care_pro_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0141_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_carbo_care_pro_500_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0142_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_v30_100_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0143_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_v30_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0144_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_v30_500_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0145_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_s7_100_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0146_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_s7_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0147_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_s7_500_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0148_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_k_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0149_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_n_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0150_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_p_250_ml",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0151_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_active_enzymes_50_g",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0152_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_basic_root_10_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0153_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_basic_root_20_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0154_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_basic_root_40_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0155_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_pro_root_10_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0156_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_care_pro_root_30_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0157_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_e15_20_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0158_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_e15_40_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0159_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dennerle_plant_system_e15_100_pcs",
        brandRes = R.string.catalog_brand_dennerle,
        nameRes = R.string.catalog_material_fertilizer_0160_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_npk_plus_macroelements_250_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0161_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_npk_plus_macroelements_500_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0162_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_k_plus_macroelements_250_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0163_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_p_plus_macroelements_250_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0164_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_mg_plus_macroelements_250_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0165_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_n_plus_macroelements_250_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0166_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_fe_plus_microelements_250_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0167_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proscape_fe_plus_microelements_500_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0168_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_ferropol_100_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0169_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_ferropol_250_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0170_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_ferropol_500_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0171_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_ferropol_refill_625_ml",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0172_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_ferropol_root_30_tabs",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0173_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_7_balls_7_balls",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0174_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_7_plus_13_balls_20_balls",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0175_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_florena_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0176_name,
        keywordRes = fertilizerLiquidKeywords
    )
)

private val fertilizerResources0177To0264 = listOf(
    fertilizer(
        id = "fertilizer_sera_florena_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0177_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_florena_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0178_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_florena_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0179_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_florena_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0180_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_florenette_24_tabs",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0181_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_florenette_50_tabs",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0182_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_1_carbo_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0183_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_1_carbo_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0184_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_1_carbo_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0185_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_2_ferro_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0186_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_2_ferro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0187_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_2_ferro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0188_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_3_vital_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0189_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_3_vital_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0190_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_4_plant_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0191_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sera_flore_4_plant_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sera,
        nameRes = R.string.catalog_material_fertilizer_0192_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_profito_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0193_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_profito_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0194_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_profito_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0195_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_profito_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0196_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_profito_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0197_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0198_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0199_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0200_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0201_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0202_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_bio_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0203_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_bio_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0204_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_bio_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0205_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_bio_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0206_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_easycarbo_bio_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0207_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_ferro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0208_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_ferro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0209_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_nitro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0210_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_nitro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0211_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_fosfo_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0212_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_fosfo_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0213_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_kalium_potassium_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0214_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_kalium_potassium_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0215_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_redscape_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0216_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_redscape_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0217_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_redscape_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0218_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_redscape_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0219_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_redscape_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0220_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_greenscape_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0221_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_greenscape_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0222_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_greenscape_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0223_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_greenscape_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0224_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_greenscape_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0225_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_easy_life_root_sticks_25_pcs",
        brandRes = R.string.catalog_fertilizer_brand_easy_life,
        nameRes = R.string.catalog_material_fertilizer_0226_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_i_micro_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0227_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_i_micro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0228_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_i_micro_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0229_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_i_micro_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0230_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_ii_macro_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0231_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_ii_macro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0232_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_ii_macro_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0233_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_ii_macro_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0234_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_carbo_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0235_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_carbo_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0236_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_carbo_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0237_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_carbo_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0238_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_boost_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0239_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_boost_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0240_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_boost_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0241_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_boost_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0242_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_golden_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0243_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_golden_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0244_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_golden_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0245_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_golden_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0246_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_lean_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0247_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_lean_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0248_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_lean_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0249_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_lean_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0250_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_red_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0251_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_all_in_one_red_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0252_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_nitrate_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0253_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_nitrate_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0254_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_phosphate_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0255_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_phosphate_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0256_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_potassium_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0257_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_potassium_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0258_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_iron_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0259_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_iron_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0260_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_root_caps_60_pcs",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0261_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_masterline_root_caps_125_pcs",
        brandRes = R.string.catalog_fertilizer_brand_masterline,
        nameRes = R.string.catalog_material_fertilizer_0262_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_basic_eisen_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0263_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_basic_eisen_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0264_name,
        keywordRes = fertilizerLiquidKeywords
    )
)

private val fertilizerResources0265To0352 = listOf(
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_basic_eisen_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0265_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_npk_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0266_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_npk_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0267_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_npk_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0268_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_spezial_flowgrow_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0269_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_spezial_flowgrow_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0270_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_spezial_flowgrow_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0271_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_estimative_index_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0272_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_estimative_index_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0273_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_estimative_index_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0274_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_advanced_gh_boost_n_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0275_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_advanced_gh_boost_n_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0276_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_advanced_gh_boost_n_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0277_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_spezial_eisen_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0278_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_spezial_eisen_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0279_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_mikro_spezial_eisen_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0280_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_kalium_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0281_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_kalium_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0282_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_kalium_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0283_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_phosphat_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0284_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_phosphat_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0285_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_phosphat_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0286_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_spezial_n_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0287_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_basic_nitrat_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0288_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_rebell_makro_spezial_npk_soil_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_aqua_rebell,
        nameRes = R.string.catalog_material_fertilizer_0289_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_macro_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0290_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_macro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0291_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_macro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0292_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_macro_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0293_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_micro_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0294_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_micro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0295_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_micro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0296_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_micro_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0297_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_iron_boost_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0298_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_iron_boost_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0299_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_iron_boost_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0300_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_iron_boost_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0301_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_k_boost_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0302_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_k_boost_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0303_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_k_boost_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0304_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_k_boost_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0305_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_po4_boost_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0306_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_po4_boost_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0307_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_po4_boost_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0308_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_po4_boost_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0309_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_n_boost_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0310_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_n_boost_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0311_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_n_boost_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0312_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_n_boost_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0313_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_carbon_boost_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0314_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_carbon_boost_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0315_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_carbon_boost_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0316_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_carbon_boost_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0317_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_red_boost_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0318_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_red_boost_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0319_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_red_boost_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0320_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_red_boost_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0321_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_af_starter_pack_freshwater_1_pack",
        brandRes = R.string.catalog_fertilizer_brand_aquaforest,
        nameRes = R.string.catalog_material_fertilizer_0322_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_1_150_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0323_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_1_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0324_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_1_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0325_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_2_150_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0326_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_2_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0327_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_2_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0328_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_k_150_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0329_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_k_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0330_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_k_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0331_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_fe_150_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0332_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_fe_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0333_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_fe_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0334_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_complex_150_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0335_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_complex_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0336_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_solution_complex_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0337_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_plants_tab_70_g",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0338_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_plants_k_70_g",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0339_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_plants_fe_70_g",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0340_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_plants_st_long_70_g",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0341_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_booster_plants_300_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0342_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_cap_tropical_booster_retail_pack",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0343_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aquario_neo_plants_tab_solid_slim_retail_pack",
        brandRes = R.string.catalog_fertilizer_brand_aquario_neo,
        nameRes = R.string.catalog_material_fertilizer_0344_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinmulti_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0345_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinmulti_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0346_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinmulti_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0347_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinmulti_2_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0348_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinmulti_20_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0349_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_k_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0350_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_k_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0351_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_k_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0352_name,
        keywordRes = fertilizerLiquidKeywords
    )
)

private val fertilizerResources0353To0440 = listOf(
    fertilizer(
        id = "fertilizer_brightwell_florin_k_2_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0353_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_k_20_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0354_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_fe_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0355_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_fe_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0356_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_fe_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0357_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_fe_2_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0358_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_fe_20_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0359_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_floringro_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0360_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_floringro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0361_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_floringro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0362_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_floringro_2_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0363_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_floringro_20_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0364_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_p_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0365_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_p_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0366_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_p_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0367_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_p_2_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0368_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_p_20_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0369_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinaxis_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0370_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinaxis_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0371_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinaxis_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0372_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinaxis_2_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0373_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinaxis_20_l",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0374_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_delta_gh_plus_250_g",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0375_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_delta_gh_plus_500_g",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0376_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_delta_gh_plus_1_kg",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0377_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_delta_gh_plus_4_kg",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0378_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_delta_gh_plus_20_kg",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0379_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florin_delta_gh_plus_220_kg",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0380_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinbase_laterite_powder_160_g",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0381_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinbase_laterite_powder_320_g",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0382_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinbase_laterite_powder_600_g",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0383_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_brightwell_florinbase_laterite_powder_1000_g",
        brandRes = R.string.catalog_fertilizer_brand_brightwell_aquatics,
        nameRes = R.string.catalog_material_fertilizer_0384_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_api_leaf_zone_237_ml_8_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_api,
        nameRes = R.string.catalog_material_fertilizer_0385_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_api_leaf_zone_473_ml_16_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_api,
        nameRes = R.string.catalog_material_fertilizer_0386_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_api_co2_booster_237_ml_8_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_api,
        nameRes = R.string.catalog_material_fertilizer_0387_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_api_co2_booster_473_ml_16_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_api,
        nameRes = R.string.catalog_material_fertilizer_0388_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_api_root_tabs_10_tabs",
        brandRes = R.string.catalog_fertilizer_brand_api,
        nameRes = R.string.catalog_material_fertilizer_0389_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_fluval_plant_gro_plus_120_ml",
        brandRes = R.string.catalog_fertilizer_brand_fluval,
        nameRes = R.string.catalog_material_fertilizer_0390_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_fluval_plant_gro_plus_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_fluval,
        nameRes = R.string.catalog_material_fertilizer_0391_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_green_all_in_one_fertilizer_120_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0392_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_green_all_in_one_fertilizer_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0393_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_green_all_in_one_fertilizer_2_l",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0394_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_potassium_120_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0395_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_potassium_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0396_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_iron_120_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0397_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_iron_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0398_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_root_tabs_20_tabs",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0399_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aquarium_co_op_easy_root_tabs_60_tabs",
        brandRes = R.string.catalog_fertilizer_brand_aquarium_co_op,
        nameRes = R.string.catalog_material_fertilizer_0400_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrive_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0401_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrive_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0402_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrive_4000_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0403_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrive_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0404_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrive_plus_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0405_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrive_plus_4000_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0406_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrivec_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0407_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrivec_2000_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0408_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrivec_4000_ml",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0409_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrives_current_retail_bottle",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0410_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrivetabs_75_tabs",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0411_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_thrivecaps_current_retail_pack",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0412_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_potassium_nitrate_kno3_1_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0413_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_potassium_nitrate_kno3_5_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0414_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_potassium_sulfate_k2so4_1_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0415_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_potassium_sulfate_k2so4_5_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0416_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_monopotassium_phosphate_kh2po4_1_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0417_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_monopotassium_phosphate_kh2po4_5_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0418_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_plantex_csm_plus_b_1_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0419_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_plantex_csm_plus_b_5_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0420_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_gh_booster_1_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0421_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_gh_booster_5_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0422_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_microplex_0_5_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0423_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_microplex_1_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0424_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_microplex_5_lb",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0425_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_manganese_sulfate_mnso4_dry_salt",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0426_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_calcium_sulfate_caso4_dry_salt",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0427_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_magnesium_sulfate_mgso4_dry_salt",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0428_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_iron_gluconate_dry_salt",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0429_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_iron_chelate_dry_salt",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0430_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_ei_based_npk_and_csm_plus_b_fertilizer_package_1_kit",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0431_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nilocg_pps_pro_fertilizer_package_1_kit",
        brandRes = R.string.catalog_fertilizer_brand_nilocg,
        nameRes = R.string.catalog_material_fertilizer_0432_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_potassium_nitrate_kno3_aquarium_fertilizer_1_lb_bag",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0433_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_potassium_nitrate_kno3_aquarium_fertilizer_1_lb_jar",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0434_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_monopotassium_phosphate_kh2po4_aquarium_fertilizer_1_lb_bag",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0435_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_monopotassium_phosphate_kh2po4_aquarium_fertilizer_1_lb_jar",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0436_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_potassium_sulfate_k2so4_aquarium_fertilizer_1_lb_bag",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0437_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_potassium_sulfate_k2so4_aquarium_fertilizer_1_lb_jar",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0438_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_macros_macromix_npk_and_mg_1_lb_bag",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0439_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_macros_and_micros_fertilizer_kit_ei_pps_pro_bag_kit",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0440_name,
        keywordRes = fertilizerBaseKeywords
    )
)

private val fertilizerResources0441To0528 = listOf(
    fertilizer(
        id = "fertilizer_gla_macros_and_micros_fertilizer_kit_ei_pps_pro_jar_kit",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0441_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_macros_and_micros_ii_copper_free_fertilizer_kit_bag_kit",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0442_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_gla_macros_and_micros_ii_copper_free_fertilizer_kit_jar_kit",
        brandRes = R.string.catalog_fertilizer_brand_green_leaf_aquariums_gla,
        nameRes = R.string.catalog_material_fertilizer_0443_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_plantamin_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0444_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_plantamin_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0445_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_plantamin_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0446_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_plantamin_5_l",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0447_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_plantapro_micro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0448_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_co2_plus_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0449_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_crypto_10_tabs",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0450_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_crypto_30_tabs",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0451_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_tetra_plantastart_12_tabs",
        brandRes = R.string.catalog_fertilizer_brand_tetra,
        nameRes = R.string.catalog_material_fertilizer_0452_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dupla_plant_24_10_ml",
        brandRes = R.string.catalog_fertilizer_brand_dupla,
        nameRes = R.string.catalog_material_fertilizer_0453_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dupla_plant_24_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_dupla,
        nameRes = R.string.catalog_material_fertilizer_0454_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dupla_plant_24_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_dupla,
        nameRes = R.string.catalog_material_fertilizer_0455_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_dupla_plant_basic_10_tabs",
        brandRes = R.string.catalog_fertilizer_brand_dupla,
        nameRes = R.string.catalog_material_fertilizer_0456_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dupla_plant_basic_50_tabs",
        brandRes = R.string.catalog_fertilizer_brand_dupla,
        nameRes = R.string.catalog_material_fertilizer_0457_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_dupla_plant_basic_200_tabs",
        brandRes = R.string.catalog_fertilizer_brand_dupla,
        nameRes = R.string.catalog_material_fertilizer_0458_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_scaperline_daily_fertilizer_150_ml",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0459_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_scaperline_daily_fertilizer_300_ml",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0460_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_scaperline_mineralmix_fertilizer_150_ml",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0461_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_scaperline_mineralmix_fertilizer_300_ml",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0462_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_scaperline_plant_boost_tablets_10_pcs",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0463_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_plantgrow_daily_250_ml",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0464_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_plantgrow_iron_250_ml",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0465_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_plantgrow_weekly_250_ml",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0466_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_sosgrow_quick_fertilizer_tablets_10_pcs",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0467_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_oase_sosgrow_quick_fertilizer_tablets_20_pcs",
        brandRes = R.string.catalog_brand_oase,
        nameRes = R.string.catalog_material_fertilizer_0468_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_all_in_one_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0469_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_all_in_one_1175_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0470_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_all_in_one_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0471_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_all_in_red_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0472_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_all_in_red_1175_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0473_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_all_in_red_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0474_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_micro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0475_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_micro_1175_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0476_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_micro_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0477_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_accelerator_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0478_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_accelerator_1175_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0479_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_accelerator_5000_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0480_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_solo_n_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0481_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_solo_fe_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0482_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_solo_p_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0483_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_solo_k_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0484_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_solo_trace_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0485_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_reminamin_250_g",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0486_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_reminamin_500_g",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0487_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_reminamin_1000_g",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0488_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_bottom_action_20_pcs",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0489_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_bottom_action_50_pcs",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0490_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_bottom_long_lasting_20_pcs",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0491_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_vimi_bottom_long_lasting_50_pcs",
        brandRes = R.string.catalog_fertilizer_brand_vimi,
        nameRes = R.string.catalog_material_fertilizer_0492_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0493_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0494_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_2500_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0495_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_pro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0496_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_pro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0497_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_pro_2500_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0498_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_carbo_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0499_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_carbo_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0500_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_floragrow_carbo_2500_ml",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0501_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_colombo_fe_tabs_retail_pack",
        brandRes = R.string.catalog_fertilizer_brand_colombo,
        nameRes = R.string.catalog_material_fertilizer_0502_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_120_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0503_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_250_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0504_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_500_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0505_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_1_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0506_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_2_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0507_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_4_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0508_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_fertilizer_120_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0509_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_fertilizer_240_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0510_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_fertilizer_500_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0511_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_fertilizer_1_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0512_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_fertilizer_2_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0513_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_fertilizer_4_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0514_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_red_promote_120_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0515_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_red_promote_240_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0516_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_red_promote_500_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0517_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_red_promote_1_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0518_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_red_promote_2_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0519_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_premium_water_plant_red_promote_4_l",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0520_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_trace_elements_120_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0521_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_trace_elements_250_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0522_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_trace_elements_500_ml",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0523_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_ball_10_balls",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0524_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_ball_20_balls",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0525_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_water_plant_fertilizer_ball_50_balls",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0526_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_ista_plant_fertilizer_tablets_20_tabs_100_g",
        brandRes = R.string.catalog_brand_ista,
        nameRes = R.string.catalog_material_fertilizer_0527_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aqua_plant_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0528_name,
        keywordRes = fertilizerLiquidKeywords
    )
)

private val fertilizerResources0529To0616 = listOf(
    fertilizer(
        id = "fertilizer_tropical_aqua_plant_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0529_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aqua_plant_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0530_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aqua_plant_2_l",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0531_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aquaflorin_potassium_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0532_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aquaflorin_potassium_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0533_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aquaflorin_potassium_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0534_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aquaflorin_potassium_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0535_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_aquaflorin_potassium_2_l",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0536_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_ferro_aktiv_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0537_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_ferro_aktiv_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0538_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_ferro_aktiv_2_l",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0539_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_carbo_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0540_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_carbo_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0541_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_tropical_carbo_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_tropical,
        nameRes = R.string.catalog_material_fertilizer_0542_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_prodibio_biovert_plus_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_prodibio,
        nameRes = R.string.catalog_material_fertilizer_0543_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_prodibio_biovert_plus_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_prodibio,
        nameRes = R.string.catalog_material_fertilizer_0544_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_prodibio_biovert_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_prodibio,
        nameRes = R.string.catalog_material_fertilizer_0545_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_prodibio_biovert_ultimate_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_prodibio,
        nameRes = R.string.catalog_material_fertilizer_0546_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_prodibio_biovert_ultimate_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_prodibio,
        nameRes = R.string.catalog_material_fertilizer_0547_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_prodibio_biovert_ultimate_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_prodibio,
        nameRes = R.string.catalog_material_fertilizer_0548_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_uns_plant_food_min_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_ultum_nature_systems_uns,
        nameRes = R.string.catalog_material_fertilizer_0549_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_uns_plant_food_max_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_ultum_nature_systems_uns,
        nameRes = R.string.catalog_material_fertilizer_0550_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_x_all_in_one_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0551_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_x_all_in_one_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0552_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_n_nitrogen_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0553_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_n_nitrogen_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0554_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_p_phosphorus_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0555_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_p_phosphorus_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0556_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_k_potassium_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0557_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_k_potassium_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0558_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_fe_iron_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0559_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_fe_iron_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0560_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_bg_microelements_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0561_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_bg_microelements_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0562_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_c_carbon_30_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0563_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_gen_c_carbon_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0564_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_sosei_caps_15_pcs",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0565_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_yokuchi_sosei_caps_80_pcs",
        brandRes = R.string.catalog_fertilizer_brand_yokuchi,
        nameRes = R.string.catalog_material_fertilizer_0566_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_classic_10_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0567_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_classic_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0568_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_classic_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0569_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_ferro_plus_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0570_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_ferro_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0571_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_micro_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0572_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_micro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0573_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_macro_red_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0574_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_macro_red_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0575_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_macro_green_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0576_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_pro_macro_green_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0577_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_k_plus_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0578_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_k_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0579_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_carbo_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0580_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_carbo_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0581_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_planta_gainer_caps_retail_pack",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0582_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_aqua_art_vivoverde_tropical_rain_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aqua_art,
        nameRes = R.string.catalog_material_fertilizer_0583_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_azoo_plant_nutrients_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_azoo,
        nameRes = R.string.catalog_material_fertilizer_0584_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_azoo_trace_elements_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_azoo,
        nameRes = R.string.catalog_material_fertilizer_0585_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_azoo_red_plant_nutrients_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_azoo,
        nameRes = R.string.catalog_material_fertilizer_0586_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_azoo_chelated_ferrite_liquid_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_azoo,
        nameRes = R.string.catalog_material_fertilizer_0587_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_azoo_carbon_plus_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_azoo,
        nameRes = R.string.catalog_material_fertilizer_0588_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_azoo_basic_fertilizer_size_varies_by_market",
        brandRes = R.string.catalog_fertilizer_brand_azoo,
        nameRes = R.string.catalog_material_fertilizer_0589_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_azoo_ferti_stick_retail_pack",
        brandRes = R.string.catalog_fertilizer_brand_azoo,
        nameRes = R.string.catalog_material_fertilizer_0590_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_microbe_lift_all_in_one_aquatic_plant_supplement_236_ml_8_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_microbe_lift,
        nameRes = R.string.catalog_material_fertilizer_0591_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_microbe_lift_all_in_one_aquatic_plant_supplement_473_ml_16_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_microbe_lift,
        nameRes = R.string.catalog_material_fertilizer_0592_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_microbe_lift_bloom_and_grow_iron_236_ml_8_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_microbe_lift,
        nameRes = R.string.catalog_material_fertilizer_0593_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_microbe_lift_bloom_and_grow_iron_473_ml_16_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_microbe_lift,
        nameRes = R.string.catalog_material_fertilizer_0594_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_microbe_lift_bloom_and_grow_473_ml_16_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_microbe_lift,
        nameRes = R.string.catalog_material_fertilizer_0595_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_microbe_lift_bloom_and_grow_946_ml_32_fl_oz",
        brandRes = R.string.catalog_fertilizer_brand_microbe_lift,
        nameRes = R.string.catalog_material_fertilizer_0596_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_microbe_lift_bloom_and_grow_1_gal",
        brandRes = R.string.catalog_fertilizer_brand_microbe_lift,
        nameRes = R.string.catalog_material_fertilizer_0597_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_nitrate_i_85_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0598_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_nitrate_i_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0599_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_nitrate_i_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0600_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_nitrate_i_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0601_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_phosphate_ii_85_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0602_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_phosphate_ii_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0603_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_phosphate_ii_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0604_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_phosphate_ii_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0605_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_liquid_carbon_iii_85_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0606_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_liquid_carbon_iii_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0607_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_liquid_carbon_iii_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0608_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_liquid_carbon_iii_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0609_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_potash_iv_85_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0610_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_potash_iv_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0611_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_potash_iv_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0612_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_potash_iv_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0613_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_ferrous_v_85_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0614_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_ferrous_v_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0615_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_ferrous_v_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0616_name,
        keywordRes = fertilizerLiquidKeywords
    )
)

private val fertilizerResources0617To0704 = listOf(
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_ferrous_v_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0617_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_trace_vi_85_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0618_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_trace_vi_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0619_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_trace_vi_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0620_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_trace_vi_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0621_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_all_inclusive_85_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0622_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_all_inclusive_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0623_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_all_inclusive_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0624_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_reeflowers_aquaplants_all_inclusive_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_reeflowers,
        nameRes = R.string.catalog_material_fertilizer_0625_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_demir_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0626_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_demir_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0627_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_demir_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0628_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_potasyum_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0629_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_potasyum_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0630_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_potasyum_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0631_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_nope_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0632_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_nope_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0633_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_nope_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0634_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_finish_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0635_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_finish_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0636_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_finish_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0637_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_gold_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0638_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_gold_200_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0639_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_gold_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0640_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_akvaryum_kapsul_gubresi_10_pcs",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0641_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_gh_50_g",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0642_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_gh_100_g",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0643_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_kh_gh_50_g",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0644_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_nixa_kh_gh_100_g",
        brandRes = R.string.catalog_fertilizer_brand_nixa,
        nameRes = R.string.catalog_material_fertilizer_0645_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_makro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0646_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_mikro_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0647_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_potasyum_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0648_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_gh_plus_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0649_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_nitrogen_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0650_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_phospate_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0651_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_iron_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0652_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_six_up_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0653_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_exalg_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0654_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_low_tech_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0655_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_creaqua_low_tech_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_creaqua,
        nameRes = R.string.catalog_material_fertilizer_0656_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_micro_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0657_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_micro_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0658_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_multi_minerals_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0659_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_multi_minerals_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0660_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_npk_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0661_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_npk_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0662_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_potassium_plus_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0663_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_potassium_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0664_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_carbon_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0665_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_carbon_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0666_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_nitrate_plus_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0667_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_nitrate_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0668_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_trace_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0669_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_flora_trace_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0670_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_phosphate_plus_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0671_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_phosphate_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0672_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_ferro_plus_125_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0673_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_crystalpro_ferro_plus_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_crystalpro,
        nameRes = R.string.catalog_material_fertilizer_0674_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_deep_fix_floramin_50_ml",
        brandRes = R.string.catalog_fertilizer_brand_deep_fix,
        nameRes = R.string.catalog_material_fertilizer_0675_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquamins_aqua_plants_all_included_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquamins,
        nameRes = R.string.catalog_material_fertilizer_0676_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquamins_aqua_plants_all_included_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquamins,
        nameRes = R.string.catalog_material_fertilizer_0677_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquamins_aqua_plants_all_included_500_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquamins,
        nameRes = R.string.catalog_material_fertilizer_0678_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquamins_aqua_plants_all_included_1000_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquamins,
        nameRes = R.string.catalog_material_fertilizer_0679_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquamins_aqua_potasflow_100_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquamins,
        nameRes = R.string.catalog_material_fertilizer_0680_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_aquamins_aqua_potasflow_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_aquamins,
        nameRes = R.string.catalog_material_fertilizer_0681_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_potasyum_bitki_gubresi_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0682_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_nitrat_bitki_gubresi_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0683_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_fosfat_bitki_gubresi_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0684_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_mikro_element_bitki_gubresi_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0685_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_demir_bitki_gubresi_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0686_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_excel_sivi_karbon_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0687_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_bitki_gubresi_seti_5_x_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0688_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_bitki_gubresi_seti_6_x_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0689_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_potasyum_gubre_kiti_1_l_hazirlama_kiti",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0690_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_nitrat_gubre_kiti_1_l_hazirlama_kiti",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0691_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_fosfat_gubre_kiti_1_l_hazirlama_kiti",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0692_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_demir_gubre_kiti_1_l_hazirlama_kiti",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0693_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_badem_akvaryum_akilli_kapsul_gubre_10_pcs",
        brandRes = R.string.catalog_fertilizer_brand_badem_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0694_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_sakura_akvaryum_bitki_gubresi_demir_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sakura_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0695_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sakura_akvaryum_bitki_gubresi_fosfat_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sakura_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0696_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sakura_akvaryum_bitki_gubresi_nitrat_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sakura_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0697_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sakura_akvaryum_bitki_gubresi_potasyum_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sakura_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0698_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sakura_akvaryum_sivi_karbon_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sakura_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0699_name,
        keywordRes = fertilizerCarbonLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_sakura_akvaryum_bitki_gubresi_seti_5_x_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sakura_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0700_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_sakura_akvaryum_npk_seti_3_x_250_ml",
        brandRes = R.string.catalog_fertilizer_brand_sakura_akvaryum,
        nameRes = R.string.catalog_material_fertilizer_0701_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_lab_formula_plant_fertilizer_400_ml",
        brandRes = R.string.catalog_fertilizer_brand_lab_formula,
        nameRes = R.string.catalog_material_fertilizer_0702_name,
        keywordRes = fertilizerLiquidKeywords
    ),
    fertilizer(
        id = "fertilizer_ocean_nutrition_giovannis_starter_200_g",
        brandRes = R.string.catalog_fertilizer_brand_ocean_nutrition,
        nameRes = R.string.catalog_material_fertilizer_0703_name,
        keywordRes = fertilizerBaseKeywords
    ),
    fertilizer(
        id = "fertilizer_jbl_proflora_plantstart_2_x_8_g",
        brandRes = R.string.catalog_brand_jbl,
        nameRes = R.string.catalog_material_fertilizer_0704_name,
        keywordRes = fertilizerBaseKeywords
    )
)

private val fertilizerCatalogResources =
    fertilizerResources0001To0088 +
        fertilizerResources0089To0176 +
        fertilizerResources0177To0264 +
        fertilizerResources0265To0352 +
        fertilizerResources0353To0440 +
        fertilizerResources0441To0528 +
        fertilizerResources0529To0616 +
        fertilizerResources0617To0704

object FertilizerCatalog {

    val definitions: List<AquariumMaterialDefinition> =
        fertilizerCatalogResources.map { resource ->
            AquariumMaterialDefinition(
                id = resource.id,
                brandRes = resource.brandRes,
                nameRes = resource.nameRes,
                categoryKey = MaterialCategoryKey.FERTILIZER,
                categoryTitleRes = R.string.catalog_material_category_fertilizer_title,
                keywordRes = resource.keywordRes
            )
        }

    init {
        check(definitions.size == FERTILIZER_PRODUCT_COUNT)
        check(definitions.map(AquariumMaterialDefinition::id).distinct().size == definitions.size)
        check(definitions.all { definition -> definition.id.startsWith("fertilizer_") })
        check(
            fertilizerCatalogResources
                .map(FertilizerCatalogResource::brandRes)
                .distinct()
                .size == FERTILIZER_BRAND_COUNT
        )
    }
}

private fun fertilizer(
    id: String,
    brandRes: Int,
    nameRes: Int,
    keywordRes: List<Int>
): FertilizerCatalogResource = FertilizerCatalogResource(
    id = id,
    brandRes = brandRes,
    nameRes = nameRes,
    keywordRes = keywordRes
)
