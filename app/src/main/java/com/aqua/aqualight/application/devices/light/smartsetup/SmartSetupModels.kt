package com.aqua.aqualight.application.devices.light.smartsetup

import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand

enum class SmartSetupAquariumEnvironment {
    FRESHWATER,
    MARINE,
    UNKNOWN
}

enum class SmartSetupLifecycleStage {
    STARTUP,
    ESTABLISHING,
    MATURE
}

enum class SmartSetupChannel(val sceneKey: String) {
    RED("redPercent"),
    GREEN("greenPercent"),
    BLUE("bluePercent"),
    WHITE("whitePercent")
}

@Suppress("MagicNumber")
data class SmartSetupCalibrationProfile(
    val id: String,
    val productKey: String,
    val revision: Int,
    val channels: List<SmartSetupChannel>,
    val minimumWaterDepthCm: Int,
    val maximumWaterDepthCm: Int,
    val minimumFixtureMountHeightCm: Int,
    val maximumFixtureMountHeightCm: Int
) {
    init {
        require(CALIBRATION_ID.matches(id))
        require(productKey.isNotBlank())
        require(revision > 0)
        require(channels.isNotEmpty() && channels.distinct().size == channels.size)
        require(minimumWaterDepthCm in 1..maximumWaterDepthCm)
        require(minimumFixtureMountHeightCm in 0..maximumFixtureMountHeightCm)
    }

    private companion object {
        val CALIBRATION_ID = Regex("^[a-z0-9][a-z0-9._-]{2,79}$")
    }
}

data class SmartSetupMaintenanceObservations(
    val latestWaterChangeEpochDay: Long? = null,
    val latestAlgaeCleaningEpochDay: Long? = null,
    val latestPlantHealthCheckEpochDay: Long? = null,
    val latestCo2CheckEpochDay: Long? = null,
    val overdueRelevantTaskCount: Int = 0
) {
    init {
        require(overdueRelevantTaskCount >= 0)
    }
}

/** Complete semantic input. Nullable facts are unknown, never implicit defaults. */
data class SmartSetupInput(
    val tankId: Long,
    val tankName: String,
    val aquariumEnvironment: SmartSetupAquariumEnvironment,
    val evaluationEpochDay: Long?,
    val setupDateEpochDay: Long?,
    val setupDay: Int?,
    val lifecycleStage: SmartSetupLifecycleStage?,
    val isPlanted: Boolean,
    val plantDensity: PlantDensity?,
    val highestPlantLightDemand: PlantLightDemand?,
    val co2Status: Co2Status?,
    val isActiveSoil: Boolean?,
    val waterDepthCm: Int?,
    val fixtureMountHeightCm: Int?,
    val deviceProductKey: String,
    val reportedCalibrationRevision: Int?,
    val reportedChannelSceneKeys: List<String>,
    val calibrationProfile: SmartSetupCalibrationProfile?,
    val preferredViewingStartMinuteOfDay: Int?,
    val preferredViewingEndMinuteOfDay: Int?,
    val algaeObservation: AquariumObservationSeverity?,
    val plantStressObservation: AquariumObservationSeverity?,
    val observationDateEpochDay: Long?,
    val maintenance: SmartSetupMaintenanceObservations
) {
    init {
        require(tankId > 0L)
        require(tankName.isNotBlank())
        require(deviceProductKey.isNotBlank())
        require(reportedChannelSceneKeys.all(String::isNotBlank))
        require(reportedChannelSceneKeys.distinct().size == reportedChannelSceneKeys.size)
    }
}

enum class SmartSetupMissingField {
    DEVICE_LOCAL_DATE,
    SETUP_DATE,
    PLANT_DENSITY,
    HIGHEST_PLANT_LIGHT_DEMAND,
    CO2_STATUS,
    ACTIVE_SOIL,
    WATER_DEPTH,
    FIXTURE_MOUNT_HEIGHT,
    VIEWING_WINDOW,
    ALGAE_OBSERVATION,
    PLANT_STRESS_OBSERVATION,
    OBSERVATION_DATE,
    OBSERVATIONS_STALE,
    CALIBRATION_PROFILE
}

enum class SmartSetupUnsupportedReason {
    UNKNOWN_AQUARIUM_ENVIRONMENT,
    MARINE_EVIDENCE_NOT_AVAILABLE,
    SETUP_DATE_IN_FUTURE,
    LIFECYCLE_MISMATCH,
    INCONSISTENT_PLANT_FACTS,
    OBSERVATION_DATE_IN_FUTURE,
    OBSERVATION_BEFORE_SETUP,
    UNKNOWN_DEVICE_PRODUCT,
    CALIBRATION_NOT_AVAILABLE_FOR_PRODUCT,
    DEVICE_CALIBRATION_METADATA_MISSING,
    CALIBRATION_PRODUCT_MISMATCH,
    CALIBRATION_PROFILE_MISMATCH,
    UNSUPPORTED_CHANNEL_SET,
    CALENDAR_OUTSIDE_FIRMWARE_RANGE,
    INSTALLATION_OUTSIDE_CALIBRATION,
    VIEWING_WINDOW_UNSUPPORTED
}

enum class SmartSetupConfidence {
    MODERATE,
    HIGH
}

enum class SmartSetupFactorId {
    AQUARIUM_STAGE,
    UNPLANTED_AQUARIUM,
    PLANT_DENSITY,
    PLANT_LIGHT_DEMAND,
    CO2_STATE,
    ACTIVE_SOIL,
    OPTICAL_DISTANCE,
    ALGAE_OBSERVATION,
    PLANT_STRESS_OBSERVATION,
    RECENT_ALGAE_MAINTENANCE,
    OVERDUE_MAINTENANCE,
    VIEWING_WINDOW,
    DEVICE_CALIBRATION
}

enum class SmartSetupFactorEffect {
    INCREASE,
    DECREASE,
    LIMIT,
    SCHEDULE,
    INFORMATION
}

data class SmartSetupFactor(
    val id: SmartSetupFactorId,
    val value: String,
    val effect: SmartSetupFactorEffect
) {
    init {
        require(value.isNotBlank())
    }
}

data class SmartLightScene(val channels: Map<SmartSetupChannel, Int>) {
    init {
        require(channels.isNotEmpty())
        require(channels.values.all { value -> value in 0..MAX_CHANNEL_PERCENT })
    }
}

private const val MAX_CHANNEL_PERCENT = 100

data class SmartLightPhaseDraft(
    val validFromEpochDay: Long,
    val validUntilEpochDayExclusive: Long?,
    val transitionDays: Int,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: SmartLightScene
)

data class SmartLightPlanDraft(
    val initialStartPercent: Int,
    val phases: List<SmartLightPhaseDraft>
)

data class SmartSetupRecommendation(
    val plan: SmartLightPlanDraft,
    val confidence: SmartSetupConfidence,
    val factors: List<SmartSetupFactor>,
    val sourceIds: Set<String>,
    val calibrationProfileId: String,
    val profileFingerprint: String,
    val generatedEpochDay: Long,
    val reevaluationEpochDay: Long
)

sealed interface SmartSetupDecision {
    data class Ready(val recommendation: SmartSetupRecommendation) : SmartSetupDecision

    data class MissingData(
        val fields: Set<SmartSetupMissingField>
    ) : SmartSetupDecision {
        init {
            require(fields.isNotEmpty())
        }
    }

    data class Unsupported(
        val reason: SmartSetupUnsupportedReason,
        val facts: Set<String> = emptySet()
    ) : SmartSetupDecision
}
