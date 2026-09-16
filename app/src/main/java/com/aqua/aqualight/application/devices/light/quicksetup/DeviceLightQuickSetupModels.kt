package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot

data class DeviceLightCalibrationProfile(
    val profileId: String,
    val revision: Int,
    val productKey: String,
    val hardwareRevision: String,
    val fixtureLengthMm: Int
)

data class DeviceLightQuickSetupTank(
    val tankId: Long,
    val tankName: String,
    /** Trusted civil date reported by the fixture scheduler, never the phone date. */
    val deviceLocalEpochDay: Long,
    val setupDateEpochDay: Long?,
    val tankHeightCm: Int,
    val hasPlants: Boolean,
    val plantDemand: DeviceLightPlantDemand,
    /** Highest demand proven by exact reviewed catalog records, excluding unknown plants. */
    val reviewedPlantDemandFloor: DeviceLightPlantDemand,
    val plantCatalogIds: Set<String>,
    val plantEvidenceSourceIds: Set<String>,
    val plantCoverage: AquariumPlantCoverage,
    val co2ComponentPresent: Boolean,
    val co2Readiness: AquariumCo2Readiness,
    val substrateSemantic: AquariumSubstrateSemantic,
    val substrateProductIds: Set<String>,
    val substrateEvidenceSourceIds: Set<String>,
    val daylightExposure: AquariumDaylightExposure,
    val daylightStartMinute: Int?,
    val daylightEndMinute: Int?,
    val preferredLightEndMinute: Int?,
    val surfaceGrowth: AquariumSurfaceGrowth,
    val latestObservationEpochDay: Long?,
    val hasShrimp: Boolean,
    val shelterAvailability: AquariumShelterAvailability,
    val waterDepthCm: Int?,
    val fixtureHeightAboveWaterCm: Int?,
    val profileUpdatedAtMillis: Long?,
    val installationUpdatedAtMillis: Long?,
    val plantedFreshwater: Boolean,
    val productKey: String,
    val productDisplayName: String,
    val hardwareRevision: String,
    val fixtureLengthMm: Int,
    val calibrationProfile: DeviceLightCalibrationProfile?,
    val lastLightingResetEpochDay: Long?,
    val lastAppliedPhotoperiodMinutes: Int?,
    val lastAppliedMaximumChannelPercent: Int?,
    val lastAppliedEpochDay: Long?,
    val nextReevaluationEpochDay: Long?,
    /** Last applied optical baseline, retained when the current measurement is invalidated. */
    val lastAppliedWaterDepthCm: Int? = null
)

sealed interface DeviceLightQuickSetupTankReadResult {
    data class Available(val tank: DeviceLightQuickSetupTank) :
        DeviceLightQuickSetupTankReadResult

    data class Failed(val failure: DeviceLightQuickSetupTankFailure) :
        DeviceLightQuickSetupTankReadResult
}

enum class DeviceLightQuickSetupTankFailure {
    INVALID_DEVICE,
    DEVICE_NOT_FOUND,
    DEVICE_IDENTITY_UNVERIFIED,
    DEVICE_RUNTIME_UNVERIFIED,
    DEVICE_TIME_UNVERIFIED,
    TANK_NOT_ASSIGNED,
    TANK_NOT_FOUND,
    UNAVAILABLE
}

sealed interface DeviceLightQuickSetupPersistenceResult {
    /** Immutable audit row was committed before the firmware mutation. */
    data class Prepared(val auditId: String) : DeviceLightQuickSetupPersistenceResult
    data object Saved : DeviceLightQuickSetupPersistenceResult
    data class Failed(val cause: Throwable? = null) : DeviceLightQuickSetupPersistenceResult
}

sealed interface DeviceLightQuickSetupRecordedOutcome {
    data class Applied(
        val plan: DeviceLightManagedPlanSnapshot
    ) : DeviceLightQuickSetupRecordedOutcome

    data object Failed : DeviceLightQuickSetupRecordedOutcome
    data object Indeterminate : DeviceLightQuickSetupRecordedOutcome
}

interface DeviceLightQuickSetupTankOperations {
    suspend fun readForDevice(deviceUid: String): DeviceLightQuickSetupTankReadResult

    /** Must complete before a proposal is sent to firmware. */
    suspend fun prepareRecommendation(
        deviceUid: String,
        input: DeviceLightQuickSetupInput,
        plan: DeviceLightQuickSetupPlan
    ): DeviceLightQuickSetupPersistenceResult

    suspend fun recordRecommendationOutcome(
        deviceUid: String,
        auditId: String,
        outcome: DeviceLightQuickSetupRecordedOutcome
    ): DeviceLightQuickSetupPersistenceResult
}

