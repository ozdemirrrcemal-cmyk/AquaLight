package com.aqua.aqualight.data.aquarium.catalog.livestock

object LivestockCategories {

    const val FISH = "Fish"
    const val SHRIMP = "Shrimp"
    const val SNAIL = "Snail"
    const val CRAB_CRAYFISH = "Crab / Crayfish"
    const val CORAL = "Coral"
    const val OTHER = "Other"

    val all: List<String> = listOf(
        FISH,
        SHRIMP,
        SNAIL,
        CRAB_CRAYFISH,
        CORAL,
        OTHER
    )
}
