package com.aqua.aqualight.application.aquarium

/** Stable, non-localized category codes persisted for aquarium livestock. */
object AquariumLivestockCategory {
    const val FISH = "Fish"
    const val SHRIMP = "Shrimp"
    const val SNAIL = "Snail"
    const val CRAB_CRAYFISH = "Crab / Crayfish"
    const val CORAL = "Coral"
    const val OTHER = "Other"

    val codes: Set<String> = linkedSetOf(
        FISH,
        SHRIMP,
        SNAIL,
        CRAB_CRAYFISH,
        CORAL,
        OTHER
    )

    fun isSupported(value: String): Boolean = value in codes
}
