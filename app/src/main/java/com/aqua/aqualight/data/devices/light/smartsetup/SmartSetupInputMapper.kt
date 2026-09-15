package com.aqua.aqualight.data.devices.light.smartsetup

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupAquariumEnvironment
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupCalibrationProfile
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupInput
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupLifecycleClassifier
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupMaintenanceObservations
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import java.time.Instant
import java.time.ZoneId

/** Device-authoritative facts consumed by the shared Smart Setup input mapper. */
internal data class SmartSetupDeviceFacts(
    val evaluationEpochDay: Long?,
    val productKey: String,
    val calibrationRevision: Int?,
    val channelSceneKeys: List<String>,
    val calibrationProfile: SmartSetupCalibrationProfile?
)

/** Maps explicit aquarium, maintenance and device facts without guessing from display text. */
internal fun AquariumTankSnapshot.toSmartSetupInput(
    deviceFacts: SmartSetupDeviceFacts,
    tasks: List<CareTask>,
    zone: ZoneId,
    nowMillis: Long
): SmartSetupInput {
    val lifecycle = if (setupDateEpochDay != null && deviceFacts.evaluationEpochDay != null) {
        SmartSetupLifecycleClassifier.classify(
            setupDateEpochDay,
            deviceFacts.evaluationEpochDay
        )
    } else {
        null
    }
    return SmartSetupInput(
        tankId = id,
        tankName = name,
        aquariumEnvironment = tankType.toSmartSetupEnvironment(),
        evaluationEpochDay = deviceFacts.evaluationEpochDay,
        setupDateEpochDay = setupDateEpochDay,
        setupDay = lifecycle?.setupDay,
        lifecycleStage = lifecycle?.stage,
        isPlanted = tankType == AquariumTankTaxonomy.TYPE_PLANTED || plants.isNotEmpty(),
        plantDensity = lightingProfile.plantDensity,
        highestPlantLightDemand = lightingProfile.highestPlantLightDemand,
        co2Status = lightingProfile.co2Status,
        isActiveSoil = lightingProfile.isActiveSoil,
        waterDepthCm = lightingProfile.waterDepthCm,
        fixtureMountHeightCm = lightingProfile.fixtureMountHeightCm,
        deviceProductKey = deviceFacts.productKey,
        reportedCalibrationRevision = deviceFacts.calibrationRevision,
        reportedChannelSceneKeys = deviceFacts.channelSceneKeys,
        calibrationProfile = deviceFacts.calibrationProfile,
        preferredViewingStartMinuteOfDay =
            lightingProfile.preferredViewingStartMinuteOfDay,
        preferredViewingEndMinuteOfDay = lightingProfile.preferredViewingEndMinuteOfDay,
        algaeObservation = lightingProfile.algaeObservation,
        plantStressObservation = lightingProfile.plantStressObservation,
        observationDateEpochDay = lightingProfile.observationDateEpochDay,
        maintenance = tasks.toMaintenanceObservations(zone, nowMillis)
    )
}

private fun String.toSmartSetupEnvironment(): SmartSetupAquariumEnvironment = when (this) {
    AquariumTankTaxonomy.TYPE_FISH,
    AquariumTankTaxonomy.TYPE_SHRIMP,
    AquariumTankTaxonomy.TYPE_PLANTED -> SmartSetupAquariumEnvironment.FRESHWATER
    AquariumTankTaxonomy.TYPE_MARINE,
    AquariumTankTaxonomy.TYPE_SOFTIES,
    AquariumTankTaxonomy.TYPE_MIXED_REEF,
    AquariumTankTaxonomy.TYPE_SPS,
    AquariumTankTaxonomy.TYPE_CORAL -> SmartSetupAquariumEnvironment.MARINE
    AquariumTankTaxonomy.TYPE_OTHER -> SmartSetupAquariumEnvironment.UNKNOWN
    else -> SmartSetupAquariumEnvironment.UNKNOWN
}

private fun List<CareTask>.toMaintenanceObservations(
    zone: ZoneId,
    nowMillis: Long
): SmartSetupMaintenanceObservations {
    val completed = filter { task ->
        task.status == CareTaskStatus.COMPLETED &&
            task.completedAtMillis != null &&
            task.type in RELEVANT_CARE_TASKS
    }
    fun latest(type: CareTaskType): Long? = completed
        .asSequence()
        .filter { task -> task.type == type }
        .mapNotNull(CareTask::completedAtMillis)
        .maxOrNull()
        ?.let { millis -> Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay() }
    val overdue = count { task ->
        task.type in RELEVANT_CARE_TASKS &&
            task.status == CareTaskStatus.PENDING &&
            task.dueAtMillis <= nowMillis
    }
    return SmartSetupMaintenanceObservations(
        latestWaterChangeEpochDay = latest(CareTaskType.WATER_CHANGE),
        latestAlgaeCleaningEpochDay = latest(CareTaskType.ALGAE_CLEANING),
        latestPlantHealthCheckEpochDay = latest(CareTaskType.PLANT_HEALTH_CHECK),
        latestCo2CheckEpochDay = latest(CareTaskType.CO2_CHECK),
        overdueRelevantTaskCount = overdue
    )
}

private val RELEVANT_CARE_TASKS = setOf(
    CareTaskType.WATER_CHANGE,
    CareTaskType.ALGAE_CLEANING,
    CareTaskType.PLANT_HEALTH_CHECK,
    CareTaskType.CO2_CHECK,
    CareTaskType.LIGHT_CHECK
)
