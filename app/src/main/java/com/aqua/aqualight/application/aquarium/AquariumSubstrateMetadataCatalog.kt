package com.aqua.aqualight.application.aquarium

private const val GRAVEL_ID_PREFIX = "gravel_"
private const val GRAVEL_ID_PADDING = 4
private const val EXPECTED_GRAVEL_RECORD_COUNT = 181
private const val SUBSTRATE_REVIEW_DATE = "2026-09-23"
private const val GRAVEL_CATALOG_REVIEW_DATE = "2026-09-23"

private data class SubstrateEvidenceGroup(
    val productIds: List<String>,
    val semantic: AquariumSubstrateSemantic,
    val sourceOrganization: String
)

private val substrateEvidenceGroups = listOf(
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_chihiros_aquasoil_3l",
            "substrate_chihiros_aquasoil_9l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Chihiros Aquatic Studio"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_ada_tourmaline_bc"
        ),
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "Aqua Design Amano"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_dennerle_deponit_mix_pro_10in1_4_8_kg"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Dennerle"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_ada_aqua_soil_amazonia_ver_2_3_l",
            "substrate_ada_aqua_soil_amazonia_ver_2_9_l",
            "substrate_ada_aqua_soil_amazonia_ver_2_powder_3_l",
            "substrate_ada_aqua_soil_amazonia_ver_2_powder_9_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Aqua Design Amano"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_ada_power_sand_advance_s_2_l",
            "substrate_ada_power_sand_advance_m_6_l",
            "substrate_ada_power_sand_advance_l_6_l"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Aqua Design Amano"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_ada_bacter_100_100_g",
            "substrate_ada_bacter_ball",
            "substrate_ada_clear_super_50_g"
        ),
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "Aqua Design Amano"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_tropica_aquarium_soil_3_l",
            "substrate_tropica_aquarium_soil_9_l",
            "substrate_tropica_aquarium_soil_powder_3_l",
            "substrate_tropica_aquarium_soil_powder_9_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Tropica Aquarium Plants"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_tropica_plant_growth_substrate_1_l",
            "substrate_tropica_plant_growth_substrate_2_5_l",
            "substrate_tropica_plant_growth_substrate_5_l"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Tropica Aquarium Plants"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_dennerle_scapers_soil_4_l",
            "substrate_dennerle_scapers_soil_8_l",
            "substrate_dennerle_shrimp_king_active_soil_4_l",
            "substrate_dennerle_shrimp_king_active_soil_8_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Dennerle"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_dennerle_deponit_mix_pro_10in1_9_6_kg",
            "substrate_dennerle_nutribasis_6in1"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Dennerle"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_jbl_proscape_plantsoil_brown_3_l",
            "substrate_jbl_proscape_plantsoil_brown_9_l",
            "substrate_jbl_proscape_plantsoil_beige_3_l",
            "substrate_jbl_proscape_plantsoil_beige_9_l",
            "substrate_jbl_proscape_shrimpsoil_brown_3_l",
            "substrate_jbl_proscape_shrimpsoil_brown_9_l",
            "substrate_jbl_proscape_shrimpsoil_beige_3_l",
            "substrate_jbl_proscape_shrimpsoil_beige_9_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "JBL"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_jbl_proflora_aquabasis_plus_2_5_l",
            "substrate_jbl_proflora_aquabasis_plus_5_l",
            "substrate_jbl_proscape_volcano_mineral_3_l",
            "substrate_jbl_proscape_volcano_mineral_9_l"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "JBL"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_jbl_proscape_volcano_powder_250_g",
            "substrate_jbl_proscape_plantstart_2_x_8_g"
        ),
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "JBL"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_fluval_stratum_2_kg",
            "substrate_fluval_stratum_4_kg",
            "substrate_fluval_stratum_8_kg",
            "substrate_fluval_bio_stratum",
            "substrate_fluval_betta_stratum_0_8_kg"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Fluval"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_oase_scaperline_soil_black_3_l",
            "substrate_oase_scaperline_soil_black_9_l",
            "substrate_oase_scaperline_soil_brown_3_l",
            "substrate_oase_scaperline_soil_brown_9_l",
            "substrate_oase_scaperline_soil_small_black_3_l",
            "substrate_oase_scaperline_soil_small_black_9_l",
            "substrate_oase_scaperline_soil_small_brown_3_l",
            "substrate_oase_scaperline_soil_small_brown_9_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "OASE"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_oase_basesoil"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "OASE"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_aquario_neo_soil_compact_plant_3_l",
            "substrate_aquario_neo_soil_compact_plant_8_l",
            "substrate_aquario_neo_soil_compact_plant_powder_3_l",
            "substrate_aquario_neo_soil_compact_plant_powder_8_l",
            "substrate_aquario_neo_soil_compact_shrimp_3_l",
            "substrate_aquario_neo_soil_compact_shrimp_8_l",
            "substrate_aquario_neo_soil_compact_shrimp_powder_3_l",
            "substrate_aquario_neo_soil_compact_shrimp_powder_8_l",
            "substrate_aquario_neo_soil_no_co2_3_l_8_l",
            "substrate_aquario_neo_soil_brown_plant"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Aquario"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_uns_controsoil_black_normal_1_3_10_l",
            "substrate_uns_controsoil_black_fine_1_3_10_l",
            "substrate_uns_controsoil_black_extra_fine_1_3_10_l",
            "substrate_uns_controsoil_brown_normal_1_3_10_l",
            "substrate_uns_controsoil_brown_fine_1_3_10_l",
            "substrate_uns_controsoil_brown_extra_fine_1_3_10_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Ultum Nature Systems"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_uns_controbase"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Ultum Nature Systems"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_2hr_aquarist_apt_feast_aquarium_soil_2_l",
            "substrate_2hr_aquarist_apt_feast_aquarium_soil_5_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "2HR Aquarist"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_glasgarten_environment_aquarium_soil_4_l",
            "substrate_glasgarten_environment_aquarium_soil_9_l",
            "substrate_glasgarten_environment_aquarium_soil_powder_4_l",
            "substrate_glasgarten_environment_aquarium_soil_powder_9_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "GlasGarten"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_sl_aqua_more_nature_soil_black_s_3_8_l",
            "substrate_sl_aqua_more_nature_soil_black_m_3_8_l",
            "substrate_sl_aqua_more_nature_soil_black_l_3_8_l",
            "substrate_sl_aqua_more_nature_brown_soil_s_8_l",
            "substrate_sl_aqua_more_nature_brown_soil_m_8_l",
            "substrate_sl_aqua_more_nature_brown_soil_l_8_l",
            "substrate_sl_aqua_gokujou_black_soil"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "SL-Aqua"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_sl_aqua_sulawesi_volcanic_rock_soil"
        ),
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "SL-Aqua"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_sl_aqua_mironekuton",
            "substrate_sl_aqua_montmorillonite"
        ),
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "SL-Aqua"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_jun_platinum_soil_black_normal_1_3_8_l",
            "substrate_jun_platinum_soil_black_powder_1_3_8_l",
            "substrate_jun_platinum_soil_black_super_powder_1_3_8_l",
            "substrate_jun_platinum_soil_brown_normal_1_3_8_l",
            "substrate_jun_platinum_soil_brown_powder_1_3_8_l",
            "substrate_jun_platinum_soil_brown_super_powder_1_3_8_l",
            "substrate_jun_master_soil_next_black_normal_3_8_l",
            "substrate_jun_master_soil_next_black_powder_3_8_l",
            "substrate_jun_master_soil_next_black_super_powder_3_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "JUN"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_ista_water_plant_soil_ph_6_5_medium_2_9_l",
            "substrate_ista_water_plant_soil_ph_6_5_small_2_9_l",
            "substrate_ista_substrate_premium_soil_s_2_8_l",
            "substrate_ista_substrate_premium_soil_l_2_8_l",
            "substrate_ista_plant_shrimp_soil_ph_5_5_2_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "ISTA"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_brightwell_aquatics_florinvolcanit_rio_escuro_fine_5_15_24_lb",
            "substrate_brightwell_aquatics_florinvolcanit_rio_escuro_medium",
            "substrate_brightwell_aquatics_florinvolcanit_rio_escuro_extra_fine",
            "substrate_brightwell_aquatics_rio_cafe_fine",
            "substrate_brightwell_aquatics_rio_cafe_medium",
            "substrate_brightwell_aquatics_rio_cafe_extra_fine"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Brightwell Aquatics"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_brightwell_aquatics_florinbase_laterin_substrat_vf"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Brightwell Aquatics"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_brightwell_aquatics_florinbase_laterite_powder"
        ),
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        sourceOrganization = "Brightwell Aquatics"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_ebi_gold_shrimp_soil_5_l",
            "substrate_ebi_gold_waterplant_soil_5_l",
            "substrate_ebi_gold_waterplant_soil_powder_5_l",
            "substrate_ebi_gold_waterplant_soil_natural_brown_5_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Ebi Gold"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_tetra_activesubstrate_3_l",
            "substrate_tetra_activesubstrate_6_l"
        ),
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "Tetra"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_tetra_completesubstrate_2_5_kg"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Tetra"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_sera_aquarium_gravel_floredepot_substrate_2_4_kg",
            "substrate_sera_aquarium_gravel_floredepot_substrate_4_7_kg"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "sera"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_aquaforest_af_natural_substrate_7_5_l"
        ),
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        sourceOrganization = "Aquaforest"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_aquaforest_af_lava_soil_5_l",
            "substrate_aquaforest_af_lava_soil_black_5_l"
        ),
        semantic = AquariumSubstrateSemantic.UNKNOWN,
        sourceOrganization = "Aquaforest"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_yokuchi_jiban_soil_10_l",
            "substrate_yokuchi_jiban_soil_powder_10_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Yokuchi"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_benibachi_black_soil_5_kg"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Benibachi"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_shrimps_forever_shrimps_soil_9_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Shrimps Forever"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_prize_aquasoil_3_l",
            "substrate_prize_aquasoil_9_l",
            "substrate_prize_aquasoil_powder_3_l",
            "substrate_prize_aquasoil_powder_9_l"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Prize"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_seachem_aquavitro_aquasolum_black_humate_4_kg"
        ),
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        sourceOrganization = "Seachem Laboratories"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_seachem_flourite_7_kg",
            "substrate_seachem_flourite_black_7_kg",
            "substrate_seachem_flourite_dark_7_kg",
            "substrate_seachem_flourite_red_7_kg"
        ),
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "Seachem Laboratories"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_caribsea_eco_complete_planted_aquarium_substrate_black",
            "substrate_caribsea_eco_complete_planted_aquarium_substrate_red"
        ),
        semantic = AquariumSubstrateSemantic.INERT,
        sourceOrganization = "CaribSea"
    ),
    SubstrateEvidenceGroup(
        productIds = listOf(
            "substrate_eurostar_aquaclay_5_10_l"
        ),
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
        val records = substrateEvidenceGroups.flatMap { group ->
            group.productIds.map { productId ->
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
        check(records.map { (productId, _) -> productId }.distinct().size == records.size)
        return records
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
