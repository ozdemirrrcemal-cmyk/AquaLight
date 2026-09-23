package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumSubstrateMetadataCatalog
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductMetadata

private val gravelKeywords = listOf(
    R.string.catalog_keyword_gravel,
    R.string.catalog_keyword_sand,
    R.string.catalog_keyword_stone,
    R.string.catalog_keyword_substrate
)

object GravelCatalog {

    val definitions: List<AquariumMaterialDefinition> = listOf(
        gravel(
            index = 1,
            brandRes = R.string.catalog_brand_ada,
            nameRes = R.string.catalog_material_gravel_0001_name
        ),
        gravel(
            index = 2,
            brandRes = R.string.catalog_brand_ada,
            nameRes = R.string.catalog_material_gravel_0002_name
        ),
        gravel(
            index = 3,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0003_name
        ),
        gravel(
            index = 4,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0004_name
        ),
        gravel(
            index = 5,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0005_name
        ),
        gravel(
            index = 6,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0006_name
        ),
        gravel(
            index = 7,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0007_name
        ),
        gravel(
            index = 8,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0008_name
        ),
        gravel(
            index = 9,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0009_name
        ),
        gravel(
            index = 10,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0010_name
        ),
        gravel(
            index = 11,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0011_name
        ),
        gravel(
            index = 12,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0012_name
        ),
        gravel(
            index = 13,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0013_name
        ),
        gravel(
            index = 14,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0014_name
        ),
        gravel(
            index = 15,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0015_name
        ),
        gravel(
            index = 16,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0016_name
        ),
        gravel(
            index = 17,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0017_name
        ),
        gravel(
            index = 18,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0018_name
        ),
        gravel(
            index = 19,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0019_name
        ),
        gravel(
            index = 20,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0020_name
        ),
        gravel(
            index = 21,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0021_name
        ),
        gravel(
            index = 22,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0022_name
        ),
        gravel(
            index = 23,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0023_name
        ),
        gravel(
            index = 24,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0024_name
        ),
        gravel(
            index = 25,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0025_name
        ),
        gravel(
            index = 26,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0026_name
        ),
        gravel(
            index = 27,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0027_name
        ),
        gravel(
            index = 28,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0028_name
        ),
        gravel(
            index = 29,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0029_name
        ),
        gravel(
            index = 30,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0030_name
        ),
        gravel(
            index = 31,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0031_name
        ),
        gravel(
            index = 32,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0032_name
        ),
        gravel(
            index = 33,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0033_name
        ),
        gravel(
            index = 34,
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_0034_name
        ),
        gravel(
            index = 35,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0035_name
        ),
        gravel(
            index = 36,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0036_name
        ),
        gravel(
            index = 37,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0037_name
        ),
        gravel(
            index = 38,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0038_name
        ),
        gravel(
            index = 39,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0039_name
        ),
        gravel(
            index = 40,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0040_name
        ),
        gravel(
            index = 41,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0041_name
        ),
        gravel(
            index = 42,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0042_name
        ),
        gravel(
            index = 43,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0043_name
        ),
        gravel(
            index = 44,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0044_name
        ),
        gravel(
            index = 45,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0045_name
        ),
        gravel(
            index = 46,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0046_name
        ),
        gravel(
            index = 47,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0047_name
        ),
        gravel(
            index = 48,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0048_name
        ),
        gravel(
            index = 49,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0049_name
        ),
        gravel(
            index = 50,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0050_name
        ),
        gravel(
            index = 51,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0051_name
        ),
        gravel(
            index = 52,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0052_name
        ),
        gravel(
            index = 53,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0053_name
        ),
        gravel(
            index = 54,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0054_name
        ),
        gravel(
            index = 55,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0055_name
        ),
        gravel(
            index = 56,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0056_name
        ),
        gravel(
            index = 57,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0057_name
        ),
        gravel(
            index = 58,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0058_name
        ),
        gravel(
            index = 59,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0059_name
        ),
        gravel(
            index = 60,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0060_name
        ),
        gravel(
            index = 61,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0061_name
        ),
        gravel(
            index = 62,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0062_name
        ),
        gravel(
            index = 63,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0063_name
        ),
        gravel(
            index = 64,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0064_name
        ),
        gravel(
            index = 65,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0065_name
        ),
        gravel(
            index = 66,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0066_name
        ),
        gravel(
            index = 67,
            brandRes = R.string.catalog_gravel_brand_wio,
            nameRes = R.string.catalog_material_gravel_0067_name
        ),
        gravel(
            index = 68,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0068_name
        ),
        gravel(
            index = 69,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0069_name
        ),
        gravel(
            index = 70,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0070_name
        ),
        gravel(
            index = 71,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0071_name
        ),
        gravel(
            index = 72,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0072_name
        ),
        gravel(
            index = 73,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0073_name
        ),
        gravel(
            index = 74,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0074_name
        ),
        gravel(
            index = 75,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0075_name
        ),
        gravel(
            index = 76,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0076_name
        ),
        gravel(
            index = 77,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0077_name
        ),
        gravel(
            index = 78,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0078_name
        ),
        gravel(
            index = 79,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0079_name
        ),
        gravel(
            index = 80,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0080_name
        ),
        gravel(
            index = 81,
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_0081_name
        ),
        gravel(
            index = 82,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0082_name
        ),
        gravel(
            index = 83,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0083_name
        ),
        gravel(
            index = 84,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0084_name
        ),
        gravel(
            index = 85,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0085_name
        ),
        gravel(
            index = 86,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0086_name
        ),
        gravel(
            index = 87,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0087_name
        ),
        gravel(
            index = 88,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0088_name
        ),
        gravel(
            index = 89,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0089_name
        ),
        gravel(
            index = 90,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0090_name
        ),
        gravel(
            index = 91,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0091_name
        ),
        gravel(
            index = 92,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0092_name
        ),
        gravel(
            index = 93,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0093_name
        ),
        gravel(
            index = 94,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0094_name
        ),
        gravel(
            index = 95,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0095_name
        ),
        gravel(
            index = 96,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0096_name
        ),
        gravel(
            index = 97,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0097_name
        ),
        gravel(
            index = 98,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0098_name
        ),
        gravel(
            index = 99,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0099_name
        ),
        gravel(
            index = 100,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0100_name
        ),
        gravel(
            index = 101,
            brandRes = R.string.catalog_gravel_brand_sera,
            nameRes = R.string.catalog_material_gravel_0101_name
        ),
        gravel(
            index = 102,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0102_name
        ),
        gravel(
            index = 103,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0103_name
        ),
        gravel(
            index = 104,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0104_name
        ),
        gravel(
            index = 105,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0105_name
        ),
        gravel(
            index = 106,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0106_name
        ),
        gravel(
            index = 107,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0107_name
        ),
        gravel(
            index = 108,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0108_name
        ),
        gravel(
            index = 109,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0109_name
        ),
        gravel(
            index = 110,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0110_name
        ),
        gravel(
            index = 111,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0111_name
        ),
        gravel(
            index = 112,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0112_name
        ),
        gravel(
            index = 113,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0113_name
        ),
        gravel(
            index = 114,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0114_name
        ),
        gravel(
            index = 115,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0115_name
        ),
        gravel(
            index = 116,
            brandRes = R.string.catalog_gravel_brand_seachem,
            nameRes = R.string.catalog_material_gravel_0116_name
        ),
        gravel(
            index = 117,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0117_name
        ),
        gravel(
            index = 118,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0118_name
        ),
        gravel(
            index = 119,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0119_name
        ),
        gravel(
            index = 120,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0120_name
        ),
        gravel(
            index = 121,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0121_name
        ),
        gravel(
            index = 122,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0122_name
        ),
        gravel(
            index = 123,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0123_name
        ),
        gravel(
            index = 124,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0124_name
        ),
        gravel(
            index = 125,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0125_name
        ),
        gravel(
            index = 126,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0126_name
        ),
        gravel(
            index = 127,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0127_name
        ),
        gravel(
            index = 128,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0128_name
        ),
        gravel(
            index = 129,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0129_name
        ),
        gravel(
            index = 130,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0130_name
        ),
        gravel(
            index = 131,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0131_name
        ),
        gravel(
            index = 132,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0132_name
        ),
        gravel(
            index = 133,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0133_name
        ),
        gravel(
            index = 134,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0134_name
        ),
        gravel(
            index = 135,
            brandRes = R.string.catalog_gravel_brand_caribsea,
            nameRes = R.string.catalog_material_gravel_0135_name
        ),
        gravel(
            index = 136,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0136_name
        ),
        gravel(
            index = 137,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0137_name
        ),
        gravel(
            index = 138,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0138_name
        ),
        gravel(
            index = 139,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0139_name
        ),
        gravel(
            index = 140,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0140_name
        ),
        gravel(
            index = 141,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0141_name
        ),
        gravel(
            index = 142,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0142_name
        ),
        gravel(
            index = 143,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0143_name
        ),
        gravel(
            index = 144,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0144_name
        ),
        gravel(
            index = 145,
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_0145_name
        ),
        gravel(
            index = 146,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0146_name
        ),
        gravel(
            index = 147,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0147_name
        ),
        gravel(
            index = 148,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0148_name
        ),
        gravel(
            index = 149,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0149_name
        ),
        gravel(
            index = 150,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0150_name
        ),
        gravel(
            index = 151,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0151_name
        ),
        gravel(
            index = 152,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0152_name
        ),
        gravel(
            index = 153,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0153_name
        ),
        gravel(
            index = 154,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0154_name
        ),
        gravel(
            index = 155,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0155_name
        ),
        gravel(
            index = 156,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0156_name
        ),
        gravel(
            index = 157,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0157_name
        ),
        gravel(
            index = 158,
            brandRes = R.string.catalog_gravel_brand_reeflowers,
            nameRes = R.string.catalog_material_gravel_0158_name
        ),
        gravel(
            index = 159,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0159_name
        ),
        gravel(
            index = 160,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0160_name
        ),
        gravel(
            index = 161,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0161_name
        ),
        gravel(
            index = 162,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0162_name
        ),
        gravel(
            index = 163,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0163_name
        ),
        gravel(
            index = 164,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0164_name
        ),
        gravel(
            index = 165,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0165_name
        ),
        gravel(
            index = 166,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0166_name
        ),
        gravel(
            index = 167,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0167_name
        ),
        gravel(
            index = 168,
            brandRes = R.string.catalog_gravel_brand_crystalpro,
            nameRes = R.string.catalog_material_gravel_0168_name
        ),
        gravel(
            index = 169,
            brandRes = R.string.catalog_gravel_brand_amtra,
            nameRes = R.string.catalog_material_gravel_0169_name
        ),
        gravel(
            index = 170,
            brandRes = R.string.catalog_gravel_brand_amtra,
            nameRes = R.string.catalog_material_gravel_0170_name
        ),
        gravel(
            index = 171,
            brandRes = R.string.catalog_gravel_brand_amtra,
            nameRes = R.string.catalog_material_gravel_0171_name
        ),
        gravel(
            index = 172,
            brandRes = R.string.catalog_gravel_brand_amtra,
            nameRes = R.string.catalog_material_gravel_0172_name
        ),
        gravel(
            index = 173,
            brandRes = R.string.catalog_gravel_brand_amtra,
            nameRes = R.string.catalog_material_gravel_0173_name
        ),
        gravel(
            index = 174,
            brandRes = R.string.catalog_gravel_brand_amtra,
            nameRes = R.string.catalog_material_gravel_0174_name
        ),
        gravel(
            index = 175,
            brandRes = R.string.catalog_gravel_brand_amtra,
            nameRes = R.string.catalog_material_gravel_0175_name
        ),
        gravel(
            index = 176,
            brandRes = R.string.catalog_gravel_brand_prodac,
            nameRes = R.string.catalog_material_gravel_0176_name
        ),
        gravel(
            index = 177,
            brandRes = R.string.catalog_gravel_brand_prodac,
            nameRes = R.string.catalog_material_gravel_0177_name
        ),
        gravel(
            index = 178,
            brandRes = R.string.catalog_gravel_brand_prodac,
            nameRes = R.string.catalog_material_gravel_0178_name
        ),
        gravel(
            index = 179,
            brandRes = R.string.catalog_gravel_brand_prodac,
            nameRes = R.string.catalog_material_gravel_0179_name
        ),
        gravel(
            index = 180,
            brandRes = R.string.catalog_gravel_brand_prodac,
            nameRes = R.string.catalog_material_gravel_0180_name
        ),
        gravel(
            index = 181,
            brandRes = R.string.catalog_gravel_brand_prodac,
            nameRes = R.string.catalog_material_gravel_0181_name
        )
    )
}

private fun gravel(
    index: Int,
    brandRes: Int,
    nameRes: Int
): AquariumMaterialDefinition {
    val productId = gravelProductId(index)
    return AquariumMaterialDefinition(
        id = productId,
        brandRes = brandRes,
        nameRes = nameRes,
        categoryKey = MaterialCategoryKey.GRAVEL,
        categoryTitleRes = R.string.catalog_material_category_gravel_title,
        keywordRes = gravelKeywords,
        substrateMetadata = metadata(productId)
    )
}

private fun gravelProductId(index: Int): String {
    require(index in 1..181)
    return "gravel_${index.toString().padStart(4, '0')}"
}

private fun metadata(productId: String): AquariumSubstrateProductMetadata = requireNotNull(
    AquariumSubstrateMetadataCatalog.metadata(productId, MaterialCategoryKey.GRAVEL)
)
