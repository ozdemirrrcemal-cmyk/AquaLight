package com.aqua.aqualight.application.aquarium

/**
 * Product semantics used by aquarium policies. A display name or material category must never be
 * parsed to infer one of these values.
 */
enum class AquariumSubstrateSemantic {
    NOT_APPLICABLE,
    UNKNOWN,

    /**
     * Legacy gravel semantic: the product is not active soil, a nutrient base, or an additive.
     *
     * This does not mean the material is chemically neutral. Mineral gravels can still buffer or
     * raise pH/KH/GH while remaining non-active for AquaLight's active-soil care rules.
     */
    INERT,

    NUTRIENT_BASE,
    ACTIVE_SOIL,
    ADDITIVE
}

enum class AquariumSubstrateEvidenceStatus {
    VERIFIED_PRODUCT,
    UNVERIFIED_GENERIC
}

/**
 * Auditable product metadata attached to an exact, stable material-catalog identity.
 *
 * Source URLs are intentionally not shipped in the application. Verification is performed during
 * catalog review; runtime metadata keeps only a provenance organization and an internal evidence
 * record key.
 */
data class AquariumSubstrateProductMetadata(
    val semantic: AquariumSubstrateSemantic,
    val evidenceStatus: AquariumSubstrateEvidenceStatus,
    val sourceOrganization: String,
    val sourceRecordId: String,
    val reviewedOn: String,
    val catalogRevision: Int = 1
) {
    init {
        require(catalogRevision > 0)
        require(sourceOrganization.isNotBlank())
        require(sourceRecordId.isNotBlank())
        require(reviewedOn.matches(ISO_DATE_PATTERN))
        require(semantic != AquariumSubstrateSemantic.NOT_APPLICABLE)

        when (evidenceStatus) {
            AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT -> {
                require(semantic != AquariumSubstrateSemantic.UNKNOWN)
            }

            AquariumSubstrateEvidenceStatus.UNVERIFIED_GENERIC -> {
                require(semantic == AquariumSubstrateSemantic.UNKNOWN)
            }
        }
    }

    val isVerifiedProduct: Boolean
        get() = evidenceStatus == AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT

    private companion object {
        val ISO_DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
