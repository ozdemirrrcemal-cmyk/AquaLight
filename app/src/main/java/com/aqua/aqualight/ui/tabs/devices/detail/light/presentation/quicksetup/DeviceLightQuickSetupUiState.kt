@file:Suppress("CyclomaticComplexMethod", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupApplyResult
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupInput
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal data class DeviceLightQuickSetupUiState(
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val snapshot: SmartSetupSnapshot? = null,
    val editor: DeviceLightQuickSetupEditor = DeviceLightQuickSetupEditor(),
    val editMode: Boolean = false,
    val draftDirty: Boolean = false,
    val initialLoading: Boolean = false,
    val operationInProgress: Boolean = false,
    val loadFailure: DeviceLightQuickSetupLoadFailure? = null,
    val appliedResult: SmartSetupApplyResult.Applied? = null
) {
    val decision: SmartSetupDecision?
        get() = snapshot?.decision

    val isPlanted: Boolean
        get() = snapshot?.input?.isPlanted == true

    val canSave: Boolean
        get() = snapshot != null &&
            draftDirty &&
            !operationInProgress &&
            editor.setupDateEpochDay != null &&
            editor.toLightingProfileOrNull(isPlanted) != null

    val canApply: Boolean
        get() = decision is SmartSetupDecision.Ready &&
            !draftDirty &&
            !editMode &&
            !operationInProgress &&
            !planInstalled

    val planInstalled: Boolean
        get() = snapshot?.installedPlanFingerprintMatches == true || appliedResult != null
}

internal enum class DeviceLightQuickSetupLoadFailure {
    DEVICE_NOT_ASSIGNED,
    AQUARIUM_NOT_FOUND,
    NOT_CONNECTED,
    INVALID_DEVICE,
    INVALID_FIRMWARE_DATA,
    UNAVAILABLE
}

internal data class DeviceLightQuickSetupEditor(
    val setupDateEpochDay: Long? = null,
    val plantDensity: PlantDensity? = null,
    val highestPlantLightDemand: PlantLightDemand? = null,
    val co2Status: Co2Status? = null,
    val isActiveSoil: Boolean? = null,
    val waterDepthCm: Int? = null,
    val fixtureMountHeightCm: Int? = null,
    val preferredViewingStartMinuteOfDay: Int? = null,
    val preferredViewingEndMinuteOfDay: Int? = null,
    val algaeObservation: AquariumObservationSeverity? = null,
    val plantStressObservation: AquariumObservationSeverity? = null,
    val observationDateEpochDay: Long? = null
) {
    fun aquariumAgeDays(evaluationEpochDay: Long?): Int? {
        val setup = setupDateEpochDay ?: return null
        val evaluation = evaluationEpochDay ?: return null
        if (setup > evaluation) return null
        return (evaluation - setup + 1L).takeIf { value -> value <= Int.MAX_VALUE }?.toInt()
    }

    fun toLightingProfileOrNull(isPlanted: Boolean): AquariumLightingProfile? {
        if (isPlanted && (plantDensity == null || highestPlantLightDemand == null)) return null
        if (co2Status == null || isActiveSoil == null) return null
        if (waterDepthCm == null || fixtureMountHeightCm == null) return null
        val viewingStart = preferredViewingStartMinuteOfDay ?: return null
        val viewingEnd = preferredViewingEndMinuteOfDay ?: return null
        if (viewingEnd <= viewingStart || viewingEnd - viewingStart < 60) return null
        val algae = algaeObservation ?: return null
        val plantStress = plantStressObservation ?: return null
        val observationDate = observationDateEpochDay ?: return null
        return runCatching {
            AquariumLightingProfile(
                plantDensity = plantDensity.takeIf { isPlanted },
                highestPlantLightDemand = highestPlantLightDemand.takeIf { isPlanted },
                co2Status = co2Status,
                isActiveSoil = isActiveSoil,
                waterDepthCm = waterDepthCm,
                fixtureMountHeightCm = fixtureMountHeightCm,
                preferredViewingStartMinuteOfDay = viewingStart,
                preferredViewingEndMinuteOfDay = viewingEnd,
                algaeObservation = algae,
                plantStressObservation = plantStress,
                observationDateEpochDay = observationDate
            )
        }.getOrNull()
    }

    companion object {
        fun from(input: SmartSetupInput): DeviceLightQuickSetupEditor =
            DeviceLightQuickSetupEditor(
                setupDateEpochDay = input.setupDateEpochDay,
                plantDensity = input.plantDensity,
                highestPlantLightDemand = input.highestPlantLightDemand,
                co2Status = input.co2Status,
                isActiveSoil = input.isActiveSoil,
                waterDepthCm = input.waterDepthCm,
                fixtureMountHeightCm = input.fixtureMountHeightCm,
                preferredViewingStartMinuteOfDay = input.preferredViewingStartMinuteOfDay,
                preferredViewingEndMinuteOfDay = input.preferredViewingEndMinuteOfDay,
                algaeObservation = input.algaeObservation,
                plantStressObservation = input.plantStressObservation,
                observationDateEpochDay = input.observationDateEpochDay
            )
    }
}

internal data class DeviceLightQuickSetupActions(
    val onEditClick: () -> Unit,
    val onCancelEditClick: () -> Unit,
    val onAquariumAgeSelected: (Int) -> Unit,
    val onAquariumAgeAdjusted: (Int) -> Unit,
    val onPlantDensitySelected: (PlantDensity) -> Unit,
    val onPlantLightDemandSelected: (PlantLightDemand) -> Unit,
    val onCo2StatusSelected: (Co2Status) -> Unit,
    val onActiveSoilSelected: (Boolean) -> Unit,
    val onWaterDepthSelected: (Int) -> Unit,
    val onWaterDepthAdjusted: (Int) -> Unit,
    val onMountHeightSelected: (Int) -> Unit,
    val onMountHeightAdjusted: (Int) -> Unit,
    val onViewingWindowSelected: (Int, Int) -> Unit,
    val onViewingStartAdjusted: (Int) -> Unit,
    val onViewingEndAdjusted: (Int) -> Unit,
    val onAlgaeObservationSelected: (AquariumObservationSeverity) -> Unit,
    val onPlantStressObservationSelected: (AquariumObservationSeverity) -> Unit,
    val onRecordObservationsToday: () -> Unit,
    val onSaveProfileClick: () -> Unit,
    val onApplyClick: () -> Unit,
    val onRetryClick: () -> Unit
)
