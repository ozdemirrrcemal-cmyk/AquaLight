package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumSubstrateMetadataCatalog
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductIds
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductMetadata
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic

private val substrateCatalogResources = listOf(
    R.string.catalog_brand_chihiros to R.string.catalog_material_substrate_chihiros_aquasoil_3l_name,
    R.string.catalog_brand_chihiros to R.string.catalog_material_substrate_chihiros_aquasoil_9l_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_ada_tourmaline_bc_name,
    R.string.catalog_brand_dennerle to R.string.catalog_material_substrate_dennerle_deponitmix_4_8kg_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0005_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0006_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0007_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0008_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0009_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0010_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0011_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0012_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0013_name,
    R.string.catalog_brand_ada to R.string.catalog_material_substrate_0014_name,
    R.string.catalog_brand_tropica to R.string.catalog_material_substrate_0015_name,
    R.string.catalog_brand_tropica to R.string.catalog_material_substrate_0016_name,
    R.string.catalog_brand_tropica to R.string.catalog_material_substrate_0017_name,
    R.string.catalog_brand_tropica to R.string.catalog_material_substrate_0018_name,
    R.string.catalog_brand_tropica to R.string.catalog_material_substrate_0019_name,
    R.string.catalog_brand_tropica to R.string.catalog_material_substrate_0020_name,
    R.string.catalog_brand_tropica to R.string.catalog_material_substrate_0021_name,
    R.string.catalog_brand_dennerle to R.string.catalog_material_substrate_0022_name,
    R.string.catalog_brand_dennerle to R.string.catalog_material_substrate_0023_name,
    R.string.catalog_brand_dennerle to R.string.catalog_material_substrate_0024_name,
    R.string.catalog_brand_dennerle to R.string.catalog_material_substrate_0025_name,
    R.string.catalog_brand_dennerle to R.string.catalog_material_substrate_0026_name,
    R.string.catalog_brand_dennerle to R.string.catalog_material_substrate_0027_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0028_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0029_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0030_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0031_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0032_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0033_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0034_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0035_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0036_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0037_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0038_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0039_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0040_name,
    R.string.catalog_brand_jbl to R.string.catalog_material_substrate_0041_name,
    R.string.catalog_substrate_brand_fluval to R.string.catalog_material_substrate_0042_name,
    R.string.catalog_substrate_brand_fluval to R.string.catalog_material_substrate_0043_name,
    R.string.catalog_substrate_brand_fluval to R.string.catalog_material_substrate_0044_name,
    R.string.catalog_substrate_brand_fluval to R.string.catalog_material_substrate_0045_name,
    R.string.catalog_substrate_brand_fluval to R.string.catalog_material_substrate_0046_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0047_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0048_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0049_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0050_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0051_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0052_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0053_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0054_name,
    R.string.catalog_brand_oase to R.string.catalog_material_substrate_0055_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0056_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0057_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0058_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0059_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0060_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0061_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0062_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0063_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0064_name,
    R.string.catalog_brand_aquario to R.string.catalog_material_substrate_0065_name,
    R.string.catalog_substrate_brand_uns to R.string.catalog_material_substrate_0066_name,
    R.string.catalog_substrate_brand_uns to R.string.catalog_material_substrate_0067_name,
    R.string.catalog_substrate_brand_uns to R.string.catalog_material_substrate_0068_name,
    R.string.catalog_substrate_brand_uns to R.string.catalog_material_substrate_0069_name,
    R.string.catalog_substrate_brand_uns to R.string.catalog_material_substrate_0070_name,
    R.string.catalog_substrate_brand_uns to R.string.catalog_material_substrate_0071_name,
    R.string.catalog_substrate_brand_uns to R.string.catalog_material_substrate_0072_name,
    R.string.catalog_substrate_brand_2hr_aquarist to R.string.catalog_material_substrate_0073_name,
    R.string.catalog_substrate_brand_2hr_aquarist to R.string.catalog_material_substrate_0074_name,
    R.string.catalog_substrate_brand_glasgarten to R.string.catalog_material_substrate_0075_name,
    R.string.catalog_substrate_brand_glasgarten to R.string.catalog_material_substrate_0076_name,
    R.string.catalog_substrate_brand_glasgarten to R.string.catalog_material_substrate_0077_name,
    R.string.catalog_substrate_brand_glasgarten to R.string.catalog_material_substrate_0078_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0079_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0080_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0081_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0082_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0083_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0084_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0085_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0086_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0087_name,
    R.string.catalog_substrate_brand_sl_aqua to R.string.catalog_material_substrate_0088_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0089_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0090_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0091_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0092_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0093_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0094_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0095_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0096_name,
    R.string.catalog_substrate_brand_jun to R.string.catalog_material_substrate_0097_name,
    R.string.catalog_brand_ista to R.string.catalog_material_substrate_0098_name,
    R.string.catalog_brand_ista to R.string.catalog_material_substrate_0099_name,
    R.string.catalog_brand_ista to R.string.catalog_material_substrate_0100_name,
    R.string.catalog_brand_ista to R.string.catalog_material_substrate_0101_name,
    R.string.catalog_brand_ista to R.string.catalog_material_substrate_0102_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0103_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0104_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0105_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0106_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0107_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0108_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0109_name,
    R.string.catalog_substrate_brand_brightwell_aquatics to R.string.catalog_material_substrate_0110_name,
    R.string.catalog_substrate_brand_ebi_gold to R.string.catalog_material_substrate_0111_name,
    R.string.catalog_substrate_brand_ebi_gold to R.string.catalog_material_substrate_0112_name,
    R.string.catalog_substrate_brand_ebi_gold to R.string.catalog_material_substrate_0113_name,
    R.string.catalog_substrate_brand_ebi_gold to R.string.catalog_material_substrate_0114_name,
    R.string.catalog_substrate_brand_tetra to R.string.catalog_material_substrate_0115_name,
    R.string.catalog_substrate_brand_tetra to R.string.catalog_material_substrate_0116_name,
    R.string.catalog_substrate_brand_tetra to R.string.catalog_material_substrate_0117_name,
    R.string.catalog_substrate_brand_sera to R.string.catalog_material_substrate_0118_name,
    R.string.catalog_substrate_brand_sera to R.string.catalog_material_substrate_0119_name,
    R.string.catalog_substrate_brand_aquaforest to R.string.catalog_material_substrate_0120_name,
    R.string.catalog_substrate_brand_aquaforest to R.string.catalog_material_substrate_0121_name,
    R.string.catalog_substrate_brand_aquaforest to R.string.catalog_material_substrate_0122_name,
    R.string.catalog_substrate_brand_yokuchi to R.string.catalog_material_substrate_0123_name,
    R.string.catalog_substrate_brand_yokuchi to R.string.catalog_material_substrate_0124_name,
    R.string.catalog_substrate_brand_benibachi to R.string.catalog_material_substrate_0125_name,
    R.string.catalog_substrate_brand_shrimps_forever to R.string.catalog_material_substrate_0126_name,
    R.string.catalog_substrate_brand_prize to R.string.catalog_material_substrate_0127_name,
    R.string.catalog_substrate_brand_prize to R.string.catalog_material_substrate_0128_name,
    R.string.catalog_substrate_brand_prize to R.string.catalog_material_substrate_0129_name,
    R.string.catalog_substrate_brand_prize to R.string.catalog_material_substrate_0130_name,
    R.string.catalog_substrate_brand_seachem_aquavitro to R.string.catalog_material_substrate_0131_name,
    R.string.catalog_substrate_brand_seachem to R.string.catalog_material_substrate_0132_name,
    R.string.catalog_substrate_brand_seachem to R.string.catalog_material_substrate_0133_name,
    R.string.catalog_substrate_brand_seachem to R.string.catalog_material_substrate_0134_name,
    R.string.catalog_substrate_brand_seachem to R.string.catalog_material_substrate_0135_name,
    R.string.catalog_substrate_brand_caribsea to R.string.catalog_material_substrate_0136_name,
    R.string.catalog_substrate_brand_caribsea to R.string.catalog_material_substrate_0137_name,
    R.string.catalog_substrate_brand_eurostar to R.string.catalog_material_substrate_0138_name
)

