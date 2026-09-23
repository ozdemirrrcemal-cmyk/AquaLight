package com.aqua.aqualight.application.aquarium

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

    private data class GravelEvidence(
        val sourceOrganization: String,
        val sourceRecordId: String
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
         * None of the 181 reviewed Gravel products is an active soil.
         *
         * INERT is the existing AquaLight gravel semantic for "non-active substrate". It must not
         * be read as "chemically neutral": some mineral products in this catalog can buffer or
         * raise pH/KH/GH, but they do not provide the acidifying/softening active-soil behavior
         * consumed by AquaLight's active-soil care rules.
         */
        return (1..181).map { index ->
            val productId = gravelProductId(index)
            val evidence = gravelEvidence(index)
            productId.verified(
                categoryKey = AquariumMaterialCategoryKeys.GRAVEL,
                semantic = AquariumSubstrateSemantic.INERT,
                sourceOrganization = evidence.sourceOrganization,
                sourceRecordId = evidence.sourceRecordId,
                reviewedOn = GRAVEL_CATALOG_REVIEW_DATE
            )
        }
    }

    private fun gravelEvidence(index: Int): GravelEvidence {
        return when (index) {
            in 1..2 -> evidence("Aqua Design Amano", "ada_aqua_gravel")
            in 3..5 -> evidence("Dennerle", "dennerle_natural_gravel_bairaman")
            in 6..11 -> evidence("Dennerle", "dennerle_natural_gravel_kongo")
            in 12..13 -> evidence("Dennerle", "dennerle_natural_gravel_mekong")
            in 14..19 -> evidence("Dennerle", "dennerle_natural_gravel_okavango")
            in 20..21 -> evidence("Dennerle", "dennerle_natural_gravel_rio_branco")
            in 22..24 -> evidence("Dennerle", "dennerle_natural_gravel_rio_xingu")
            in 25..30 -> evidence("Dennerle", "dennerle_aquarium_gravel")
            in 31..34 -> evidence("Dennerle", "dennerle_nano_shrimp_gravel")
            in 35..37 -> evidence("WIO", "wio_adder_gravel")
            in 38..40 -> evidence("WIO", "wio_belladonna_gravel")
            in 41..43 -> evidence("WIO", "wio_bumblebee_gravel")
            in 44..46 -> evidence("WIO", "wio_elderly_gravel")
            in 47..49 -> evidence("WIO", "wio_inferno_gravel")
            in 50..52 -> evidence("WIO", "wio_midnight_gravel")
            in 53..55 -> evidence("WIO", "wio_mist_gravel")
            in 56..58 -> evidence("WIO", "wio_ryuoh_gravel")
            in 59..61 -> evidence("WIO", "wio_shadow_gravel")
            in 62..64 -> evidence("WIO", "wio_stream_gravel")
            in 65..67 -> evidence("WIO", "wio_venom_gravel")
            in 68..69 -> evidence("JBL", "jbl_sansibar_snow")
            in 70..71 -> evidence("JBL", "jbl_sansibar_white")
            in 72..73 -> evidence("JBL", "jbl_sansibar_red")
            in 74..75 -> evidence("JBL", "jbl_sansibar_orange")
            in 76..77 -> evidence("JBL", "jbl_sansibar_dark")
            in 78..79 -> evidence("JBL", "jbl_sansibar_grey")
            in 80..81 -> evidence("JBL", "jbl_sansibar_river")
            in 82..83 -> evidence("sera", "sera_gravel_anthracite_fine")
            in 84..85 -> evidence("sera", "sera_gravel_brown_fine")
            in 86..87 -> evidence("sera", "sera_gravel_ocher_fine")
            in 88..89 -> evidence("sera", "sera_gravel_white_fine")
            in 90..91 -> evidence("sera", "sera_gravel_anthracite_coarse")
            in 92..93 -> evidence("sera", "sera_gravel_beige_coarse")
            in 94..95 -> evidence("sera", "sera_gravel_black_coarse")
            in 96..97 -> evidence("sera", "sera_gravel_mix_coarse")
            in 98..99 -> evidence("sera", "sera_gravel_white_coarse")
            in 100..101 -> evidence("sera", "sera_gravel_lava_substrate")
            in 102..103 -> evidence("Seachem", "seachem_flourite")
            in 104..105 -> evidence("Seachem", "seachem_flourite_black")
            in 106..107 -> evidence("Seachem", "seachem_flourite_dark")
            in 108..109 -> evidence("Seachem", "seachem_flourite_red")
            in 110..111 -> evidence("Seachem", "seachem_flourite_sand")
            in 112..113 -> evidence("Seachem", "seachem_flourite_black_sand")
            in 114..115 -> evidence("Seachem", "seachem_onyx_sand")
            116 -> evidence("Seachem", "seachem_onyx")
            in 117..119 -> evidence("CaribSea", "caribsea_gemstone_creek")
            in 120..123 -> evidence("CaribSea", "caribsea_peace_river")
            in 124..127 -> evidence("CaribSea", "caribsea_midnight_river")
            in 128..131 -> evidence("CaribSea", "caribsea_raven_river_pebble")
            in 132..135 -> evidence("CaribSea", "caribsea_shadow_creek_sand")
            in 136..141 -> evidence("Aquael", "aquael_natural_multicolour_gravel")
            in 142..143 -> evidence("Aquael", "aquael_basalt_gravel")
            in 144..145 -> evidence("Aquael", "aquael_dolomite_gravel")
            in 146..147 -> evidence("ReeFlowers", "reeflowers_natural_aquasand")
            in 148..151 -> evidence("ReeFlowers", "reeflowers_iceland_black_sand")
            in 152..155 -> evidence("ReeFlowers", "reeflowers_pearl_white_sand")
            in 156..157 -> evidence("ReeFlowers", "reeflowers_natural_tara_gravel")
            158 -> evidence("ReeFlowers", "reeflowers_natural_sahara_sand")
            in 159..161 -> evidence("CrystalPro Aquatics", "crystalpro_black_sand")
            in 162..164 -> evidence("CrystalPro Aquatics", "crystalpro_white_sand")
            in 165..166 -> evidence("CrystalPro Aquatics", "crystalpro_silica_sand")
            in 167..168 -> evidence("CrystalPro Aquatics", "crystalpro_river_sand")
            in 169..171 -> evidence("AMTRA", "amtra_polychrome_gravel")
            in 172..175 -> evidence("AMTRA", "amtra_gravel_noa")
            in 176..177 -> evidence("PRODAC International", "prodac_mixed_color_quartz")
            in 178..179 -> evidence("PRODAC International", "prodac_quartz_black")
            in 180..181 -> evidence("PRODAC International", "prodac_polycrome")
            else -> error("Unsupported gravel catalog index: $index")
        }
    }

    private fun evidence(
        sourceOrganization: String,
        sourceRecordId: String
    ): GravelEvidence = GravelEvidence(
        sourceOrganization = sourceOrganization,
        sourceRecordId = sourceRecordId
    )

    private fun gravelProductId(index: Int): String {
        require(index in 1..181)
        return "gravel_${index.toString().padStart(4, '0')}"
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
