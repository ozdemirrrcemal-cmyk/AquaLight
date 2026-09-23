package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestRecord
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservation
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservationInput

internal data class HealthRecordWriteMetadata(
    val id: Long,
    val ownerUid: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

internal object AquariumHealthRecordMapper {

    fun waterTestToStored(
        input: AquariumWaterTestInput,
        metadata: HealthRecordWriteMetadata
    ): StoredAquariumWaterTest =
        StoredAquariumWaterTest.newBuilder()
            .setId(metadata.id)
            .setOwnerUid(metadata.ownerUid)
            .setTankId(input.tankId)
            .setMeasuredAtMillis(input.measuredAtMillis)
            .addAllReadings(
                input.readings.map { reading ->
                    StoredAquariumWaterReading.newBuilder()
                        .setParameter(reading.parameter.name)
                        .setValue(reading.value)
                        .build()
                }
            )
            .setNote(input.note)
            .setCreatedAtMillis(metadata.createdAtMillis)
            .setUpdatedAtMillis(metadata.updatedAtMillis)
            .build()
            .also(AquariumHealthStoredRecordRules::validateWaterTest)

    fun waterTestToApplication(
        stored: StoredAquariumWaterTest
    ): AquariumWaterTestRecord =
        AquariumWaterTestRecord(
            id = stored.id,
            tankId = stored.tankId,
            measuredAtMillis = stored.measuredAtMillis,
            readings = stored.readingsList.map { reading ->
                AquariumWaterReading(
                    parameter = HealthWaterParameter.valueOf(reading.parameter),
                    value = reading.value
                )
            },
            note = stored.note,
            createdAtMillis = stored.createdAtMillis,
            updatedAtMillis = stored.updatedAtMillis
        )

    fun livestockObservationToStored(
        input: LivestockHealthObservationInput,
        metadata: HealthRecordWriteMetadata
    ): StoredLivestockHealthObservation =
        StoredLivestockHealthObservation.newBuilder()
            .setId(metadata.id)
            .setOwnerUid(metadata.ownerUid)
            .setTankId(input.tankId)
            .setLivestockId(input.livestockId ?: 0L)
            .setCategoryKey(input.categoryKey)
            .setSymptomKey(input.symptomKey)
            .setIntensity(input.intensity.name)
            .setObservedAtMillis(input.observedAtMillis)
            .setNote(input.note)
            .setCreatedAtMillis(metadata.createdAtMillis)
            .setUpdatedAtMillis(metadata.updatedAtMillis)
            .build()
            .also(AquariumHealthStoredRecordRules::validateLivestockObservation)

    fun livestockObservationToApplication(
        stored: StoredLivestockHealthObservation
    ): LivestockHealthObservation =
        LivestockHealthObservation(
            id = stored.id,
            tankId = stored.tankId,
            livestockId = stored.livestockId.takeIf { id -> id > 0L },
            categoryKey = stored.categoryKey,
            symptomKey = stored.symptomKey,
            intensity = ObservationIntensity.valueOf(stored.intensity),
            observedAtMillis = stored.observedAtMillis,
            note = stored.note,
            createdAtMillis = stored.createdAtMillis,
            updatedAtMillis = stored.updatedAtMillis
        )

    fun plantObservationToStored(
        input: PlantHealthObservationInput,
        metadata: HealthRecordWriteMetadata
    ): StoredPlantHealthObservation =
        StoredPlantHealthObservation.newBuilder()
            .setId(metadata.id)
            .setOwnerUid(metadata.ownerUid)
            .setTankId(input.tankId)
            .setPlantId(input.plantId ?: 0L)
            .setSymptomKey(input.symptomKey)
            .setAlgaeTypeKey(input.algaeTypeKey.orEmpty())
            .setIntensity(input.intensity.name)
            .setObservedAtMillis(input.observedAtMillis)
            .setNote(input.note)
            .setCreatedAtMillis(metadata.createdAtMillis)
            .setUpdatedAtMillis(metadata.updatedAtMillis)
            .build()
            .also(AquariumHealthStoredRecordRules::validatePlantObservation)

    fun plantObservationToApplication(
        stored: StoredPlantHealthObservation
    ): PlantHealthObservation =
        PlantHealthObservation(
            id = stored.id,
            tankId = stored.tankId,
            plantId = stored.plantId.takeIf { id -> id > 0L },
            symptomKey = stored.symptomKey,
            algaeTypeKey = stored.algaeTypeKey.takeIf(String::isNotEmpty),
            intensity = ObservationIntensity.valueOf(stored.intensity),
            observedAtMillis = stored.observedAtMillis,
            note = stored.note,
            createdAtMillis = stored.createdAtMillis,
            updatedAtMillis = stored.updatedAtMillis
        )
}
