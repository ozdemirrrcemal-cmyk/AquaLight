package com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock

import com.aqua.aqualight.application.aquarium.AquariumLivestockCategory

object LivestockCategories {

    const val FISH = AquariumLivestockCategory.FISH
    const val SHRIMP = AquariumLivestockCategory.SHRIMP
    const val SNAIL = AquariumLivestockCategory.SNAIL
    const val CRAB_CRAYFISH = AquariumLivestockCategory.CRAB_CRAYFISH
    const val CORAL = AquariumLivestockCategory.CORAL
    const val OTHER = AquariumLivestockCategory.OTHER

    val all: List<String> = AquariumLivestockCategory.codes.toList()
}
