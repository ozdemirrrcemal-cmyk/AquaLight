package com.aqua.aqualight.application.aquarium

private const val GRAVEL_ID_PREFIX = "gravel_"
private const val GRAVEL_ID_PADDING = 4
private const val EXPECTED_GRAVEL_RECORD_COUNT = 181

private data class GravelEvidenceGroup(
    val firstProductId: String,
    val lastProductId: String,
    val sourceOrganization: String
)

private val gravelEvidenceGroups = listOf(
    GravelEvidenceGroup("gravel_0001", "gravel_0002", "Aqua Design Amano"),
    GravelEvidenceGroup("gravel_0003", "gravel_0034", "Dennerle"),
    GravelEvidenceGroup("gravel_0035", "gravel_0067", "WIO"),
    GravelEvidenceGroup("gravel_0068", "gravel_0081", "JBL"),
    GravelEvidenceGroup("gravel_0082", "gravel_0101", "sera"),
    GravelEvidenceGroup("gravel_0102", "gravel_0116", "Seachem"),
    GravelEvidenceGroup("gravel_0117", "gravel_0135", "CaribSea"),
    GravelEvidenceGroup("gravel_0136", "gravel_0145", "Aquael"),
    GravelEvidenceGroup("gravel_0146", "gravel_0158", "ReeFlowers"),
    GravelEvidenceGroup("gravel_0159", "gravel_0168", "CrystalPro Aquatics"),
    GravelEvidenceGroup("gravel_0169", "gravel_0175", "AMTRA"),
    GravelEvidenceGroup("gravel_0176", "gravel_0181", "PRODAC International")
)

/**
 * Exact product-id based substrate semantics used outside presentation.
 *
 * Records are intentionally keyed by stable material catalog IDs. Display-name, brand, translated
 * text and free-form note parsing are forbidden fallbacks.
 *
 * Verification source URLs are deliberately kept out of the runtime application catalog.
 */
object AquariumSubstrateMetadataCatalog {
    private const val SUBSTRATE_REVIEW_DATE = "2026-09-16"
    private const val GRAVEL_CATALOG_REVIEW_DATE = "2026-09-23"
    const val EXPECTED_RECORD_COUNT = 185

    private data class Record(
        val categoryKey: String,
        val metadata: AquariumSubstrateProductMetadata
    )

    private val records: Map<String, Record> = (
        listOf(
            "substrate_chihiros_aquasoil_9l".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.ACTIVE_SOIL,
                "Chihiros Aquatic Studio",
                "chihiros_aqua_soil_launch"
            ),
            "substrate_chihiros_aquasoil_3l".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.ACTIVE_SOIL,
                "Chihiros Aquatic Studio",
                "chihiros_aqua_soil_launch"
            ),
            "substrate_ada_tourmaline_bc".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.ADDITIVE,
                "Aqua Design Amano",
                "ada_tourmaline_bc"
            ),
            "substrate_dennerle_deponitmix_4_8kg".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.NUTRIENT_BASE,
                "Dennerle",
                "dennerle_deponit_mix_pro"
            )
        ) + replacementGravelRecords()
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

    private fun replacementGravelRecords(): List<Pair<String, Record>> {
        /*
         * None of the reviewed Gravel products is an active soil.
         *
         * INERT is the existing AquaLight gravel semantic for "non-active substrate". It must not
         * be read as "chemically neutral": some mineral products in this catalog can buffer or
         * raise pH/KH/GH, but they do not provide the acidifying/softening active-soil behavior
         * consumed by AquaLight's active-soil care rules.
         */
        val records = gravelEvidenceGroups.flatMap { group ->
            productIds(group).map { productId ->
                productId.verified(
                    categoryKey = AquariumMaterialCategoryKeys.GRAVEL,
                    semantic = AquariumSubstrateSemantic.INERT,
                    sourceOrganization = group.sourceOrganization,
                    sourceRecordId = productId,
                    reviewedOn = GRAVEL_CATALOG_REVIEW_DATE
                )
            }
        }
        check(records.size == EXPECTED_GRAVEL_RECORD_COUNT)
        return records
    }

    private fun productIds(group: GravelEvidenceGroup): List<String> {
        val firstNumber = gravelProductNumber(group.firstProductId)
        val lastNumber = gravelProductNumber(group.lastProductId)
        require(firstNumber <= lastNumber)
        return (firstNumber..lastNumber).map(::gravelProductId)
    }

    private fun gravelProductNumber(productId: String): Int {
        require(productId.startsWith(GRAVEL_ID_PREFIX))
        return productId.removePrefix(GRAVEL_ID_PREFIX).toInt()
    }

    private fun gravelProductId(index: Int): String {
        require(index > 0)
        return GRAVEL_ID_PREFIX + index.toString().padStart(GRAVEL_ID_PADDING, '0')
    }

    private fun String.verified(
        categoryKey: String,
        semantic: AquariumSubstrateSemantic,
        sourceOrganization: String,
        sourceRecordId: String,
        reviewedOn: String = SUBSTRATE_REVIEW_DATE
    ): Pair<String, Record> = this to Record(
        categoryKey = categoryKey,
        metadata = AquariumSubstrateProductMetadata(
            semantic = semantic,
            evidenceStatus = AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT,
            sourceOrganization = sourceOrganization,
            sourceRecordId = sourceRecordId,
            reviewedOn = reviewedOn
        )
    )
}
