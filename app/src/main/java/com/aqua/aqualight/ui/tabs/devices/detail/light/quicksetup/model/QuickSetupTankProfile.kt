package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

data class QuickSetupTankProfile(
    val tankId: Long? = null,
    val tankName: String = "Selected Tank",

    val volumeLiters: Int = 0,
    val setupAgeDays: Int = 0,

    val tankType: QuickSetupTankType = QuickSetupTankType.UNKNOWN,
    val tankStyle: QuickSetupTankStyle = QuickSetupTankStyle.UNKNOWN,

    val setupPhase: QuickSetupSetupPhase = QuickSetupSetupPhase.UNKNOWN,
    val techLevel: QuickSetupTechLevel = QuickSetupTechLevel.LOW_TECH,

    val hasCo2: Boolean = false,
    val hasFertilizer: Boolean = false,
    val hasNutrientSubstrate: Boolean = false,

    val plantCount: Int = 0,
    val plantDensity: QuickSetupPlantDensity = QuickSetupPlantDensity.NONE,
    val plantDemand: QuickSetupPlantDemand = QuickSetupPlantDemand.LOW,
    val hasGroundCoverPlants: Boolean = false,
    val hasStemPlants: Boolean = false,
    val hasEpiphytePlants: Boolean = false,
    val hasFloatingPlants: Boolean = false,
    val hasRedPlants: Boolean = false,

    val hasFish: Boolean = false,
    val hasShrimp: Boolean = false,
    val hasSnails: Boolean = false,
    val hasSensitiveLivestock: Boolean = false,

    val algaeRisk: QuickSetupAlgaeRisk = QuickSetupAlgaeRisk.NORMAL,

    val recommendationConfidence: QuickSetupRecommendationConfidence =
        QuickSetupRecommendationConfidence.MEDIUM,

    val profileWarnings: List<String> = emptyList()
)

enum class QuickSetupTankType {
    UNKNOWN,
    FRESHWATER,
    PLANTED,
    SHRIMP,
    FISH_ONLY,
    BLACKWATER,
    BRACKISH,
    MARINE,
    REEF
}

enum class QuickSetupTankStyle {
    UNKNOWN,
    NATURE,
    DUTCH,
    IWAGUMI,
    JUNGLE,
    LOW_TECH,
    HIGH_TECH,
    BIOTOPE,
    FISH_ONLY,
    SHRIMP_TANK
}

enum class QuickSetupSetupPhase {
    UNKNOWN,
    FIRST_WEEK,
    EARLY_START,
    STABILIZING,
    BALANCED_RAMP_UP,
    MATURE
}

enum class QuickSetupTechLevel {
    LOW_TECH,
    MID_TECH,
    HIGH_TECH
}

enum class QuickSetupPlantDensity {
    NONE,
    LOW,
    MEDIUM,
    DENSE
}

enum class QuickSetupPlantDemand {
    LOW,
    MEDIUM,
    HIGH
}

enum class QuickSetupAlgaeRisk {
    LOW,
    NORMAL,
    HIGH
}

enum class QuickSetupRecommendationConfidence {
    LOW,
    MEDIUM,
    HIGH
}