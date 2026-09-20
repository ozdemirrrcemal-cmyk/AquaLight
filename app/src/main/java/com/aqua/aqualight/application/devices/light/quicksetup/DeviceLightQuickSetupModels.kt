package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic

data class DeviceLightQuickSetupPlant(
    val catalogId: String,
    val displayName: String
)

data class DeviceLightQuickSetupContext(
    val deviceUid: String,
    val productKey: String,
    val productDisplayName: String,
    val channelKeys: List<String>,
    val tankId: Long,
    val aquariumName: String,
    val setupDateEpochDay: Long,
    val tankWidthCm: Int,
    val tankLengthCm: Int,
    val tankHeightCm: Int,
    val tankType: String,
    val tankStyle: String,
    val plants: List<DeviceLightQuickSetupPlant>,
    val substrateSemantic: AquariumSubstrateSemantic,
    val co2Present: Boolean,
    val profileFingerprint: String
)

sealed interface DeviceLightQuickSetupContextResult {
    data class Available(
        val context: DeviceLightQuickSetupContext
    ) : DeviceLightQuickSetupContextResult

    data class Blocked(
        val reason: DeviceLightQuickSetupBlockReason
    ) : DeviceLightQuickSetupContextResult
}

enum class DeviceLightQuickSetupBlockReason {
    INVALID_DEVICE_UID,
    DEVICE_NOT_REGISTERED,
    DEVICE_METADATA_NOT_READY,
    DEVICE_NOT_ASSIGNED,
    AQUARIUM_NOT_FOUND,
    MULTIPLE_LIGHT_FIXTURES_UNSUPPORTED,
    UNSUPPORTED_PRODUCT,
    NO_PLANTS,
    MISSING_PLANT_CATALOG_ID,
    UNKNOWN_PLANT_CATALOG_ID,
    MISSING_SETUP_DATE,
    MISSING_CALIBRATION,
    CALIBRATION_GEOMETRY_UNSUPPORTED,
    INSUFFICIENT_FIXTURE_COVERAGE,
    INVALID_INPUT,
    CONNECTION_UNAVAILABLE,
    RTC_NOT_READY,
    STALE_CONTEXT,
    MALFORMED_FIRMWARE_STATE,
    DEVICE_WRITE_FAILED
}

enum class DeviceLightQuickSetupCo2Readiness {
    NOT_PRESENT,
    PRESENT_NOT_PRECHARGED,
    PRESENT_PRECHARGED
}

data class DeviceLightQuickSetupInput(
    val waterHeightCm: Int,
    val fixtureHeightAboveWaterCm: Int,
    val firstLightOnMinuteOfDay: Int,
    val co2Readiness: DeviceLightQuickSetupCo2Readiness
)

data class DeviceLightQuickSetupPlantProfile(
    val selectedPlantCount: Int,
    val uniqueSpeciesCount: Int,
    val lowDemandCount: Int,
    val mediumDemandCount: Int,
    val highDemandCount: Int,
    val highestDemand: AquariumPlantLightDemand,
    val plantCatalogRevision: Int
)

enum class DeviceLightFixtureCalibrationStatus {
    PLACEHOLDER,
    CALIBRATED
}

enum class DeviceLightFixtureCoverageStatus {
    COVERED,
    INSUFFICIENT
}

data class DeviceLightFixtureCalibrationRequest(
    val productKey: String,
    val tankWidthCm: Int,
    val tankLengthCm: Int,
    val waterHeightCm: Int,
    val fixtureHeightAboveWaterCm: Int,
    val targetPpfd: Int,
    val channelKeys: List<String>
)

data class DeviceLightFixtureCalibrationResult(
    val status: DeviceLightFixtureCalibrationStatus,
    val calibrationRevision: Int,
    val channelScenePercent: Map<String, Int>,
    val estimatedPpfd: Int,
    val coverageStatus: DeviceLightFixtureCoverageStatus,
    val initialStartPercent: Int
)

interface DeviceLightFixtureCalibration {
    fun solve(
        request: DeviceLightFixtureCalibrationRequest
    ): DeviceLightFixtureCalibrationResult?
}

data class DeviceLightQuickSetupPhase(
    val validFromEpochDay: Int,
    val validUntilEpochDayExclusive: Int?,
    val transitionDays: Int,
    val weekdaysMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val rampMinutes: Int,
    val channelScenePercent: Map<String, Int>
)

data class DeviceLightQuickSetupRecommendation(
    val contextFingerprint: String,
    val algorithmRevision: Int,
    val plantProfile: DeviceLightQuickSetupPlantProfile,
    val requestedTargetPpfd: Int,
    val effectiveTargetPpfd: Int,
    val co2Limited: Boolean,
    val calibration: DeviceLightFixtureCalibrationResult,
    val initialStartPercent: Int,
    val phases: List<DeviceLightQuickSetupPhase>,
    val evidenceIds: Set<String>
) {
    val productionReady: Boolean
        get() = calibration.status == DeviceLightFixtureCalibrationStatus.CALIBRATED
}

sealed interface DeviceLightQuickSetupRecommendationResult {
    data class Available(
        val recommendation: DeviceLightQuickSetupRecommendation
    ) : DeviceLightQuickSetupRecommendationResult

    data class Blocked(
        val reason: DeviceLightQuickSetupBlockReason
    ) : DeviceLightQuickSetupRecommendationResult
}
