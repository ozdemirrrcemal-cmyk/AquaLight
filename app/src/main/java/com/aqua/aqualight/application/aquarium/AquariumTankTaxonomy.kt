package com.aqua.aqualight.application.aquarium

object AquariumTankTaxonomy {
    const val TYPE_FISH = "Fish"
    const val TYPE_SHRIMP = "Shrimp"
    const val TYPE_PLANTED = "Planted"
    const val TYPE_MARINE = "Marine"
    const val TYPE_SOFTIES = "Softies"
    const val TYPE_MIXED_REEF = "Mixed Reef"
    const val TYPE_SPS = "SPS"
    const val TYPE_CORAL = "Coral"
    const val TYPE_OTHER = "Other"

    val tankTypeCodes: Set<String> = linkedSetOf(
        TYPE_FISH,
        TYPE_SHRIMP,
        TYPE_PLANTED,
        TYPE_MARINE,
        TYPE_SOFTIES,
        TYPE_MIXED_REEF,
        TYPE_SPS,
        TYPE_CORAL,
        TYPE_OTHER
    )

    const val STYLE_NATURE_AQUARIUM = "Nature Aquarium"
    const val STYLE_IWAGUMI = "Iwagumi"
    const val STYLE_DUTCH = "Dutch"
    const val STYLE_JUNGLE = "Jungle"
    const val STYLE_BIOTOPE = "Biotope"
    const val STYLE_BLACKWATER = "Blackwater"
    const val STYLE_FOREST = "Forest"
    const val STYLE_MOUNTAIN = "Mountain"
    const val STYLE_ISLAND = "Island"

    val presetStyleCodes: Set<String> = linkedSetOf(
        STYLE_NATURE_AQUARIUM,
        STYLE_IWAGUMI,
        STYLE_DUTCH,
        STYLE_JUNGLE,
        STYLE_BIOTOPE,
        STYLE_BLACKWATER,
        STYLE_FOREST,
        STYLE_MOUNTAIN,
        STYLE_ISLAND
    )

    fun isSupportedTankType(value: String): Boolean = value in tankTypeCodes
}
