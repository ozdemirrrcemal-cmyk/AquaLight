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
    PLANT_STARTUP_METHOD("plant_startup_method"),
    PLANT_STARTUP_QUICK_GUIDE("plant_startup_quick_guide"),
    PLANT_NUTRITION_MACRO_MICRO_GUIDE("plant_nutrition_macro_micro_guide"),
    PLANT_NUTRITION_MICRO_GUIDE("plant_nutrition_micro_guide"),
    AQUASCAPE_STARTUP_GUIDE("aquascape_startup_guide"),
    AQUASCAPE_FERTILIZATION_GUIDE("aquascape_fertilization_guide"),
    FISH_HEALTH_REFERENCE("fish_health_reference")
}

data class SmartCareEvidenceSource(
    val id: SmartCareEvidenceId,
    val kind: SmartCareEvidenceKind,
    val topics: Set<SmartCareEvidenceTopic>,
    val reviewedOn: String,
    val requiresProfessionalDiagnosis: Boolean = false
)

/** Versioned internal evidence categories behind Smart Care decisions. */
object SmartCareEvidenceCatalog {

    private const val REVIEW_DATE = "2026-09-15"

    val sources: List<SmartCareEvidenceSource> = listOf(
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.PLANT_STARTUP_METHOD,
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
            id = SmartCareEvidenceId.PLANT_STARTUP_QUICK_GUIDE,
            kind = SmartCareEvidenceKind.OFFICIAL_METHOD_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.STARTUP,
                SmartCareEvidenceTopic.LIGHTING
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.PLANT_NUTRITION_MACRO_MICRO_GUIDE,
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.FERTILIZER,
                SmartCareEvidenceTopic.ALGAE,
                SmartCareEvidenceTopic.WATER_CHANGE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.PLANT_NUTRITION_MICRO_GUIDE,
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.FERTILIZER,
                SmartCareEvidenceTopic.PLANT_CARE,
                SmartCareEvidenceTopic.WATER_CHANGE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.AQUASCAPE_STARTUP_GUIDE,
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
            id = SmartCareEvidenceId.AQUASCAPE_FERTILIZATION_GUIDE,
            kind = SmartCareEvidenceKind.OFFICIAL_PRODUCT_GUIDE,
            topics = setOf(
                SmartCareEvidenceTopic.FERTILIZER,
                SmartCareEvidenceTopic.PLANT_CARE
            ),
            reviewedOn = REVIEW_DATE
        ),
        SmartCareEvidenceSource(
            id = SmartCareEvidenceId.FISH_HEALTH_REFERENCE,
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
        require(sources.all { source -> source.topics.isNotEmpty() })
    }

    fun source(id: SmartCareEvidenceId): SmartCareEvidenceSource =
        requireNotNull(sourcesById[id])

    fun tags(vararg ids: SmartCareEvidenceId): List<String> =
        ids.map(SmartCareEvidenceId::stableId)
}
