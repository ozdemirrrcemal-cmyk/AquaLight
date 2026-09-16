package com.aqua.aqualight.data.care.smartcare

enum class SmartCareEvidenceTopic {
    STARTUP,
    LIGHTING,
    WATER_CHANGE,
    FERTILIZER,
    SUBSTRATE,
    CO2,
    ALGAE,
    PLANT_CARE,
    WATER_QUALITY,
    LIVESTOCK,
    FEEDING,
    FISH_HEALTH,
    BIOSECURITY
}

enum class SmartCareEvidenceKind {
    OFFICIAL_METHOD_GUIDE,
    OFFICIAL_PRODUCT_GUIDE,
    OFFICIAL_EQUIPMENT_MANUAL,
    PEER_REVIEWED_RESEARCH,
    VETERINARY_REFERENCE
}

enum class SmartCareEvidenceId(val stableId: String) {
    TROPICA_GROWING_IN("tropica_growing_in"),
    TROPICA_QUICK_GUIDE("tropica_quick_guide"),
    TROPICA_ALGAE_CONTROL("tropica_algae_control"),
    TROPICA_PLANT_DATABASE("tropica_plant_database"),
    TROPICA_SPECIALISED_NUTRITION("tropica_specialised_nutrition"),
    TROPICA_PREMIUM_NUTRITION("tropica_premium_nutrition"),
    ADA_STARTING_FROM_ZERO("ada_starting_from_zero"),
    ADA_LIQUID_FERTILIZERS("ada_liquid_fertilizers"),
    UF_IFAS_FISH_HEALTH("uf_ifas_fish_health"),
    COLOMBO_CO2_PROFI_MANUAL("colombo_co2_profi_manual"),
    BIOSCAPE_CO2_GENERATOR_MANUAL("bioscape_co2_generator_manual"),
    KITAYA_2003_CO2_LIGHT_PHOTOSYNTHESIS("kitaya_2003_co2_light_photosynthesis"),
    CHIHIROS_LIGHT_INTENSITY_GUIDANCE("chihiros_light_intensity_guidance"),
    CHIHIROS_AQUA_SOIL_LAUNCH("chihiros_aqua_soil_launch"),
    ADA_TOURMALINE_BC("ada_tourmaline_bc"),
    DENNERLE_DEPONIT_MIX_PRO("dennerle_deponit_mix_pro"),
    ADA_AQUA_GRAVEL("ada_aqua_gravel"),
    DENNERLE_NANO_SHRIMP_GRAVEL("dennerle_nano_shrimp_gravel"),
    JBL_SANSIBAR_DARK("jbl_sansibar_dark"),
    JBL_SANSIBAR_WHITE("jbl_sansibar_white"),
    AQUAEL_BASALT_GRAVEL("aquael_basalt_gravel")
}

data class SmartCareEvidenceSource(
    val id: SmartCareEvidenceId,
    val organization: String,
    val title: String,
    val url: String,
    val kind: SmartCareEvidenceKind,
    val topics: Set<SmartCareEvidenceTopic>,
    val reviewedOn: String,
    val requiresProfessionalDiagnosis: Boolean = false
)

/** Versioned, auditable evidence behind Smart Care decisions. */
object SmartCareEvidenceCatalog {

    private const val REVIEW_DATE = "2026-09-16"

