package com.aqua.aqualight.application.aquarium

/** Stable, locale-independent biological demand stored with a selected plant. */
enum class AquariumPlantLightDemand {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH
}

/** Approximate planted footprint; plant item count is intentionally not used as density. */
enum class AquariumPlantCoverage {
    UNKNOWN,
    SPARSE,
    MEDIUM,
    DENSE
}

enum class AquariumCanopyDensity {
    UNKNOWN,
    OPEN,
    PARTIAL,
    CLOSED
}

/**
 * CO2 equipment presence comes from the component inventory. This value records whether injected
 * CO2 is actually available when photosynthetic light starts; it never infers operation from
 * equipment ownership.
 */
enum class AquariumCo2Readiness {
    NOT_INSTALLED,
    UNKNOWN,
    READY_AT_LIGHT_ON,
    NOT_READY_AT_LIGHT_ON
}

enum class AquariumDaylightExposure {
    UNKNOWN,
    LOW,
    INDIRECT,
    DIRECT
}

/** One short observation distinguishes wanted shrimp biofilm from an algae warning. */
enum class AquariumSurfaceGrowth {
    UNKNOWN,
    NONE,
    TARGET_BIOFILM,
    STABLE_ALGAE,
    WORSENING_ALGAE
}

enum class AquariumShelterAvailability {
    NOT_REQUIRED,
    UNKNOWN,
    ADEQUATE,
    LIMITED,
    NONE
}

/** Explicit substrate semantics. A category name alone is never treated as active soil. */
enum class AquariumSubstrateSemantic {
    NOT_APPLICABLE,
    UNKNOWN,
    INERT,
    NUTRIENT_BASE,
    ACTIVE_SOIL,
    ADDITIVE
}

data class AquariumSubstrateCatalogRecord(
    val productId: String,
    val categoryKey: String,
    val semantic: AquariumSubstrateSemantic,
    val evidenceSourceId: String?,
    val catalogRevision: Int = 1
)

/**
 * Locale-independent, reviewed substrate catalog used by care and lighting policies. Product
 * names are never parsed. A free-text product therefore remains UNKNOWN until it is catalogued.
 */
object AquariumSubstrateSemantics {
    private const val CATEGORY_SUBSTRATE = AquariumMaterialCategory.SUBSTRATE
    private const val CATEGORY_GRAVEL = AquariumMaterialCategory.GRAVEL

    val records: List<AquariumSubstrateCatalogRecord> = listOf(
        AquariumSubstrateCatalogRecord(
            productId = "substrate_chihiros_aquasoil_9l",
            categoryKey = CATEGORY_SUBSTRATE,
            semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
            evidenceSourceId = "chihiros_aqua_soil_launch"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "substrate_chihiros_aquasoil_3l",
            categoryKey = CATEGORY_SUBSTRATE,
            semantic = AquariumSubstrateSemantic.ACTIVE_SOIL,
            evidenceSourceId = "chihiros_aqua_soil_launch"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "substrate_ada_tourmaline_bc",
            categoryKey = CATEGORY_SUBSTRATE,
            semantic = AquariumSubstrateSemantic.ADDITIVE,
            evidenceSourceId = "ada_tourmaline_bc"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "substrate_dennerle_deponit_mix_pro_4_8kg",
            categoryKey = CATEGORY_SUBSTRATE,
            semantic = AquariumSubstrateSemantic.NUTRIENT_BASE,
            evidenceSourceId = "dennerle_deponit_mix_pro"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_ada_aqua_gravel_s",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "ada_aqua_gravel"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_ada_aqua_gravel_m",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "ada_aqua_gravel"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_dennerle_nano_shrimp_gravel_sulawesi_black",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "dennerle_nano_shrimp_gravel"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_dennerle_nano_shrimp_gravel_arkansas_grey",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "dennerle_nano_shrimp_gravel"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_dennerle_nano_shrimp_gravel_borneo_brown",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "dennerle_nano_shrimp_gravel"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_dennerle_nano_shrimp_gravel_sunda_white",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "dennerle_nano_shrimp_gravel"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_jbl_sansibar_dark",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "jbl_sansibar_dark"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_jbl_sansibar_white",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "jbl_sansibar_white"
        ),
        AquariumSubstrateCatalogRecord(
            productId = "gravel_aquael_basalt_gravel",
            categoryKey = CATEGORY_GRAVEL,
            semantic = AquariumSubstrateSemantic.INERT,
            evidenceSourceId = "aquael_basalt_gravel"
        ),
    )
    private val byProductId = records.associateBy(AquariumSubstrateCatalogRecord::productId)

