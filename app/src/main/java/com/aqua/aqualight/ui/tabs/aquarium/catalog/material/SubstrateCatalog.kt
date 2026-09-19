package com.aqua.aqualight.ui.tabs.aquarium.catalog.material

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumSubstrateEvidenceStatus
import com.aqua.aqualight.application.aquarium.AquariumSubstrateProductMetadata
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic

object SubstrateCatalog {

    private const val REVIEW_DATE = "2026-09-16"

    private val chihirosAquaSoilMetadata = AquariumSubstrateProductMetadata(
        semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
        evidenceStatus = AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT,
        sourceOrganization = "Chihiros Aquatic Studio",
        sourceRecordId = "chihiros_aqua_soil_launch",
        sourceUrl = "https://www.facebook.com/chihirosaquatic/posts/606277074868748/",
        reviewedOn = REVIEW_DATE
    )

    private val adaTourmalineMetadata = AquariumSubstrateProductMetadata(
        semantic = AquariumSubstrateSemantic.ADDITIVE,
        evidenceStatus = AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT,
        sourceOrganization = "Aqua Design Amano",
        sourceRecordId = "ada_tourmaline_bc",
        sourceUrl = "https://www.adana.co.jp/en/contents/products/na_substrate/detail05.html",
        reviewedOn = REVIEW_DATE
    )

    private val dennerleDeponitMixMetadata = AquariumSubstrateProductMetadata(
        semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
        evidenceStatus = AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT,
        sourceOrganization = "Dennerle",
        sourceRecordId = "dennerle_deponit_mix_pro",
        sourceUrl = "https://dennerle.com/en/products/deponit-mix-pro",
        reviewedOn = REVIEW_DATE
    )

    val definitions: List<AquariumMaterialDefinition> = listOf(
        AquariumMaterialDefinition(
            id = "substrate_chihiros_aquasoil_9l",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_substrate_chihiros_aquasoil_9l_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_aquasoil,
                R.string.catalog_keyword_chihiros
            ),
            substrateMetadata = chihirosAquaSoilMetadata
        ),
        AquariumMaterialDefinition(
            id = "substrate_chihiros_aquasoil_3l",
            brandRes = R.string.catalog_brand_chihiros,
            nameRes = R.string.catalog_material_substrate_chihiros_aquasoil_3l_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_aquasoil,
                R.string.catalog_keyword_chihiros
            ),
            substrateMetadata = chihirosAquaSoilMetadata
        ),
        AquariumMaterialDefinition(
            id = "substrate_ada_tourmaline_bc",
            brandRes = R.string.catalog_brand_ada,
            nameRes = R.string.catalog_material_substrate_ada_tourmaline_bc_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_ada,
                R.string.catalog_keyword_additive
            ),
            substrateMetadata = adaTourmalineMetadata
        ),
        AquariumMaterialDefinition(
            id = "substrate_dennerle_deponitmix_4_8kg",
            brandRes = R.string.catalog_brand_dennerle,
            nameRes = R.string.catalog_material_substrate_dennerle_deponitmix_4_8kg_name,
            categoryKey = MaterialCategoryKey.SUBSTRATE,
            categoryTitleRes = R.string.catalog_material_category_substrate_title,
            keywordRes = listOf(
                R.string.catalog_keyword_substrate,
                R.string.catalog_keyword_soil,
                R.string.catalog_keyword_base_layer,
                R.string.catalog_keyword_dennerle
            ),
            substrateMetadata = dennerleDeponitMixMetadata
        )
    )
}
