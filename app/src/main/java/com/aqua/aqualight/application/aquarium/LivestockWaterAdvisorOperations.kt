package com.aqua.aqualight.application.aquarium

enum class LivestockWaterAssessmentStatus {
    COMPATIBLE,
    OUT_OF_RANGE,
    NO_COMPARABLE_MEASUREMENTS,
    CUSTOM_UNVERIFIED,
    CATALOG_ENTRY_MISSING
}

data class LivestockWaterAssessment(
    val livestockId: Long,
    val catalogEntryId: String,
    val status: LivestockWaterAssessmentStatus,
    val compatibility: LivestockWaterCompatibility? = null
)

interface LivestockWaterAdvisorOperations {
    fun assess(
        livestock: AquariumLivestock,
        water: AquariumWaterSnapshot
    ): LivestockWaterAssessment

    fun assessTank(
        livestock: List<AquariumLivestock>,
        water: AquariumWaterSnapshot
    ): List<LivestockWaterAssessment>
}
