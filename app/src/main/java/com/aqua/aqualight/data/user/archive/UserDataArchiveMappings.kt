package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.application.aquarium.lighting.AquariumLightingProfile
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType

internal fun SavedAquariumTank.toArchiveAquarium(
    photoReference: ArchiveMediaReference?
): ArchiveAquarium {
    return ArchiveAquarium(
        id = id,
        name = name,
        description = description,
        photo = photoReference,
        setupDateEpochDay = setupDateEpochDay,
        widthCm = widthCm,
        lengthCm = lengthCm,
        heightCm = heightCm,
        sizeUnit = sizeUnit,
        volumeUnit = volumeUnit,
        tankType = tankType,
        tankStyle = tankStyle,
        smartLightProfile = lightingProfile.toArchiveSmartLightProfile(),
        createdAtMillis = createdAtMillis,
        smartCareEnabled = smartCareEnabled,
        careRemindersEnabled = careRemindersEnabled,
        plants = plants.map { plant ->
            ArchivePlant(
                id = plant.id,
                plantName = plant.plantName,
                category = plant.category,
                markerX = plant.markerX,
                markerY = plant.markerY
            )
        },
        materials = materials.map { material ->
            ArchiveMaterial(
                id = material.id,
                productId = material.productId,
                categoryKey = material.categoryKey,
                categoryTitle = material.categoryTitle,
                name = material.name,
                brand = material.brand,
                note = material.note
            )
        },
        livestock = livestock.map { item ->
            ArchiveLivestock(
                id = item.id,
                name = item.name,
                category = item.category,
                quantity = item.quantity,
                addedDateEpochDay = item.addedDateEpochDay,
                note = item.note
            )
        }
    )
}

internal fun ArchiveAquarium.toTankDraft(photoUri: String?): TankDraft {
    return TankDraft(
        name = name,
        description = description,
        photoUri = photoUri,
        plants = plants.map { plant ->
            TankPlantTag(
                id = plant.id,
                plantName = plant.plantName,
                category = plant.category,
                markerX = plant.markerX,
                markerY = plant.markerY
            )
        },
        materials = materials.map { material ->
            TankMaterialSelection(
                id = material.id,
                productId = material.productId,
                categoryKey = material.categoryKey,
                categoryTitle = material.categoryTitle,
                name = material.name,
                brand = material.brand,
                note = material.note
            )
        },
        setupDateEpochDay = setupDateEpochDay,
        widthCm = widthCm,
        lengthCm = lengthCm,
        heightCm = heightCm,
        sizeUnit = sizeUnit,
        volumeUnit = volumeUnit,
        tankType = tankType,
        tankStyle = tankStyle,
        lightingProfile = smartLightProfile.toAquariumLightingProfile()
    )
}

private fun AquariumLightingProfile.toArchiveSmartLightProfile() = ArchiveSmartLightProfile(
    plantDensity = plantDensity?.name.orEmpty(),
    highestPlantLightDemand = highestPlantLightDemand?.name.orEmpty(),
    co2Status = co2Status?.name.orEmpty(),
    isActiveSoil = isActiveSoil,
    waterDepthCm = waterDepthCm,
    fixtureMountHeightCm = fixtureMountHeightCm,
    preferredViewingStartMinuteOfDay = preferredViewingStartMinuteOfDay,
    preferredViewingEndMinuteOfDay = preferredViewingEndMinuteOfDay,
    algaeObservation = algaeObservation?.name.orEmpty(),
    plantStressObservation = plantStressObservation?.name.orEmpty(),
    observationDateEpochDay = observationDateEpochDay
)

internal fun ArchiveSmartLightProfile.toAquariumLightingProfile() = AquariumLightingProfile(
    plantDensity = plantDensity.toArchiveEnumOrNull(PlantDensity.entries),
    highestPlantLightDemand = highestPlantLightDemand.toArchiveEnumOrNull(
        PlantLightDemand.entries
    ),
    co2Status = co2Status.toArchiveEnumOrNull(Co2Status.entries),
    isActiveSoil = isActiveSoil,
    waterDepthCm = waterDepthCm,
    fixtureMountHeightCm = fixtureMountHeightCm,
    preferredViewingStartMinuteOfDay = preferredViewingStartMinuteOfDay,
    preferredViewingEndMinuteOfDay = preferredViewingEndMinuteOfDay,
    algaeObservation = algaeObservation.toArchiveEnumOrNull(
        AquariumObservationSeverity.entries
    ),
    plantStressObservation = plantStressObservation.toArchiveEnumOrNull(
        AquariumObservationSeverity.entries
    ),
    observationDateEpochDay = observationDateEpochDay
)

private fun <T : Enum<T>> String.toArchiveEnumOrNull(entries: Iterable<T>): T? =
    takeIf(String::isNotBlank)?.let { value ->
        entries.singleOrNull { entry -> entry.name == value }
            ?: throw IllegalArgumentException(
                "Unsupported Smart Light semantic value '$value' in backup."
            )
    }

internal fun ArchiveLivestock.toSavedLivestock(): SavedAquariumLivestock {
    return SavedAquariumLivestock(
        id = id,
        name = name,
        category = category,
        quantity = quantity,
        addedDateEpochDay = addedDateEpochDay,
        note = note
    )
}

internal fun CareTask.toArchiveCareTask(): ArchiveCareTask {
    return ArchiveCareTask(
        id = id,
        tankId = tankId,
        title = title,
        description = description,
        type = type.name,
        source = source.name,
        status = status.name,
        dueAtMillis = dueAtMillis,
        completedAtMillis = completedAtMillis,
        repeatEnabled = repeatEnabled,
        repeatIntervalDays = repeatIntervalDays,
        reminderEnabled = reminderEnabled,
        missedReminderEnabled = missedReminderEnabled,
        missedReminderDays = missedReminderDays,
        waterChangePercent = waterChangePercent,
        note = note,
        generatedRuleKey = generatedRuleKey,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

internal fun ArchiveCareTask.toCareTask(
    ownerUid: String,
    restoredTankId: Long,
    restoredTaskId: Long
): CareTask {
    return CareTask(
        id = restoredTaskId,
        ownerUid = ownerUid,
        tankId = restoredTankId,
        title = title,
        description = description,
        type = CareTaskType.valueOf(type),
        source = CareTaskSource.valueOf(source),
        status = CareTaskStatus.valueOf(status),
        dueAtMillis = dueAtMillis,
        completedAtMillis = completedAtMillis,
        repeatEnabled = repeatEnabled,
        repeatIntervalDays = repeatIntervalDays,
        reminderEnabled = reminderEnabled,
        missedReminderEnabled = missedReminderEnabled,
        missedReminderDays = missedReminderDays,
        waterChangePercent = waterChangePercent,
        note = note,
        generatedRuleKey = remapGeneratedRuleKey(restoredTankId),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

private fun ArchiveCareTask.remapGeneratedRuleKey(restoredTankId: Long): String {
    if (generatedRuleKey.isBlank() || source != CareTaskSource.AUTOMATIC.name) {
        return generatedRuleKey
    }
    val oldPrefix = "smart_${tankId}_"
    return if (generatedRuleKey.startsWith(oldPrefix)) {
        "smart_${restoredTankId}_" + generatedRuleKey.removePrefix(oldPrefix)
    } else {
        generatedRuleKey
    }
}

internal fun TankDeviceAssignment.toArchiveAssignment(): ArchiveDeviceAssignment {
    return ArchiveDeviceAssignment(
        tankId = tankId,
        deviceUid = deviceUid.value,
        assignedAtMillis = assignedAtMillis
    )
}
