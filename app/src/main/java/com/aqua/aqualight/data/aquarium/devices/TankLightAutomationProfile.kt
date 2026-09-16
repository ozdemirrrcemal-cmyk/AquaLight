package com.aqua.aqualight.data.aquarium.devices

data class TankLightInstallationProfile(
    val contractRevision: Int = CONTRACT_REVISION,
    val fixtureHeightAboveWaterCm: Int? = null,
    val installedAtEpochDay: Long? = null,
    val lastFixtureChangeEpochDay: Long? = null,
    val updatedAtMillis: Long? = null
) {
    companion object {
        const val CONTRACT_REVISION = 1
    }
}

data class TankLightRecommendationSnapshot(
    val recommendationId: String,
    val profileFingerprint: String,
    val policyVersion: String,
    val evidenceSourceIds: List<String>,
    val reasonCodes: List<String>,
    val warningCodes: List<String>,
    val plantCatalogIds: List<String>,
    val substrateProductIds: List<String>,
    val productKey: String,
    val hardwareRevision: String,
    val fixtureLengthMm: Int,
    val calibrationProfileId: String,
    val calibrationRevision: Int,
    val confidence: String,
    val tankSetupEpochDay: Long?,
    val tankHeightCm: Int,
    val hasPlants: Boolean,
    val plantedFreshwater: Boolean,
    val co2ComponentPresent: Boolean,
    val hasShrimp: Boolean,
    val waterDepthCm: Int,
    val fixtureHeightAboveWaterCm: Int,
    val plantDemand: String,
    val plantCoverage: String,
    val co2Readiness: String,
    val substrateSemantic: String,
    val daylightExposure: String,
    val daylightStartMinute: Int?,
    val daylightEndMinute: Int?,
    val surfaceGrowth: String,
    val shelterAvailability: String,
    val programEndMinute: Int,
    val programStartMinute: Int,
    val rampDurationMinutes: Int,
    val photoperiodMinutes: Int,
    val initialStartPercent: Int,
    val redPercent: Int,
    val greenPercent: Int,
    val bluePercent: Int,
    val whitePercent: Int?,
    val maximumChannelPercent: Int,
    /** Fixture-local civil day used to calculate and start this recommendation. */
    val recommendationEpochDay: Long,
    val reevaluationEpochDay: Long,
    val lastLightingResetEpochDay: Long?,
    val priorAppliedPhotoperiodMinutes: Int?,
    val priorAppliedMaximumChannelPercent: Int?,
    val priorAppliedEpochDay: Long?,
    val profileUpdatedAtMillis: Long?,
    val installationUpdatedAtMillis: Long?,
    val createdAtMillis: Long,
    val appliedAtMillis: Long? = null,
    val firmwarePlanId: String? = null,
    val firmwarePlanRevision: Long? = null,
    val firmwareStorageGeneration: Long? = null,
    val outcome: TankLightRecommendationOutcome
)

enum class TankLightRecommendationOutcome {
    PREPARED,
    APPLIED,
    FAILED,
    INDETERMINATE
}