enum class DeviceLightPlantDemand {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH
}

internal fun DeviceLightPlantDemand.notBelow(
    minimum: DeviceLightPlantDemand
): DeviceLightPlantDemand = if (plantDemandRank() >= minimum.plantDemandRank()) this else minimum

private fun DeviceLightPlantDemand.plantDemandRank(): Int = when (this) {
    DeviceLightPlantDemand.UNKNOWN -> 0
    DeviceLightPlantDemand.LOW -> 1
    DeviceLightPlantDemand.MEDIUM -> 2
    DeviceLightPlantDemand.HIGH -> 3
}

enum class DeviceLightPlanConfidence {
    CONSERVATIVE_UNCALIBRATED,
    CALIBRATED
}

enum class DeviceLightPlanReason {
    EVIDENCE_SIX_HOUR_START,
    EVIDENCE_EIGHT_HOUR_BASELINE,
    CONTROLLED_SEVEN_HOUR_STEP,
    REVIEW_INTERVAL_HOLD,
    OPTICAL_GEOMETRY_CHANGE_RESET,
    BIOLOGICAL_PROFILE_CHANGE_RESET,
    LAST_APPLIED_OUTPUT_HOLD,
    LOW_LIGHT_PLANTS,
    MEDIUM_LIGHT_PLANTS,
    HIGH_LIGHT_PLANTS,
    CO2_READY_AT_LIGHT_ON,
    CO2_NOT_READY_GUARD,
    ACTIVE_SOIL_STARTUP,
    INDIRECT_DAYLIGHT,
    DIRECT_DAYLIGHT_GUARD,
    STABLE_ALGAE_HOLD,
    WORSENING_ALGAE_GUARD,
    TARGET_BIOFILM_PROTECTED,
    SPARSE_PLANTING_GUARD,
    SHRIMP_SHELTER_GUARD,
    UNCALIBRATED_OUTPUT_GUARD
}

enum class DeviceLightPlanWarning {
    SETUP_DATE_MISSING,
    SETUP_DATE_IN_FUTURE,
    NOT_PLANTED_FRESHWATER,
    NO_PLANTS,
    UNKNOWN_PLANT_DEMAND,
    HIGH_LIGHT_WITHOUT_READY_CO2,
    CALIBRATION_UNAVAILABLE,
    DEEP_INSTALLATION_UNCALIBRATED,
    DIRECT_DAYLIGHT_OVERLAP,
    SHRIMP_SHELTER_MISSING
}

enum class DeviceLightLifecycleStage(val durationMinutes: Int) {
    STARTUP(6 * 60),
    ACCLIMATION(7 * 60),
    ESTABLISHED(8 * 60)
}

data class DeviceLightQuickSetupInput(
    val plantDemand: DeviceLightPlantDemand,
    val plantCoverage: AquariumPlantCoverage,
    val waterDepthCm: Int,
    val fixtureHeightAboveWaterCm: Int,
    val co2Readiness: AquariumCo2Readiness,
    val substrateSemantic: AquariumSubstrateSemantic,
    val daylightExposure: AquariumDaylightExposure,
    val daylightStartMinute: Int?,
    val daylightEndMinute: Int?,
    val surfaceGrowth: AquariumSurfaceGrowth,
    val shelterAvailability: AquariumShelterAvailability,
    val programEndMinute: Int
)

data class DeviceLightQuickSetupPhase(
    val lifecycleStage: DeviceLightLifecycleStage,
    val draft: DeviceLightManagedPlanPhaseDraft
)

data class DeviceLightQuickSetupPlan(
    val recommendationId: String,
    val policyVersion: String,
    val evidenceSourceIds: Set<String>,
    val initialStartPercent: Int,
    val confidence: DeviceLightPlanConfidence,
    val calibrationProfileId: String,
    val calibrationRevision: Int,
    val maximumChannelPercent: Int,
    val reasons: Set<DeviceLightPlanReason>,
    val warnings: Set<DeviceLightPlanWarning>,
    val phases: List<DeviceLightQuickSetupPhase>,
    val reevaluationEpochDay: Long,
    val profileFingerprint: String
) {
    init {
        require(phases.size == 1) {
            "Commercial quick setup creates only the currently reviewed phase."
        }
    }

    fun toManagedPlanDraft(): DeviceLightManagedPlanDraft = DeviceLightManagedPlanDraft(
        initialStartPercent = initialStartPercent,
        phases = phases.map(DeviceLightQuickSetupPhase::draft)
    )

    val currentPhase: DeviceLightQuickSetupPhase
        get() = phases.single()

    val scene: DeviceLightAutomaticScene
        get() = currentPhase.draft.scene
}
