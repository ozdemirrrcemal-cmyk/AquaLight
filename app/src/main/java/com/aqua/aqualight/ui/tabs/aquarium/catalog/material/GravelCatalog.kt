package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumSubstrateMetadataCatalog
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductMetadata

private const val GRAVEL_PRODUCT_COUNT = 181
private const val GRAVEL_ID_PADDING = 4
private const val GRAVEL_ID_PREFIX = "gravel_"

private data class GravelCatalogResource(
    val brandRes: Int,
    val nameRes: Int,
    val extraKeywordRes: List<Int>
)

private val gravelKeywords = listOf(
    R.string.catalog_keyword_gravel,
    R.string.catalog_keyword_sand,
    R.string.catalog_keyword_stone,
    R.string.catalog_keyword_substrate
)

private val gravelCatalogResources = listOf(
    gravelResource(
        R.string.catalog_brand_ada,
        R.string.catalog_material_gravel_0001_name,
        R.string.catalog_keyword_aqua_gravel
    ),
    gravelResource(
        R.string.catalog_brand_ada,
        R.string.catalog_material_gravel_0002_name,
        R.string.catalog_keyword_aqua_gravel
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0003_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0004_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0005_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0006_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0007_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0008_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0009_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0010_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0011_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0012_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0013_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0014_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0015_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0016_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0017_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0018_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0019_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0020_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0021_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0022_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0023_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0024_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0025_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0026_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0027_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_dark
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0028_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_white
    ),
    gravelResource(R.string.catalog_brand_dennerle, R.string.catalog_material_gravel_0029_name),
    gravelResource(R.string.catalog_brand_dennerle, R.string.catalog_material_gravel_0030_name),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0031_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_nano
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0032_name,
        R.string.catalog_keyword_nano
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0033_name,
        R.string.catalog_keyword_nano
    ),
    gravelResource(
        R.string.catalog_brand_dennerle,
        R.string.catalog_material_gravel_0034_name,
        R.string.catalog_keyword_nano,
        R.string.catalog_keyword_white
    ),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0035_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0036_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0037_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0038_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0039_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0040_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0041_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0042_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0043_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0044_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0045_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0046_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0047_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0048_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0049_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0050_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0051_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0052_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0053_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0054_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0055_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0056_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0057_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0058_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0059_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0060_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0061_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0062_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0063_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0064_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0065_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0066_name),
    gravelResource(R.string.catalog_gravel_brand_wio, R.string.catalog_material_gravel_0067_name),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0068_name),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0069_name),
    gravelResource(
        R.string.catalog_brand_jbl,
        R.string.catalog_material_gravel_0070_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_brand_jbl,
        R.string.catalog_material_gravel_0071_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0072_name),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0073_name),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0074_name),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0075_name),
    gravelResource(
        R.string.catalog_brand_jbl,
        R.string.catalog_material_gravel_0076_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_dark
    ),
    gravelResource(
        R.string.catalog_brand_jbl,
        R.string.catalog_material_gravel_0077_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_dark
    ),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0078_name),
    gravelResource(R.string.catalog_brand_jbl, R.string.catalog_material_gravel_0079_name),
    gravelResource(
        R.string.catalog_brand_jbl,
        R.string.catalog_material_gravel_0080_name,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_brand_jbl,
        R.string.catalog_material_gravel_0081_name,
        R.string.catalog_keyword_river
    ),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0082_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0083_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0084_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0085_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0086_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0087_name),
    gravelResource(
        R.string.catalog_gravel_brand_sera,
        R.string.catalog_material_gravel_0088_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_sera,
        R.string.catalog_material_gravel_0089_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0090_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0091_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0092_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0093_name),
    gravelResource(
        R.string.catalog_gravel_brand_sera,
        R.string.catalog_material_gravel_0094_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_sera,
        R.string.catalog_material_gravel_0095_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0096_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0097_name),
    gravelResource(
        R.string.catalog_gravel_brand_sera,
        R.string.catalog_material_gravel_0098_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_sera,
        R.string.catalog_material_gravel_0099_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0100_name),
    gravelResource(R.string.catalog_gravel_brand_sera, R.string.catalog_material_gravel_0101_name),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0102_name),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0103_name),
    gravelResource(
        R.string.catalog_gravel_brand_seachem,
        R.string.catalog_material_gravel_0104_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_seachem,
        R.string.catalog_material_gravel_0105_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_seachem,
        R.string.catalog_material_gravel_0106_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_dark
    ),
    gravelResource(
        R.string.catalog_gravel_brand_seachem,
        R.string.catalog_material_gravel_0107_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_dark
    ),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0108_name),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0109_name),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0110_name),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0111_name),
    gravelResource(
        R.string.catalog_gravel_brand_seachem,
        R.string.catalog_material_gravel_0112_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_seachem,
        R.string.catalog_material_gravel_0113_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0114_name),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0115_name),
    gravelResource(R.string.catalog_gravel_brand_seachem, R.string.catalog_material_gravel_0116_name),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0117_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0118_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0119_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0120_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0121_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0122_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0123_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0124_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0125_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0126_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0127_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0128_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0129_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0130_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0131_name,
        R.string.catalog_keyword_natural,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0132_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0133_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0134_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_caribsea,
        R.string.catalog_material_gravel_0135_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0136_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0137_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0138_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0139_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0140_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0141_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0142_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_basalt
    ),
    gravelResource(
        R.string.catalog_brand_aquael,
        R.string.catalog_material_gravel_0143_name,
        R.string.catalog_keyword_black,
        R.string.catalog_keyword_basalt
    ),
    gravelResource(R.string.catalog_brand_aquael, R.string.catalog_material_gravel_0144_name),
    gravelResource(R.string.catalog_brand_aquael, R.string.catalog_material_gravel_0145_name),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0146_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0147_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0148_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0149_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0150_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0151_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0152_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0153_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0154_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0155_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0156_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0157_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_reeflowers,
        R.string.catalog_material_gravel_0158_name,
        R.string.catalog_keyword_natural
    ),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0159_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0160_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0161_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0162_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0163_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0164_name,
        R.string.catalog_keyword_white
    ),
    gravelResource(R.string.catalog_gravel_brand_crystalpro, R.string.catalog_material_gravel_0165_name),
    gravelResource(R.string.catalog_gravel_brand_crystalpro, R.string.catalog_material_gravel_0166_name),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0167_name,
        R.string.catalog_keyword_river
    ),
    gravelResource(
        R.string.catalog_gravel_brand_crystalpro,
        R.string.catalog_material_gravel_0168_name,
        R.string.catalog_keyword_river
    ),
    gravelResource(R.string.catalog_gravel_brand_amtra, R.string.catalog_material_gravel_0169_name),
    gravelResource(R.string.catalog_gravel_brand_amtra, R.string.catalog_material_gravel_0170_name),
    gravelResource(R.string.catalog_gravel_brand_amtra, R.string.catalog_material_gravel_0171_name),
    gravelResource(R.string.catalog_gravel_brand_amtra, R.string.catalog_material_gravel_0172_name),
    gravelResource(R.string.catalog_gravel_brand_amtra, R.string.catalog_material_gravel_0173_name),
    gravelResource(R.string.catalog_gravel_brand_amtra, R.string.catalog_material_gravel_0174_name),
    gravelResource(R.string.catalog_gravel_brand_amtra, R.string.catalog_material_gravel_0175_name),
    gravelResource(R.string.catalog_gravel_brand_prodac, R.string.catalog_material_gravel_0176_name),
    gravelResource(R.string.catalog_gravel_brand_prodac, R.string.catalog_material_gravel_0177_name),
    gravelResource(
        R.string.catalog_gravel_brand_prodac,
        R.string.catalog_material_gravel_0178_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(
        R.string.catalog_gravel_brand_prodac,
        R.string.catalog_material_gravel_0179_name,
        R.string.catalog_keyword_black
    ),
    gravelResource(R.string.catalog_gravel_brand_prodac, R.string.catalog_material_gravel_0180_name),
    gravelResource(R.string.catalog_gravel_brand_prodac, R.string.catalog_material_gravel_0181_name)
)

