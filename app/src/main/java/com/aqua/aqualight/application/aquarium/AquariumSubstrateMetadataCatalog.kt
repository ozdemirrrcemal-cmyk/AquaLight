package com.aqua.aqualight.application.aquarium

private const val SUBSTRATE_ID_PREFIX = "substrate_"
private const val GRAVEL_ID_PREFIX = "gravel_"
private const val GRAVEL_ID_PADDING = 4
private const val EXPECTED_GRAVEL_RECORD_COUNT = 181
private const val SUBSTRATE_REVIEW_DATE = "2026-09-23"
private const val GRAVEL_CATALOG_REVIEW_DATE = "2026-09-23"

private data class SubstrateEvidenceGroup(
    val firstProductId: String,
    val lastProductId: String,
    val semantic: AquariumSubstrateSemantic,
    val sourceOrganization: String
)

private val substrateEvidenceGroups = listOf(
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0005",
        lastProductId = "substrate_0008",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Aqua Design Amano"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0009",
        lastProductId = "substrate_0011",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Aqua Design Amano"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0012",
        lastProductId = "substrate_0014",
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "Aqua Design Amano"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0015",
        lastProductId = "substrate_0018",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Tropica Aquarium Plants"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0019",
        lastProductId = "substrate_0021",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Tropica Aquarium Plants"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0022",
        lastProductId = "substrate_0025",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Dennerle"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0026",
        lastProductId = "substrate_0027",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Dennerle"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0028",
        lastProductId = "substrate_0035",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "JBL"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0036",
        lastProductId = "substrate_0039",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "JBL"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0040",
        lastProductId = "substrate_0041",
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "JBL"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0042",
        lastProductId = "substrate_0046",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Fluval"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0047",
        lastProductId = "substrate_0054",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "OASE"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0055",
        lastProductId = "substrate_0055",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "OASE"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0056",
        lastProductId = "substrate_0065",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Aquario"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0066",
        lastProductId = "substrate_0071",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Ultum Nature Systems"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0072",
        lastProductId = "substrate_0072",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Ultum Nature Systems"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0073",
        lastProductId = "substrate_0074",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "2HR Aquarist"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0075",
        lastProductId = "substrate_0078",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "GlasGarten"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0079",
        lastProductId = "substrate_0085",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "SL-Aqua"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0086",
        lastProductId = "substrate_0086",
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "SL-Aqua"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0087",
        lastProductId = "substrate_0088",
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "SL-Aqua"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0089",
        lastProductId = "substrate_0097",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "JUN"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0098",
        lastProductId = "substrate_0102",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "ISTA"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0103",
        lastProductId = "substrate_0108",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Brightwell Aquatics"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0109",
        lastProductId = "substrate_0109",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Brightwell Aquatics"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0110",
        lastProductId = "substrate_0110",
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "Brightwell Aquatics"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0111",
        lastProductId = "substrate_0114",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Ebi Gold"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0115",
        lastProductId = "substrate_0116",
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "Tetra"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0117",
        lastProductId = "substrate_0117",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Tetra"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0118",
        lastProductId = "substrate_0119",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "sera"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0120",
        lastProductId = "substrate_0120",
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Aquaforest"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0121",
        lastProductId = "substrate_0122",
        semantic = AquariumSubstrateSemantic.UNKNOWN,
        sourceOrganization = "Aquaforest"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0123",
        lastProductId = "substrate_0124",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Yokuchi"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0125",
        lastProductId = "substrate_0125",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Benibachi"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0126",
        lastProductId = "substrate_0126",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Shrimps Forever"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0127",
        lastProductId = "substrate_0130",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Prize"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0131",
        lastProductId = "substrate_0131",
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Seachem Laboratories"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0132",
        lastProductId = "substrate_0135",
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "Seachem Laboratories"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0136",
        lastProductId = "substrate_0137",
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "CaribSea"
    ),
    SubstrateEvidenceGroup(
        firstProductId = "substrate_0138",
        lastProductId = "substrate_0138",
        semantic = AquariumSubstrateSemantic.UNKNOWN,
        sourceOrganization = "EuroStar"
    )
)

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
    const val EXPECTED_RECORD_COUNT =
        EXPECTED_GRAVEL_RECORD_COUNT + AquariumSubstrateProductIds.EXPECTED_CATALOG_PRODUCT_COUNT

    private data class Record(
        val categoryKey: String,
        val metadata: AquariumSubstrateProductMetadata
    )

    private val records: Map<String, Record> = (
        replacementSubstrateRecords() + replacementGravelRecords()
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

    private fun replacementSubstrateRecords(): List<Pair<String, Record>> {
        val records = legacySubstrateRecords() + substrateEvidenceGroups.flatMap { group ->
            substrateProductIds(group).map { productId ->
                if (group.semantic == AquariumSubstrateSemantic.UNKNOWN) {
                    productId.unverified(
                        categoryKey = AquariumMaterialCategoryKeys.SUBSTRATE,
                        sourceOrganization = group.sourceOrganization,
                        sourceRecordId = productId
                    )
                } else {
                    productId.verified(
                        categoryKey = AquariumMaterialCategoryKeys.SUBSTRATE,
                        semantic = group.semantic,
                        sourceOrganization = group.sourceOrganization,
                        sourceRecordId = productId
                    )
                }
            }
        }
        check(records.size == AquariumSubstrateProductIds.EXPECTED_CATALOG_PRODUCT_COUNT)
        return records
    }

    private fun legacySubstrateRecords(): List<Pair<String, Record>> = listOf(
        "substrate_chihiros_aquasoil_3l".verified(
            AquariumMaterialCategoryKeys.SUBSTRATE,
            AquariumSubstrateSemantic.ACTIVE_SOIL,
            "Chihiros Aquatic Studio",
            "chihiros_aqua_soil_launch"
        ),
        "substrate_chihiros_aquasoil_9l".verified(
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
    )

    private fun substrateProductIds(group: SubstrateEvidenceGroup): List<String> {
        val firstNumber = substrateProductNumber(group.firstProductId)
        val lastNumber = substrateProductNumber(group.lastProductId)
        require(firstNumber <= lastNumber)
        return (firstNumber..lastNumber).map(AquariumSubstrateProductIds::productId)
    }

    private fun substrateProductNumber(productId: String): Int {
        require(productId.startsWith(SUBSTRATE_ID_PREFIX))
        return productId.removePrefix(SUBSTRATE_ID_PREFIX).toInt()
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

    private fun String.unverified(
        categoryKey: String,
        sourceOrganization: String,
        sourceRecordId: String
    ): Pair<String, Record> = this to Record(
        categoryKey = categoryKey,
        metadata = AquariumSubstrateProductMetadata(
            semantic = AquariumSubstrateSemantic.UNKNOWN,
            evidenceStatus = AquariumSubstrateEvidenceStatus.UNVERIFIED_GENERIC,
            sourceOrganization = sourceOrganization,
            sourceRecordId = sourceRecordId,
            reviewedOn = SUBSTRATE_REVIEW_DATE
        )
    )
}
