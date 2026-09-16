package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumSubstrateEvidenceStatus
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductMetadata
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic

object GravelCatalog {

    private const val REVIEW_DATE = "2026-09-16"

    private val adaAquaGravelMetadata = verifiedInertMetadata(
        sourceOrganization = "Aqua Design Amano",
        sourceRecordId = "ada_aqua_gravel",
        sourceUrl = "https://www.adana.co.jp/en/contents/products/na_substrate/detail04.html"
    )
    private val dennerleNanoGravelMetadata = verifiedInertMetadata(
        sourceOrganization = "Dennerle",
        sourceRecordId = "dennerle_nano_shrimp_gravel",
        sourceUrl = "https://dennerle.com/en/products/nano-shrimp-gravel"
    )
    private val jblSansibarDarkMetadata = verifiedInertMetadata(
        sourceOrganization = "JBL",
        sourceRecordId = "jbl_sansibar_dark",
        sourceUrl = JBL_SUBSTRATE_GUIDE_URL
    )
    private val jblSansibarWhiteMetadata = verifiedInertMetadata(
        sourceOrganization = "JBL",
        sourceRecordId = "jbl_sansibar_white",
        sourceUrl = JBL_SUBSTRATE_GUIDE_URL
    )
    private val aquaelBasaltMetadata = verifiedInertMetadata(
        sourceOrganization = "Aquael",
        sourceRecordId = "aquael_basalt_gravel",
        sourceUrl = "https://www.aquael.com/products/aquaristics/substrates-gravels/bazaltowe/"
    )
    private val genericRiverSandMetadata = AquariumSubstrateProductMetadata(
        semantic = AquariumSubstrateSemantic.UNKNOWN,
        evidenceStatus = AquariumSubstrateEvidenceStatus.UNVERIFIED_GENERIC,
        sourceOrganization = "AquaLight catalog",
        sourceRecordId = "generic_natural_river_sand",
        sourceUrl = null,
        reviewedOn = REVIEW_DATE
    )

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "gravel_ada_aqua_gravel_s",
            brandRes = R.string.catalog_brand_ada,
            nameRes = R.string.catalog_material_gravel_ada_aqua_gravel_s_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_sand,
                R.string.catalog_keyword_stone,
                R.string.catalog_keyword_ada,
                R.string.catalog_keyword_aqua_gravel
            ),
            substrateMetadata = adaAquaGravelMetadata
        ),
        AquariumMaterialDefinition(
            id = "gravel_ada_aqua_gravel_m",
            brandRes = R.string.catalog_brand_ada,
            nameRes = R.string.catalog_material_gravel_ada_aqua_gravel_m_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_sand,
                R.string.catalog_keyword_stone,
                R.string.catalog_keyword_ada,
                R.string.catalog_keyword_aqua_gravel
            ),
            substrateMetadata = adaAquaGravelMetadata
        ),
        AquariumMaterialDefinition(
            id = "gravel_dennerle_nano_gravel_black",
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_dennerle_nano_gravel_black_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_black,
                R.string.catalog_keyword_nano,
                R.string.catalog_keyword_dennerle
            ),
            substrateMetadata = dennerleNanoGravelMetadata
        ),
        AquariumMaterialDefinition(
            id = "gravel_dennerle_nano_gravel_natural",
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_gravel_dennerle_nano_gravel_natural_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_natural,
                R.string.catalog_keyword_nano,
                R.string.catalog_keyword_dennerle
            ),
            substrateMetadata = dennerleNanoGravelMetadata
        ),
        AquariumMaterialDefinition(
            id = "gravel_jbl_sansibar_dark",
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_jbl_sansibar_dark_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_sand,
                R.string.catalog_keyword_black,
                R.string.catalog_keyword_dark,
                R.string.catalog_keyword_jbl
            ),
            substrateMetadata = jblSansibarDarkMetadata
        ),
        AquariumMaterialDefinition(
            id = "gravel_jbl_sansibar_white",
            brandRes = R.string.catalog_brand_jbl,
            nameRes = R.string.catalog_material_gravel_jbl_sansibar_white_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_sand,
                R.string.catalog_keyword_white,
                R.string.catalog_keyword_jbl
            ),
            substrateMetadata = jblSansibarWhiteMetadata
        ),
        AquariumMaterialDefinition(
            id = "gravel_aquael_basaltsand",
            brandRes = R.string.catalog_brand_aquael,
            nameRes = R.string.catalog_material_gravel_aquael_basaltsand_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_sand,
                R.string.catalog_keyword_basalt,
                R.string.catalog_keyword_black,
                R.string.catalog_keyword_aquael
            ),
            substrateMetadata = aquaelBasaltMetadata
        ),
        AquariumMaterialDefinition(
            id = "gravel_natural_river_sand",
            brandRes = 0,
            nameRes = R.string.catalog_material_gravel_natural_river_sand_name,
            categoryKey = MaterialCategoryKey.GRAVEL,
            categoryTitleRes = R.string.catalog_material_category_gravel_title,
            keywordRes = listOf(
                R.string.catalog_keyword_gravel,
                R.string.catalog_keyword_sand,
                R.string.catalog_keyword_river,
                R.string.catalog_keyword_natural
            ),
            substrateMetadata = genericRiverSandMetadata
        )
    )

    private fun verifiedInertMetadata(
        sourceOrganization: String,
        sourceRecordId: String,
        sourceUrl: String
    ): AquariumSubstrateProductMetadata = AquariumSubstrateProductMetadata(
        semantic = AquariumSubstrateSemantic.INERT,
        evidenceStatus = AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT,
        sourceOrganization = sourceOrganization,
        sourceRecordId = sourceRecordId,
        sourceUrl = sourceUrl,
        reviewedOn = REVIEW_DATE
    )

    private const val JBL_SUBSTRATE_GUIDE_URL =
        "https://www.jbl.de/en/theme-world/essential_section/57/" +
            "jbl-themeworld-for-your-hobby?country=lv"
}