    init {
        require(byProductId.size == records.size)
        require(records.none { record ->
            record.semantic == AquariumSubstrateSemantic.UNKNOWN ||
                record.semantic == AquariumSubstrateSemantic.NOT_APPLICABLE
        })
        require(records.all { record -> !record.evidenceSourceId.isNullOrBlank() })
    }

    fun resolve(productId: String, categoryKey: String): AquariumSubstrateSemantic =
        byProductId[productId]
            ?.takeIf { record -> record.categoryKey == categoryKey }
            ?.semantic ?: when (categoryKey) {
            CATEGORY_GRAVEL, CATEGORY_SUBSTRATE -> AquariumSubstrateSemantic.UNKNOWN
            else -> AquariumSubstrateSemantic.NOT_APPLICABLE
        }

    fun record(productId: String): AquariumSubstrateCatalogRecord? = byProductId[productId]

    /** Combines exact selected products without parsing a display name or category label. */
    fun aggregate(productIds: Iterable<String>): AquariumSubstrateSemantic {
        val semantics = productIds.map { productId ->
            byProductId[productId]?.semantic ?: AquariumSubstrateSemantic.UNKNOWN
        }
        return when {
            AquariumSubstrateSemantic.ACTIVE_SOIL in semantics ->
                AquariumSubstrateSemantic.ACTIVE_SOIL
            AquariumSubstrateSemantic.UNKNOWN in semantics -> AquariumSubstrateSemantic.UNKNOWN
            AquariumSubstrateSemantic.NUTRIENT_BASE in semantics ->
                AquariumSubstrateSemantic.NUTRIENT_BASE
            AquariumSubstrateSemantic.INERT in semantics -> AquariumSubstrateSemantic.INERT
            AquariumSubstrateSemantic.ADDITIVE in semantics -> AquariumSubstrateSemantic.ADDITIVE
            else -> AquariumSubstrateSemantic.NOT_APPLICABLE
        }
    }

    /** Existing catalog identities are valid only in their reviewed material category. */
    fun matchesCatalogCategory(productId: String, categoryKey: String): Boolean =
        byProductId[productId]?.categoryKey?.let { expected -> expected == categoryKey } ?: true

    fun evidenceSourceIds(productIds: Iterable<String>): Set<String> = productIds
        .mapNotNull { productId -> byProductId[productId]?.evidenceSourceId }
        .toSet()
}

/**
 * Durable tank-level inputs for commercial automation. Nullable measurements are genuinely
 * unknown; zero is never used as an estimated physical value.
 */
data class AquariumAutomationProfile(
    val contractRevision: Int = CONTRACT_REVISION,
    val waterDepthCm: Int? = null,
    val substrateDepthCm: Int? = null,
    val plantDemandOverride: AquariumPlantLightDemand = AquariumPlantLightDemand.UNKNOWN,
    val plantCoverage: AquariumPlantCoverage = AquariumPlantCoverage.UNKNOWN,
    val canopyDensity: AquariumCanopyDensity = AquariumCanopyDensity.UNKNOWN,
    val co2Readiness: AquariumCo2Readiness = AquariumCo2Readiness.NOT_INSTALLED,
    val daylightExposure: AquariumDaylightExposure = AquariumDaylightExposure.UNKNOWN,
    val daylightStartMinute: Int? = null,
    val daylightEndMinute: Int? = null,
    val preferredLightEndMinute: Int? = null,
    val lastMajorPlantingEpochDay: Long? = null,
    val latestSurfaceGrowth: AquariumSurfaceGrowth = AquariumSurfaceGrowth.UNKNOWN,
    val latestObservationEpochDay: Long? = null,
    val observedAreaPercent: Int? = null,
    val observationLocation: String = "",
    val shelterAvailability: AquariumShelterAvailability =
        AquariumShelterAvailability.NOT_REQUIRED,
    val updatedAtMillis: Long? = null
) {
    companion object {
        const val CONTRACT_REVISION = 1
    }
}
