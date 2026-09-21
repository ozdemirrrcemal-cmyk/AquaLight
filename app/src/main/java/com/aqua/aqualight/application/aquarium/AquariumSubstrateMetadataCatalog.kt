package com.aqua.aqualight.application.aquarium

/**
 * Exact product-id based substrate semantics used outside presentation.
 *
 * Records are intentionally keyed by stable material catalog IDs. Display-name, brand, translated
 * text and free-form note parsing are forbidden fallbacks.
 */
object AquariumSubstrateMetadataCatalog {
    private const val REVIEW_DATE = "2026-09-16"
    const val EXPECTED_RECORD_COUNT = 12

    private data class Record(
        val categoryKey: String,
        val metadata: AquariumSubstrateProductMetadata
    )

    private val records: Map<String, Record> = listOf(
        verified(
            "substrate_chihiros_aquasoil_9l",
            AquariumMaterialCategoryKeys.SUBSTRATE,
            AquariumSubstrateSemantic.ACTIVE_SOIL,
            "Chihiros Aquatic Studio",
            "chihiros_aqua_soil_launch",
            "https://www.facebook.com/chihirosaquatic/posts/606277074868748/"
        ),
        verified(
            "substrate_chihiros_aquasoil_3l",
            AquariumMaterialCategoryKeys.SUBSTRATE,
            AquariumSubstrateSemantic.ACTIVE_SOIL,
            "Chihiros Aquatic Studio",
            "chihiros_aqua_soil_launch",
            "https://www.facebook.com/chihirosaquatic/posts/606277074868748/"
        ),
        verified(
            "substrate_ada_tourmaline_bc",
            AquariumMaterialCategoryKeys.SUBSTRATE,
            AquariumSubstrateSemantic.ADDITIVE,
            "Aqua Design Amano",
            "ada_tourmaline_bc",
            "https://www.adana.co.jp/en/contents/products/na_substrate/detail05.html"
        ),
        verified(
            "substrate_dennerle_deponitmix_4_8kg",
            AquariumMaterialCategoryKeys.SUBSTRATE,
            AquariumSubstrateSemantic.NUTRIENT_BASE,
            "Dennerle",
            "dennerle_deponit_mix_pro",
            "https://dennerle.com/en/products/deponit-mix-pro"
        ),
        verified(
            "gravel_ada_aqua_gravel_s",
            AquariumMaterialCategoryKeys.GRAVEL,
            AquariumSubstrateSemantic.INERT,
            "Aqua Design Amano",
            "ada_aqua_gravel",
            "https://www.adana.co.jp/en/contents/products/na_substrate/detail04.html"
        ),
        verified(
            "gravel_ada_aqua_gravel_m",
            AquariumMaterialCategoryKeys.GRAVEL,
            AquariumSubstrateSemantic.INERT,
            "Aqua Design Amano",
            "ada_aqua_gravel",
            "https://www.adana.co.jp/en/contents/products/na_substrate/detail04.html"
        ),
        verified(
            "gravel_dennerle_nano_gravel_black",
            AquariumMaterialCategoryKeys.GRAVEL,
            AquariumSubstrateSemantic.INERT,
            "Dennerle",
            "dennerle_nano_shrimp_gravel",
            "https://dennerle.com/en/products/nano-shrimp-gravel"
        ),
        verified(
            "gravel_dennerle_nano_gravel_natural",
            AquariumMaterialCategoryKeys.GRAVEL,
            AquariumSubstrateSemantic.INERT,
            "Dennerle",
            "dennerle_nano_shrimp_gravel",
            "https://dennerle.com/en/products/nano-shrimp-gravel"
        ),
        verified(
            "gravel_jbl_sansibar_dark",
            AquariumMaterialCategoryKeys.GRAVEL,
            AquariumSubstrateSemantic.INERT,
            "JBL",
            "jbl_sansibar_dark",
            JBL_SUBSTRATE_GUIDE_URL
        ),
        verified(
            "gravel_jbl_sansibar_white",
            AquariumMaterialCategoryKeys.GRAVEL,
            AquariumSubstrateSemantic.INERT,
            "JBL",
            "jbl_sansibar_white",
            JBL_SUBSTRATE_GUIDE_URL
        ),
        verified(
            "gravel_aquael_basaltsand",
            AquariumMaterialCategoryKeys.GRAVEL,
            AquariumSubstrateSemantic.INERT,
            "Aquael",
            "aquael_basalt_gravel",
            "https://www.aquael.com/products/aquaristics/substrates-gravels/bazaltowe/"
        ),
        unverified(
            "gravel_natural_river_sand",
            AquariumMaterialCategoryKeys.GRAVEL,
            "generic_natural_river_sand"
        )
    ).associate { (productId, record) -> productId to record }

    init {
        require(records.size == EXPECTED_RECORD_COUNT)
    }

    fun metadata(
        productId: String,
        categoryKey: String
    ): AquariumSubstrateProductMetadata? = records[productId]
        ?.takeIf { record -> record.categoryKey == categoryKey }
        ?.metadata

    fun resolveSemantic(
        productId: String,
        categoryKey: String
    ): AquariumSubstrateSemantic {
        if (
            categoryKey != AquariumMaterialCategoryKeys.SUBSTRATE &&
            categoryKey != AquariumMaterialCategoryKeys.GRAVEL
        ) {
            return AquariumSubstrateSemantic.NOT_APPLICABLE
        }
        return metadata(productId, categoryKey)?.semantic ?: AquariumSubstrateSemantic.UNKNOWN
    }

    @Suppress("LongParameterList")
    private fun verified(
        productId: String,
        categoryKey: String,
        semantic: AquariumSubstrateSemantic,
        sourceOrganization: String,
        sourceRecordId: String,
        sourceUrl: String
    ): Pair<String, Record> = productId to Record(
        categoryKey = categoryKey,
        metadata = AquariumSubstrateProductMetadata(
            semantic = semantic,
            evidenceStatus = AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT,
            sourceOrganization = sourceOrganization,
            sourceRecordId = sourceRecordId,
            sourceUrl = sourceUrl,
            reviewedOn = REVIEW_DATE
        )
    )

    private fun unverified(
        productId: String,
        categoryKey: String,
        sourceRecordId: String
    ): Pair<String, Record> = productId to Record(
        categoryKey = categoryKey,
        metadata = AquariumSubstrateProductMetadata(
            semantic = AquariumSubstrateSemantic.UNKNOWN,
            evidenceStatus = AquariumSubstrateEvidenceStatus.UNVERIFIED_GENERIC,
            sourceOrganization = "AquaLight catalog",
            sourceRecordId = sourceRecordId,
            sourceUrl = null,
            reviewedOn = REVIEW_DATE
        )
    )

    private const val JBL_SUBSTRATE_GUIDE_URL =
        "https://www.jbl.de/en/theme-world/essential_section/57/" +
            "jbl-themeworld-for-your-hobby?country=lv"
}
