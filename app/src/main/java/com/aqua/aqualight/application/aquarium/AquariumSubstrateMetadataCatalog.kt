package com.aqua.aqualight.application.aquarium

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
    val productIds: List<String>,
    val sourceOrganization: String
)

private val gravelEvidenceGroups = listOf(
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_ada_aqua_gravel_s_2_kg",
            "gravel_ada_aqua_gravel_s_8_kg"
        ),
        sourceOrganization = "Aqua Design Amano"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_dennerle_natural_gravel_bairaman_0_1_0_6_mm_500_g",
            "gravel_dennerle_natural_gravel_bairaman_0_1_0_6_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_bairaman_0_1_0_6_mm_5_kg",
            "gravel_dennerle_natural_gravel_kongo_3_8_mm_500_g",
            "gravel_dennerle_natural_gravel_kongo_3_8_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_kongo_3_8_mm_5_kg",
            "gravel_dennerle_natural_gravel_kongo_10_30_mm_500_g",
            "gravel_dennerle_natural_gravel_kongo_10_30_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_kongo_10_30_mm_5_kg",
            "gravel_dennerle_natural_gravel_mekong_0_1_1_4_mm_500_g",
            "gravel_dennerle_natural_gravel_mekong_0_1_1_4_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_okavango_4_8_mm_500_g",
            "gravel_dennerle_natural_gravel_okavango_4_8_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_okavango_4_8_mm_5_kg",
            "gravel_dennerle_natural_gravel_okavango_8_12_mm_500_g",
            "gravel_dennerle_natural_gravel_okavango_8_12_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_okavango_8_12_mm_5_kg",
            "gravel_dennerle_natural_gravel_rio_branco_0_1_2_mm_500_g",
            "gravel_dennerle_natural_gravel_rio_branco_0_1_2_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_rio_xingu_2_22_mm_500_g",
            "gravel_dennerle_natural_gravel_rio_xingu_2_22_mm_2_5_kg",
            "gravel_dennerle_natural_gravel_rio_xingu_2_22_mm_5_kg",
            "gravel_dennerle_aquarium_gravel_diamond_black_1_2_mm_5_kg",
            "gravel_dennerle_aquarium_gravel_diamond_black_1_2_mm_10_kg",
            "gravel_dennerle_aquarium_gravel_dark_brown_1_2_mm_10_kg",
            "gravel_dennerle_aquarium_gravel_natural_white_1_2_mm_10_kg",
            "gravel_dennerle_aquarium_gravel_light_brown_1_2_mm_10_kg",
            "gravel_dennerle_aquarium_gravel_slate_gray_1_2_mm_10_kg",
            "gravel_dennerle_nano_shrimp_gravel_sulawesi_black_0_7_1_2_mm_2_kg",
            "gravel_dennerle_nano_shrimp_gravel_arkansas_grey_0_7_1_2_mm_2_kg",
            "gravel_dennerle_nano_shrimp_gravel_borneo_brown_0_7_1_2_mm_2_kg",
            "gravel_dennerle_nano_shrimp_gravel_sunda_white_0_7_1_2_mm_2_kg"
        ),
        sourceOrganization = "Dennerle"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_wio_adder_gravel_s_5_10_mm_2_kg",
            "gravel_wio_adder_gravel_mix_5_30_mm_2_kg",
            "gravel_wio_adder_gravel_mix_5_30_mm_5_kg",
            "gravel_wio_belladonna_gravel_s_5_15_mm_2_kg",
            "gravel_wio_belladonna_gravel_mix_5_30_mm_2_kg",
            "gravel_wio_belladonna_gravel_mix_5_30_mm_5_kg",
            "gravel_wio_bumblebee_gravel_s_3_10_mm_2_kg",
            "gravel_wio_bumblebee_gravel_mix_3_40_mm_2_kg",
            "gravel_wio_bumblebee_gravel_mix_3_40_mm_5_kg",
            "gravel_wio_elderly_gravel_s_3_10_mm_2_kg",
            "gravel_wio_elderly_gravel_mix_3_40_mm_2_kg",
            "gravel_wio_elderly_gravel_mix_3_40_mm_5_kg",
            "gravel_wio_inferno_gravel_s_15_25_mm_2_kg",
            "gravel_wio_inferno_gravel_mix_15_40_mm_2_kg",
            "gravel_wio_inferno_gravel_mix_15_40_mm_5_kg",
            "gravel_wio_midnight_gravel_s_10_20_mm_2_kg",
            "gravel_wio_midnight_gravel_mix_10_40_mm_2_kg",
            "gravel_wio_midnight_gravel_mix_10_40_mm_5_kg",
            "gravel_wio_mist_gravel_s_10_20_mm_2_kg",
            "gravel_wio_mist_gravel_mix_10_40_mm_2_kg",
            "gravel_wio_mist_gravel_mix_10_40_mm_5_kg",
            "gravel_wio_ryuoh_gravel_s_10_20_mm_2_kg",
            "gravel_wio_ryuoh_gravel_mix_10_40_mm_2_kg",
            "gravel_wio_ryuoh_gravel_mix_10_40_mm_5_kg",
            "gravel_wio_shadow_gravel_s_1_20_mm_2_kg",
            "gravel_wio_shadow_gravel_mix_1_40_mm_2_kg",
            "gravel_wio_shadow_gravel_mix_1_40_mm_5_kg",
            "gravel_wio_stream_gravel_s_8_20_mm_2_kg",
            "gravel_wio_stream_gravel_mix_8_40_mm_2_kg",
            "gravel_wio_stream_gravel_mix_8_40_mm_5_kg",
            "gravel_wio_venom_gravel_s_8_20_mm_2_kg",
            "gravel_wio_venom_gravel_mix_8_40_mm_2_kg",
            "gravel_wio_venom_gravel_mix_8_40_mm_5_kg"
        ),
        sourceOrganization = "WIO"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_jbl_sansibar_snow_0_1_0_6_mm_5_kg",
            "gravel_jbl_sansibar_snow_0_1_0_6_mm_10_kg",
            "gravel_jbl_sansibar_white_0_2_0_6_mm_5_kg",
            "gravel_jbl_sansibar_white_0_2_0_6_mm_10_kg",
            "gravel_jbl_sansibar_red_0_2_0_6_mm_5_kg",
            "gravel_jbl_sansibar_red_0_2_0_6_mm_10_kg",
            "gravel_jbl_sansibar_orange_0_2_0_6_mm_5_kg",
            "gravel_jbl_sansibar_orange_0_2_0_6_mm_10_kg",
            "gravel_jbl_sansibar_dark_0_2_0_6_mm_5_kg",
            "gravel_jbl_sansibar_dark_0_2_0_6_mm_10_kg",
            "gravel_jbl_sansibar_grey_0_2_0_6_mm_5_kg",
            "gravel_jbl_sansibar_grey_0_2_0_6_mm_10_kg",
            "gravel_jbl_sansibar_river_approx_0_8_mm_5_kg",
            "gravel_jbl_sansibar_river_approx_0_8_mm_10_kg"
        ),
        sourceOrganization = "JBL"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_sera_aquarium_gravel_anthracite_fine_1_2_mm_3_l_4_kg",
            "gravel_sera_aquarium_gravel_anthracite_fine_1_2_mm_6_l_8_kg",
            "gravel_sera_aquarium_gravel_brown_fine_0_2_mm_3_l_4_kg",
            "gravel_sera_aquarium_gravel_brown_fine_0_2_mm_6_l_8_kg",
            "gravel_sera_aquarium_gravel_ocher_fine_0_2_mm_3_l_4_kg",
            "gravel_sera_aquarium_gravel_ocher_fine_0_2_mm_6_l_8_kg",
            "gravel_sera_aquarium_gravel_white_fine_0_2_mm_3_l_4_kg",
            "gravel_sera_aquarium_gravel_white_fine_0_2_mm_6_l_8_kg",
            "gravel_sera_aquarium_gravel_anthracite_coarse_1_3_mm_3_l_5_kg",
            "gravel_sera_aquarium_gravel_anthracite_coarse_1_3_mm_6_l_10_kg",
            "gravel_sera_aquarium_gravel_beige_coarse_2_6_mm_3_l_5_kg",
            "gravel_sera_aquarium_gravel_beige_coarse_2_6_mm_6_l_10_kg",
            "gravel_sera_aquarium_gravel_black_coarse_2_3_mm_3_l_5_kg",
            "gravel_sera_aquarium_gravel_black_coarse_2_3_mm_6_l_10_kg",
            "gravel_sera_aquarium_gravel_mix_coarse_2_8_mm_3_l_4_kg",
            "gravel_sera_aquarium_gravel_mix_coarse_2_8_mm_6_l_8_kg",
            "gravel_sera_aquarium_gravel_white_coarse_1_3_mm_3_l_5_kg",
            "gravel_sera_aquarium_gravel_white_coarse_1_3_mm_6_l_10_kg",
            "gravel_sera_aquarium_gravel_lava_substrate_2_8_mm_3_l_4_kg",
            "gravel_sera_aquarium_gravel_lava_substrate_2_8_mm_6_l_8_kg"
        ),
        sourceOrganization = "sera"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_seachem_flourite_3_5_kg",
            "gravel_seachem_flourite_7_kg",
            "gravel_seachem_flourite_black_3_5_kg",
            "gravel_seachem_flourite_black_7_kg",
            "gravel_seachem_flourite_dark_3_5_kg",
            "gravel_seachem_flourite_dark_7_kg",
            "gravel_seachem_flourite_red_3_5_kg",
            "gravel_seachem_flourite_red_7_kg",
            "gravel_seachem_flourite_sand_3_5_kg",
            "gravel_seachem_flourite_sand_7_kg",
            "gravel_seachem_flourite_black_sand_3_5_kg",
            "gravel_seachem_flourite_black_sand_7_kg",
            "gravel_seachem_onyx_sand_3_5_kg",
            "gravel_seachem_onyx_sand_7_kg",
            "gravel_seachem_onyx_7_kg"
        ),
        sourceOrganization = "Seachem"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_caribsea_super_naturals_gemstone_creek_3_5_mm_5_lb",
            "gravel_caribsea_super_naturals_gemstone_creek_3_5_mm_20_lb",
            "gravel_caribsea_super_naturals_gemstone_creek_3_5_mm_40_lb",
            "gravel_caribsea_super_naturals_peace_river_1_2_mm_5_lb",
            "gravel_caribsea_super_naturals_peace_river_1_2_mm_10_lb",
            "gravel_caribsea_super_naturals_peace_river_1_2_mm_20_lb",
            "gravel_caribsea_super_naturals_peace_river_1_2_mm_40_lb",
            "gravel_caribsea_super_naturals_midnight_river_1_4_mm_5_lb",
            "gravel_caribsea_super_naturals_midnight_river_1_4_mm_10_lb",
            "gravel_caribsea_super_naturals_midnight_river_1_4_mm_20_lb",
            "gravel_caribsea_super_naturals_midnight_river_1_4_mm_40_lb",
            "gravel_caribsea_super_naturals_raven_river_pebble_6_14_mm_5_lb",
            "gravel_caribsea_super_naturals_raven_river_pebble_6_14_mm_10_lb",
            "gravel_caribsea_super_naturals_raven_river_pebble_6_14_mm_20_lb",
            "gravel_caribsea_super_naturals_raven_river_pebble_6_14_mm_40_lb",
            "gravel_caribsea_super_naturals_shadow_creek_sand_1_3_mm_5_lb",
            "gravel_caribsea_super_naturals_shadow_creek_sand_1_3_mm_10_lb",
            "gravel_caribsea_super_naturals_shadow_creek_sand_1_3_mm_20_lb",
            "gravel_caribsea_super_naturals_shadow_creek_sand_1_3_mm_40_lb"
        ),
        sourceOrganization = "CaribSea"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_aquael_natural_multi_colour_gravel_1_4_2_mm_2_kg",
            "gravel_aquael_natural_multi_colour_gravel_1_4_2_mm_10_kg",
            "gravel_aquael_natural_multi_colour_gravel_3_5_mm_2_kg",
            "gravel_aquael_natural_multi_colour_gravel_3_5_mm_10_kg",
            "gravel_aquael_natural_multi_colour_gravel_5_10_mm_2_kg",
            "gravel_aquael_natural_multi_colour_gravel_5_10_mm_10_kg",
            "gravel_aquael_basalt_gravel_2_4_mm_2_kg",
            "gravel_aquael_basalt_gravel_2_4_mm_10_kg",
            "gravel_aquael_dolomite_gravel_2_4_mm_2_kg",
            "gravel_aquael_dolomite_gravel_2_4_mm_10_kg"
        ),
        sourceOrganization = "Aquael"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_reeflowers_natural_aquasand_0_5_1_mm_7_kg",
            "gravel_reeflowers_natural_aquasand_0_5_1_mm_25_kg",
            "gravel_reeflowers_iceland_black_sand_1_2_mm_7_kg",
            "gravel_reeflowers_iceland_black_sand_1_2_mm_25_kg",
            "gravel_reeflowers_iceland_black_sand_3_5_mm_7_kg",
            "gravel_reeflowers_iceland_black_sand_3_5_mm_25_kg",
            "gravel_reeflowers_pearl_white_sand_0_5_1_mm_7_kg",
            "gravel_reeflowers_pearl_white_sand_0_5_1_mm_25_kg",
            "gravel_reeflowers_pearl_white_sand_1_1_5_mm_7_kg",
            "gravel_reeflowers_pearl_white_sand_1_1_5_mm_25_kg",
            "gravel_reeflowers_natural_tara_gravel_1_2_mm_25_kg",
            "gravel_reeflowers_natural_tara_gravel_2_4_mm_25_kg",
            "gravel_reeflowers_natural_sahara_sand_0_5_1_mm_25_kg"
        ),
        sourceOrganization = "ReeFlowers"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_crystalpro_black_sand_1_3_mm_6_kg",
            "gravel_crystalpro_black_sand_1_3_mm_12_kg",
            "gravel_crystalpro_black_sand_1_3_mm_25_kg",
            "gravel_crystalpro_white_sand_1_1_5_mm_6_kg",
            "gravel_crystalpro_white_sand_1_1_5_mm_12_kg",
            "gravel_crystalpro_white_sand_1_1_5_mm_25_kg",
            "gravel_crystalpro_silica_sand_0_5_2_mm_6_kg",
            "gravel_crystalpro_silica_sand_0_5_2_mm_12_kg",
            "gravel_crystalpro_river_sand_3_5_mm_6_kg",
            "gravel_crystalpro_river_sand_3_5_mm_12_kg"
        ),
        sourceOrganization = "CrystalPro Aquatics"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_amtra_polychrome_gravel_3_4_mm_2_kg",
            "gravel_amtra_polychrome_gravel_3_4_mm_5_kg",
            "gravel_amtra_polychrome_gravel_3_4_mm_10_kg",
            "gravel_amtra_gravel_noa_4_8_mm_1_kg",
            "gravel_amtra_gravel_noa_4_8_mm_2_kg",
            "gravel_amtra_gravel_noa_4_8_mm_5_kg",
            "gravel_amtra_gravel_noa_4_8_mm_10_kg"
        ),
        sourceOrganization = "AMTRA"
    ),
    GravelEvidenceGroup(
        productIds = listOf(
            "gravel_prodac_mixed_color_quartz_2_3_mm_1_kg",
            "gravel_prodac_mixed_color_quartz_2_3_mm_2_5_kg",
            "gravel_prodac_quartz_black_2_3_mm_1_kg",
            "gravel_prodac_quartz_black_2_3_mm_2_5_kg",
            "gravel_prodac_polycrome_2_3_mm_2_5_kg",
            "gravel_prodac_polycrome_2_3_mm_25_kg"
        ),
        sourceOrganization = "PRODAC International"
    )
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
        AquariumGravelProductIds.EXPECTED_CATALOG_PRODUCT_COUNT +
            AquariumSubstrateProductIds.EXPECTED_CATALOG_PRODUCT_COUNT

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
         * INERT is AquaLight's existing Gravel semantic for "non-active substrate". It must not
         * be read as "chemically neutral": some mineral products can buffer or raise pH/KH/GH,
         * but they do not provide the acidifying/softening active-soil behavior consumed by
         * AquaLight's active-soil care rules.
         */
        val records = gravelEvidenceGroups.flatMap { group ->
            group.productIds.map { productId ->
                productId.verified(
                    categoryKey = AquariumMaterialCategoryKeys.GRAVEL,
                    semantic = AquariumSubstrateSemantic.INERT,
                    sourceOrganization = group.sourceOrganization,
                    sourceRecordId = productId,
                    reviewedOn = GRAVEL_CATALOG_REVIEW_DATE
                )
            }
        }
        check(records.size == AquariumGravelProductIds.EXPECTED_CATALOG_PRODUCT_COUNT)
        check(records.map { (productId, _) -> productId } == AquariumGravelProductIds.ALL)
        return records
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