object GravelCatalog {

    val definitions: List<AquariumMaterialDefinition> =
        gravelCatalogResources.mapIndexed { index, resource ->
            gravel(
                productId = gravelProductId(index + 1),
                resource = resource
            )
        }

    init {
        check(definitions.size == GRAVEL_PRODUCT_COUNT)
    }
}

private fun gravelResource(
    brandRes: Int,
    nameRes: Int,
    vararg extraKeywordRes: Int
): GravelCatalogResource = GravelCatalogResource(
    brandRes = brandRes,
    nameRes = nameRes,
    extraKeywordRes = extraKeywordRes.toList()
)

private fun gravel(
    productId: String,
    resource: GravelCatalogResource
): AquariumMaterialDefinition = AquariumMaterialDefinition(
    id = productId,
    brandRes = resource.brandRes,
    nameRes = resource.nameRes,
    categoryKey = MaterialCategoryKey.GRAVEL,
    categoryTitleRes = R.string.catalog_material_category_gravel_title,
    keywordRes = gravelKeywords + resource.extraKeywordRes,
    substrateMetadata = metadata(productId)
)

private fun gravelProductId(index: Int): String {
    require(index in 1..GRAVEL_PRODUCT_COUNT)
    return GRAVEL_ID_PREFIX + index.toString().padStart(GRAVEL_ID_PADDING, '0')
}

private fun metadata(productId: String): AquariumSubstrateProductMetadata = requireNotNull(
    AquariumSubstrateMetadataCatalog.metadata(productId, MaterialCategoryKey.GRAVEL)
)
