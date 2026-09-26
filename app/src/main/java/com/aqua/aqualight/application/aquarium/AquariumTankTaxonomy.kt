package com.aqua.aqualight.application.aquarium

/**
 * Stable aquarium classification codes.
 *
 * The persisted tankType field stores a canonical tank profile code. Water environment is derived
 * from that profile so environment/profile state cannot drift apart. Tank style remains an
 * independent axis.
 */
object AquariumTankTaxonomy {
    const val WATER_ENVIRONMENT_FRESHWATER = "Freshwater"
    const val WATER_ENVIRONMENT_BRACKISH = "Brackish"
    const val WATER_ENVIRONMENT_MARINE = "Marine"

    val waterEnvironmentCodes: Set<String> = linkedSetOf(
        WATER_ENVIRONMENT_FRESHWATER,
        WATER_ENVIRONMENT_BRACKISH,
        WATER_ENVIRONMENT_MARINE
    )

    const val TYPE_FRESHWATER_FISH = "Freshwater Fish"
    const val TYPE_PLANTED = "Planted"
    const val TYPE_SHRIMP = "Shrimp"
    const val TYPE_BRACKISH_GENERAL = "Brackish General"
    const val TYPE_MARINE_FISH = "Marine Fish"
    const val TYPE_SOFT_CORAL_REEF = "Soft Coral Reef"
    const val TYPE_LPS_REEF = "LPS Reef"
    const val TYPE_SPS_REEF = "SPS Reef"
    const val TYPE_MIXED_REEF = "Mixed Reef"
    const val TYPE_OTHER_FRESHWATER = "Other Freshwater"
    const val TYPE_OTHER_BRACKISH = "Other Brackish"
    const val TYPE_OTHER_MARINE = "Other Marine"

    private val freshwaterTankTypes = listOf(
        TYPE_FRESHWATER_FISH,
        TYPE_PLANTED,
        TYPE_SHRIMP,
        TYPE_OTHER_FRESHWATER
    )

    private val brackishTankTypes = listOf(
        TYPE_BRACKISH_GENERAL,
        TYPE_OTHER_BRACKISH
    )

    private val marineTankTypes = listOf(
        TYPE_MARINE_FISH,
        TYPE_SOFT_CORAL_REEF,
        TYPE_LPS_REEF,
        TYPE_SPS_REEF,
        TYPE_MIXED_REEF,
        TYPE_OTHER_MARINE
    )

    val tankTypeCodes: Set<String> = linkedSetOf<String>().apply {
        addAll(freshwaterTankTypes)
        addAll(brackishTankTypes)
        addAll(marineTankTypes)
    }

    fun tankTypeCodesForEnvironment(environment: String): List<String> = when (environment) {
        WATER_ENVIRONMENT_FRESHWATER -> freshwaterTankTypes
        WATER_ENVIRONMENT_BRACKISH -> brackishTankTypes
        WATER_ENVIRONMENT_MARINE -> marineTankTypes
        else -> emptyList()
    }

    fun environmentForTankType(tankType: String): String? = when (tankType) {
        in freshwaterTankTypes -> WATER_ENVIRONMENT_FRESHWATER
        in brackishTankTypes -> WATER_ENVIRONMENT_BRACKISH
        in marineTankTypes -> WATER_ENVIRONMENT_MARINE
        else -> null
    }

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

    fun isSupportedWaterEnvironment(value: String): Boolean = value in waterEnvironmentCodes

    fun isSupportedTankType(value: String): Boolean = value in tankTypeCodes
}