    val sources: List<SmartCareEvidenceSource> = listOf(
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.TROPICA_GROWING_IN,
            organization = "Tropica Aquarium Plants",
            title = "Starting a new aquarium: Growing-in",
            url = "https://tropica.com/en/guide/get-the-right-start/growing-in/",
            kind = SmartCareEvidenceKind.OFFICIAL_METHOD_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.STARTUP,
                SmartCareEvidenceTopic.LIGHTING,
                SmartCareEvidenceTopic.WATER_CHANGE,
                SmartCareEvidenceTopic.FERTILIZER,
                SmartCareEvidenceTopic.CO2,
                SmartCareEvidenceTopic.ALGAE,
                SmartCareEvidenceTopic.LIVESTOCK
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.TROPICA_QUICK_GUIDE,
            organization = "Tropica Aquarium Plants",
            title = "Make your aquarium a success",
            url = "https://tropica.com/media/870849/REDUCEDP14-11434-Quickguide_ny-UK.pdf",
            kind = SmartCareEvidenceKind.OFFICIAL_METHOD_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.STARTUP,
                SmartCareEvidenceTopic.LIGHTING
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.TROPICA_ALGAE_CONTROL,
            organization = "Tropica Aquarium Plants",
            title = "Algae control",
            url = "https://tropica.com/en/guide/algae-control/",
            kind = SmartCareEvidenceKind.OFFICIAL_METHOD_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.ALGAE,
                SmartCareEvidenceTopic.LIGHTING,
                SmartCareEvidenceTopic.WATER_CHANGE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.TROPICA_PLANT_DATABASE,
            organization = "Tropica Aquarium Plants",
            title = "Plant details database",
            url = "https://tropica.com/en/plants/search",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.LIGHTING,
                SmartCareEvidenceTopic.PLANT_CARE,
                SmartCareEvidenceTopic.CO2
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.TROPICA_SPECIALISED_NUTRITION,
            organization = "Tropica Aquarium Plants",
            title = "Specialised Nutrition",
            url = "https://tropica.com/en/plant-care/liquid-fertilisers/specialised-nutrition/",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.FERTILIZER,
                SmartCareEvidenceTopic.ALGAE,
                SmartCareEvidenceTopic.WATER_CHANGE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.TROPICA_PREMIUM_NUTRITION,
            organization = "Tropica Aquarium Plants",
            title = "Premium Nutrition",
            url = "https://tropica.com/en/plant-care/liquid-fertilisers/premium-nutrition/",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.FERTILIZER,
                SmartCareEvidenceTopic.PLANT_CARE,
                SmartCareEvidenceTopic.WATER_CHANGE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.ADA_STARTING_FROM_ZERO,
            organization = "Aqua Design Amano",
            title = "Nature Aquarium Starting from Zero",
            url = "https://www.adana.co.jp/en/contents/process/index.html",
            kind = SmartCareEvidenceKind.OFFICIAL_METHOD_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.STARTUP,
                SmartCareEvidenceTopic.WATER_CHANGE,
                SmartCareEvidenceTopic.ALGAE,
                SmartCareEvidenceTopic.PLANT_CARE,
                SmartCareEvidenceTopic.WATER_QUALITY,
                SmartCareEvidenceTopic.LIVESTOCK,
                SmartCareEvidenceTopic.FEEDING
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.ADA_LIQUID_FERTILIZERS,
            organization = "Aqua Design Amano",
            title = "The New Green Brighty",
            url = "https://www.adana.co.jp/en/contents/products/na_liquid/new_liquid/index.html",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.FERTILIZER,
                SmartCareEvidenceTopic.PLANT_CARE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.UF_IFAS_FISH_HEALTH,
            organization = "University of Florida IFAS Extension",
            title = "Fish Health Management in Recirculating Aquaculture Systems, Part 3",
            url = "https://ask.ifas.ufl.edu/publication/FA101",
            kind = SmartCareEvidenceKind.VETERINARY_REFERENCE,
            topics = setOf(
                SmartCareEvidenceTopic.WATER_QUALITY,
                SmartCareEvidenceTopic.LIVESTOCK,
                SmartCareEvidenceTopic.FISH_HEALTH,
                SmartCareEvidenceTopic.BIOSECURITY
            ),
            reviewedOn = REVIEW_DATE,
            requiresProfessionalDiagnosis = true
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.COLOMBO_CO2_PROFI_MANUAL,
            organization = "Colombo",
            title = "CO2 Profi 1200 manual",
            url = "https://aquadistri.com/wp-content/uploads/2024/08/" +
                "Manual-Colombo-CO2-Profi-Set-1200.pdf",
            kind = SmartCareEvidenceKind.OFFICIAL_EQUIPMENT_MANUAL,
            topics = setOf(
                SmartCareEvidenceTopic.CO2,
                SmartCareEvidenceTopic.LIGHTING
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.BIOSCAPE_CO2_GENERATOR_MANUAL,
            organization = "Bioscape",
            title = "CO2 Generator manual",
            url = "https://bioscape.com.au/wp-content/uploads/2025/08/" +
                "ARI10-11-Bioscape-CO2-Generator-Manual.pdf",
            kind = SmartCareEvidenceKind.OFFICIAL_EQUIPMENT_MANUAL,
            topics = setOf(
                SmartCareEvidenceTopic.CO2,
                SmartCareEvidenceTopic.LIGHTING
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.KITAYA_2003_CO2_LIGHT_PHOTOSYNTHESIS,
            organization = "Osaka Prefecture University",
            title = "Effects of CO2 concentration and light intensity on photosynthesis",
            url = "https://pubmed.ncbi.nlm.nih.gov/14503512/",
            kind = SmartCareEvidenceKind.PEER_REVIEWED_RESEARCH,
            topics = setOf(
                SmartCareEvidenceTopic.CO2,
                SmartCareEvidenceTopic.LIGHTING,
                SmartCareEvidenceTopic.PLANT_CARE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.CHIHIROS_LIGHT_INTENSITY_GUIDANCE,
            organization = "Chihiros Aquatic Studio",
            title = "How to Set Light Intensity?",
            url = "https://bbs.chihirosaquaticstudio.com/threads/" +
                "how-to-set-light-intensity.4/",
            kind = SmartCareEvidenceKind.OFFICIAL_METHOD_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.STARTUP,
                SmartCareEvidenceTopic.LIGHTING,
                SmartCareEvidenceTopic.ALGAE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.CHIHIROS_AQUA_SOIL_LAUNCH,
            organization = "Chihiros Aquatic Studio",
            title = "Chihiros Aqua Soil product launch",
            url = "https://www.facebook.com/chihirosaquatic/posts/606277074868748/",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.ADA_TOURMALINE_BC,
            organization = "Aqua Design Amano",
            title = "Substrate Additives: Tourmaline BC",
            url = "https://www.adana.co.jp/en/contents/products/na_substrate/detail05.html",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.DENNERLE_DEPONIT_MIX_PRO,
            organization = "Dennerle",
            title = "Deponit Mix Pro",
            url = "https://dennerle.com/en/products/deponit-mix-pro",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.ADA_AQUA_GRAVEL,
            organization = "Aqua Design Amano",
            title = "Aqua Gravel",
            url = "https://www.adana.co.jp/en/contents/products/na_substrate/detail04.html",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.DENNERLE_NANO_SHRIMP_GRAVEL,
            organization = "Dennerle",
            title = "Nano Shrimp Gravel",
            url = "https://dennerle.com/en/products/nano-shrimp-gravel",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.JBL_SANSIBAR_DARK,
            organization = "JBL",
            title = "JBL Sansibar Dark",
            url = "https://www.jbl.de/en/theme-world/essential_section/57/" +
                "jbl-themeworld-for-your-hobby?country=lv",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.JBL_SANSIBAR_WHITE,
            organization = "JBL",
            title = "JBL Sansibar White",
            url = "https://www.jbl.de/en/theme-world/essential_section/57/" +
                "jbl-themeworld-for-your-hobby?country=lv",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.AQUAEL_BASALT_GRAVEL,
            organization = "Aquael",
            title = "Basalt aquarium gravel",
            url = "https://www.aquael.com/products/aquaristics/substrates-gravels/bazaltowe/",
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(SmartCareEvidenceTopic.SUBSTRATE),
            reviewedOn = REVIEW_DATE
        )
    )

    private val sourcesById = sources.associateBy(SmartCareEvidenceSource::id)

    init {
        require(sourcesById.size == SmartCareEvidenceId.entries.size)
        require(sources.all { source -> source.url.startsWith("https://") })
        require(sources.all { source -> source.topics.isNotEmpty() })
    }

    fun source(id: SmartCareEvidenceId): SmartCareEvidenceSource =
        requireNotNull(sourcesById[id])

    fun tags(vararg ids: SmartCareEvidenceId): List<String> =
        ids.map(SmartCareEvidenceId::stableId)
}
