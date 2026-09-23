package com.aqua.aqualight.application.aquarium

/**
 * Exact product-id based substrate semantics used outside presentation.
 *
 * Records are intentionally keyed by stable material catalog IDs. Display-name, brand, translated
 * text and free-form note parsing are forbidden fallbacks.
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
        val sourceRecordId: String,
        val sourceUrl: String
    )

    private val records: Map<String, Record> = (
        listOf(
            "substrate_chihiros_aquasoil_9l".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.ACTIVE_SOIL,
                "Chihiros Aquatic Studio",
                "chihiros_aqua_soil_launch",
                "https://www.facebook.com/chihirosaquatic/posts/606277074868748/"
            ),
            "substrate_chihiros_aquasoil_3l".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.ACTIVE_SOIL,
                "Chihiros Aquatic Studio",
                "chihiros_aqua_soil_launch",
                "https://www.facebook.com/chihirosaquatic/posts/606277074868748/"
            ),
            "substrate_ada_tourmaline_bc".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.ADDITIVE,
                "Aqua Design Amano",
                "ada_tourmaline_bc",
                "https://www.adana.co.jp/en/contents/products/na_substrate/detail05.html"
            ),
            "substrate_dennerle_deponitmix_4_8kg".verified(
                AquariumMaterialCategoryKeys.SUBSTRATE,
                AquariumSubstrateSemantic.NUTRIENT_BASE,
                "Dennerle",
                "dennerle_deponit_mix_pro",
                "https://dennerle.com/en/products/deponit-mix-pro"
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
         * The existing catalog contract uses INERT for non-active gravel/mineral substrates.
         * Some verified mineral gravels can buffer or raise pH/KH (for example dolomite,
         * carbonate sands, Onyx and some WIO/AMTRA gravels), but they are not ACTIVE_SOIL:
         * they do not provide the soil-style acidifying/softening behavior used by AquaLight's
         * active-soil care rules.
         */
        return (1..181).map { index ->
            val productId = gravelProductId(index)
            val evidence = gravelEvidence(index)
            productId.verified(
                categoryKey = AquariumMaterialCategoryKeys.GRAVEL,
                semantic = AquariumSubstrateSemantic.INERT,
                sourceOrganization = evidence.sourceOrganization,
                sourceRecordId = evidence.sourceRecordId,
                sourceUrl = evidence.sourceUrl,
                reviewedOn = GRAVEL_CATALOG_REVIEW_DATE
            )
        }
    }

    private fun gravelEvidence(index: Int): GravelEvidence {
        val productId = gravelProductId(index)
        return when (index) {
            in 1..2 -> evidence(
                "Aqua Design Amano",
                productId,
                "https://www.adana.co.jp/en/contents/products/na_substrate/detail04.html"
            )

            in 3..5 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/products/natural-gravel-bairaman"
            )

            in 6..11 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/products/natural-gravel-kongo-3-8mm"
            )

            in 12..13 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/products/natural-gravel-mekong"
            )

            in 14..19 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/products/natural-gravel-okavango-4-8mm"
            )

            in 20..21 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/products/natural-gravel-rio-branco"
            )

            in 22..24 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/products/natural-gravel-rio-xingu"
            )

            in 25..30 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/collections/gravel"
            )

            in 31..34 -> evidence(
                "Dennerle",
                productId,
                "https://dennerle.com/en/products/nano-shrimp-gravel"
            )

            in 35..67 -> evidence(
                "WIO",
                productId,
                "https://www.wio.eco/product-lines/gravels"
            )

            in 68..81 -> evidence(
                "JBL",
                productId,
                "https://www.jbl.de/en/areas/section/57/substrate?country=us"
            )

            in 82..101 -> evidence(
                "sera",
                productId,
                "https://www.sera.de/us/freshwater-aquarium/Products/Page-10/"
            )

            in 102..103 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/flourite.php"
            )

            in 104..105 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/flourite-black.php"
            )

            in 106..107 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/flourite-dark.php"
            )

            in 108..109 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/flourite-red.php"
            )

            in 110..111 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/flourite-sand.php"
            )

            in 112..113 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/flourite-black-sand.php"
            )

            in 114..115 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/onyx-sand.php"
            )

            116 -> evidence(
                "Seachem",
                productId,
                "https://www.seachem.com/onyx.php"
            )

            in 117..135 -> evidence(
                "CaribSea",
                productId,
                "https://caribsea.com/freshwater-substrates/"
            )

            in 136..141 -> evidence(
                "Aquael",
                productId,
                "https://www.aquael.com/products/aquaristics/substrates-gravels/kwarcowe-wielobarwne/"
            )

            in 142..143 -> evidence(
                "Aquael",
                productId,
                "https://www.aquael.com/products/aquaristics/substrates-gravels/bazaltowe/"
            )

            in 144..145 -> evidence(
                "Aquael",
                productId,
                "https://www.aquael.com/products/aquaristics/substrates-gravels/dolomitowe/"
            )

            in 146..147 -> evidence(
                "ReeFlowers",
                productId,
                "https://www.reeflowers.com/urunler/duzenleyici-ve-temizleyiciler/gravels/NAS7K05?lang=en"
            )

            in 148..151 -> evidence(
                "ReeFlowers",
                productId,
                "https://www.reeflowers.com/urunler/duzenleyici-ve-temizleyiciler/gravels/IBS25K2?lang=en"
            )

            in 152..155 -> evidence(
                "ReeFlowers",
                productId,
                "https://www.reeflowers.com/urunler/duzenleyici-ve-temizleyiciler/gravels/PWS7K1?lang=tr"
            )

            in 156..158 -> evidence(
                "RFL",
                productId,
                "https://www.rfl.com.tr/marka/reeflowers"
            )

            in 159..161 -> evidence(
                "CrystalPro Aquatics",
                productId,
                "https://www.crystalpro.com.tr/sands/black-sand-eng"
            )

            in 162..164 -> evidence(
                "CrystalPro Aquatics",
                productId,
                "https://www.crystalpro.com.tr/sands/white-sand-eng"
            )

            in 165..166 -> evidence(
                "CrystalPro Aquatics",
                productId,
                "https://www.crystalpro.com.tr/sands/silica-sand-eng"
            )

            in 167..168 -> evidence(
                "CrystalPro Aquatics",
                productId,
                "https://www.crystalpro.com.tr/sands/river-sand-eng"
            )

            in 169..171 -> evidence(
                "AMTRA",
                productId,
                "https://amtra.net/en/prodotti/acquarium/sands-and-decorations/sands-and-gravels/amtra-policromo-medio-3-4mm-2/"
            )

            in 172..175 -> evidence(
                "AMTRA",
                productId,
                "https://amtra.net/en/prodotti/acquarium/sands-and-decorations/sands-and-gravels/ghiaia-noa-4-8mm-2/"
            )

            in 176..181 -> evidence(
                "PRODAC International",
                productId,
                "https://www.prodacinternational.it/en/aquarium-gb/ghiaietti-gb.html"
            )

            else -> error("Unsupported gravel catalog index: $index")
        }
    }

    private fun evidence(
        sourceOrganization: String,
        sourceRecordId: String,
        sourceUrl: String
    ): GravelEvidence = GravelEvidence(
        sourceOrganization = sourceOrganization,
        sourceRecordId = sourceRecordId,
        sourceUrl = sourceUrl
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
        sourceUrl: String,
        reviewedOn: String = SUBSTRATE_REVIEW_DATE
    ): Pair<String, Record> = this to Record(
        categoryKey = categoryKey,
        metadata = AquariumSubstrateProductMetadata(
            semantic = semantic,
            evidenceStatus = AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT,
            sourceOrganization = sourceOrganization,
            sourceRecordId = sourceRecordId,
            sourceUrl = sourceUrl,
            reviewedOn = reviewedOn
        )
    )
}
