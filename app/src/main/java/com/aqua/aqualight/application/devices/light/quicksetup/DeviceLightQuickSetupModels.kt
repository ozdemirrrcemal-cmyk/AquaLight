package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft

data class DeviceLightQuickSetupTank(
    val tankId: Long,
    val tankName: String,
    val setupDateEpochDay: Long?,
    val widthCm: Int,
    val lengthCm: Int,
    val heightCm: Int,
    val plantCount: Int,
    val inferredPlantDemand: DeviceLightPlantDemand,
    val inferredPlantDensity: DeviceLightPlantDensity,
    val inferredCo2Installed: Boolean,
    val inferredActiveSoil: Boolean,
    val plantedFreshwater: Boolean,
    val productKey: String,
    val productDisplayName: String
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
    TANK_NOT_ASSIGNED,
    TANK_NOT_FOUND,
    UNAVAILABLE
}

interface DeviceLightQuickSetupTankOperations {
    suspend fun readForDevice(deviceUid: String): DeviceLightQuickSetupTankReadResult
}

enum class DeviceLightPlantDemand {
    LOW,
    MEDIUM,
    HIGH
}

enum class DeviceLightPlantDensity {
    SPARSE,
    MEDIUM,
    DENSE
}

enum class DeviceLightPlanConfidence {
    ESTIMATED,
    CALIBRATED
}

enum class DeviceLightPlanReason {
    NEW_TANK,
    ESTABLISHED_TANK,
    LOW_LIGHT_PLANTS,
    MEDIUM_LIGHT_PLANTS,
    HIGH_LIGHT_PLANTS,
    CO2_ACTIVE,
    NO_CO2_SAFETY_CAP,
    ACTIVE_SOIL_STARTUP,
    ESTIMATED_PAR
}

enum class DeviceLightPlanWarning {
    SETUP_DATE_MISSING,
    SETUP_DATE_IN_FUTURE,
    NOT_PLANTED_FRESHWATER,
    NO_PLANTS,
    HIGH_LIGHT_WITHOUT_CO2,
    PAR_NOT_MEASURED
}

private const val STARTUP_DAY = 1
private const val STARTUP_DURATION_MINUTES = 360
private const val ESTABLISHING_DAY = 22
private const val ESTABLISHING_DURATION_MINUTES = 390
private const val ROOTING_DAY = 43
private const val ROOTING_DURATION_MINUTES = 420
private const val BALANCING_DAY = 64
private const val BALANCING_DURATION_MINUTES = 450
private const val MATURE_DAY = 85
private const val MATURE_DURATION_MINUTES = 480

enum class DeviceLightLifecycleStage(val dayStart: Int, val durationMinutes: Int) {
    STARTUP(STARTUP_DAY, STARTUP_DURATION_MINUTES),
    ESTABLISHING(ESTABLISHING_DAY, ESTABLISHING_DURATION_MINUTES),
    ROOTING(ROOTING_DAY, ROOTING_DURATION_MINUTES),
    BALANCING(BALANCING_DAY, BALANCING_DURATION_MINUTES),
    MATURE(MATURE_DAY, MATURE_DURATION_MINUTES)
}

data class DeviceLightQuickSetupInput(
    val plantDemand: DeviceLightPlantDemand,
    val plantDensity: DeviceLightPlantDensity,
    val aquariumHeightCm: Int,
    val co2Installed: Boolean,
    val activeSoil: Boolean,
    val programEndMinute: Int
)

data class DeviceLightQuickSetupPhase(
    val lifecycleStage: DeviceLightLifecycleStage,
    val targetPpfd: Int,
    val estimatedDliMolPerM2Day: Double,
    val draft: DeviceLightManagedPlanPhaseDraft
)

data class DeviceLightQuickSetupPlan(
    val initialStartPercent: Int,
    val currentPhaseIndex: Int,
    val currentTargetPpfd: Int,
    val currentEstimatedDliMolPerM2Day: Double,
    val confidence: DeviceLightPlanConfidence,
    val reasons: Set<DeviceLightPlanReason>,
    val warnings: Set<DeviceLightPlanWarning>,
    val phases: List<DeviceLightQuickSetupPhase>,
    val reevaluationEpochDay: Long,
    val profileFingerprint: String
) {
    fun toManagedPlanDraft(): DeviceLightManagedPlanDraft = DeviceLightManagedPlanDraft(
        initialStartPercent = initialStartPercent,
        phases = phases.map(DeviceLightQuickSetupPhase::draft)
    )

    val currentPhase: DeviceLightQuickSetupPhase
        get() = phases[currentPhaseIndex]

    val scene: DeviceLightAutomaticScene
        get() = currentPhase.draft.scene
}
