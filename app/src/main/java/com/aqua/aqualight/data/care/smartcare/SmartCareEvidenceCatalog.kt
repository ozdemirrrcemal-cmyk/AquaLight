package com.aqua.aqualight.data.care.smartcare

enum class SmartCareEvidenceTopic {
    STARTUP,
    LIGHTING,
    WATER_CHANGE,
    FERTILIZER,
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
    VETERINARY_REFERENCE
}

enum class SmartCareEvidenceId(val stableId: String) {
    TROPICA_GROWING_IN("tropica_growing_in"),
    TROPICA_QUICK_GUIDE("tropica_quick_guide"),
    TROPICA_SPECIALISED_NUTRITION("tropica_specialised_nutrition"),
    TROPICA_PREMIUM_NUTRITION("tropica_premium_nutrition"),
    ADA_STARTING_FROM_ZERO("ada_starting_from_zero"),
    ADA_LIQUID_FERTILIZERS("ada_liquid_fertilizers"),
    UF_IFAS_FISH_HEALTH("uf_ifas_fish_health")
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

    private const val REVIEW_DATE = "2026-09-15"

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