object SubstrateCatalog {

    val definitions: List<AquariumMaterialDefinition> =
        substrateCatalogResources.mapIndexed { index, (brandRes, nameRes) ->
            substrate(
                productId = AquariumSubstrateProductIds.productId(index + 1),
                brandRes = brandRes,
                nameRes = nameRes
            )
        }

    init {
        check(definitions.size == AquariumSubstrateProductIds.EXPECTED_CATALOG_PRODUCT_COUNT)
    }
}

private fun substrate(
    productId: String,
    brandRes: Int,
    nameRes: Int
): AquariumMaterialDefinition {
    val substrateMetadata = metadata(productId)
    val semanticKeyword = when (substrateMetadata.semantic) {
        AquariumSubstrateSemantic.ACTIVE_SOIL -> R.string.catalog_keyword_aquasoil
        AquariumSubstrateSemantic.NUTRIENT_BASE -> R.string.catalog_keyword_base_layer
        AquariumSubstrateSemantic.ADDITIVE -> R.string.catalog_keyword_additive
        AquariumSubstrateSemantic.INERT,
        AquariumSubstrateSemantic.UNKNOWN -> null
        AquariumSubstrateSemantic.NOT_APPLICABLE -> error(
            "Substrate metadata cannot be NOT_APPLICABLE"
        )
    }

    return AquariumMaterialDefinition(
        id = productId,
        brandRes = brandRes,
        nameRes = nameRes,
        categoryKey = MaterialCategoryKey.SUBSTRATE,
        categoryTitleRes = R.string.catalog_material_category_substrate_title,
        keywordRes = listOfNotNull(
            R.string.catalog_keyword_substrate,
            R.string.catalog_keyword_soil,
            semanticKeyword,
            brandKeywordRes(brandRes),
            R.string.catalog_keyword_plant
        ),
        substrateMetadata = substrateMetadata
    )
}

private fun brandKeywordRes(brandRes: Int): Int = when (brandRes) {
    R.string.catalog_brand_chihiros -> R.string.catalog_keyword_chihiros
    R.string.catalog_brand_ada -> R.string.catalog_keyword_ada
    R.string.catalog_brand_dennerle -> R.string.catalog_keyword_dennerle
    R.string.catalog_brand_jbl -> R.string.catalog_keyword_jbl
    R.string.catalog_brand_oase -> R.string.catalog_keyword_oase
    R.string.catalog_brand_aquario -> R.string.catalog_keyword_aquario
    R.string.catalog_brand_ista -> R.string.catalog_keyword_ista
    else -> brandRes
}

private fun metadata(productId: String): AquariumSubstrateProductMetadata = requireNotNull(
    AquariumSubstrateMetadataCatalog.metadata(productId, MaterialCategoryKey.SUBSTRATE)
)
