package com.aqua.aqualight.application.aquarium

/**
 * Product semantics used by aquarium policies. A display name or material category must never be
 * parsed to infer one of these values.
 */
enum class AquariumSubstrateSemantic {
    NOT_APPLICABLE,
    UNKNOWN,
    INERT,
    NUTRIENT_BASE,
    ACTIVE_SOIL,
    ADDITIVE
}

enum class AquariumSubstrateEvidenceStatus {
    VERIFIED_PRODUCT,
    UNVERIFIED_GENERIC
}

/** Auditable product metadata attached to an exact, stable material-catalog identity. */
data class AquariumSubstrateProductMetadata(
    val semantic: AquariumSubstrateSemantic,
    val evidenceStatus: AquariumSubstrateEvidenceStatus,
    val sourceOrganization: String,
    val sourceRecordId: String,
    val sourceUrl: String?,
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
                require(sourceUrl?.startsWith("https://") == true)
            }

            AquariumSubstrateEvidenceStatus.UNVERIFIED_GENERIC -> {
                require(semantic == AquariumSubstrateSemantic.UNKNOWN)
                require(sourceUrl == null)
            }
        }
    }

    val isVerifiedProduct: Boolean
        get() = evidenceStatus == AquariumSubstrateEvidenceStatus.VERIFIED_PRODUCT

    private companion object {
        val ISO_DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
