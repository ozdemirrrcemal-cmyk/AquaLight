package com.aqua.aqualight.application.aquarium

object AquariumLivestockTaxonomy {
    const val FISH = "Fish"
    const val SHRIMP = "Shrimp"
    const val SNAIL = "Snail"
    const val CRAB_CRAYFISH = "Crab / Crayfish"
    const val CORAL = "Coral"
    const val OTHER = "Other"

    val categoryCodes: Set<String> = linkedSetOf(
        FISH,
        SHRIMP,
        SNAIL,
        CRAB_CRAYFISH,
        CORAL,
        OTHER
    )
}

object AquariumLivestockIdentity {
    private const val CUSTOM_PREFIX = "custom:"

    fun custom(
        livestockId: Long
    ): String {
        require(livestockId > 0L) {
            "Custom livestock identity requires a positive livestock id."
        }
        return "$CUSTOM_PREFIX$livestockId"
    }

    fun isCustom(
        catalogEntryId: String
    ): Boolean = catalogEntryId.startsWith(CUSTOM_PREFIX)

    fun requireValid(
        livestockId: Long,
        catalogEntryId: String
    ) {
        require(catalogEntryId.isNotBlank() && catalogEntryId == catalogEntryId.trim()) {
            "Livestock catalog identity must be non-blank and canonical."
        }

        if (isCustom(catalogEntryId)) {
            require(catalogEntryId == custom(livestockId)) {
                "Custom livestock catalog identity must match its livestock id."
            }
        }
    }
}
