package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.PlantObservationInput
import com.aqua.aqualight.application.aquarium.health.PlantObservationRules
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import com.aqua.aqualight.data.store.CommercialStoreSchema

internal fun StoredPlantObservation.toInput() = PlantObservationInput(
    requestId, tankId, plantId, symptomKeysList, note, photoUrisList
)

internal fun StoredPlantObservation.toSnapshot() = PlantObservationSnapshot(
    id, tankId, plantId, symptomKeysList, note, photoUrisList, createdAtMillis
)

internal fun validatePlantHealthStore(store: PlantHealthStore): PlantHealthStore {
    CommercialStoreSchema.requireCurrent(
        "PlantHealthStore", store.schemaVersion, CommercialStoreSchema.PLANT_HEALTH_VERSION
    )
    val ids = mutableSetOf<Pair<String, Long>>()
    val requests = mutableSetOf<Pair<String, String>>()
    val photos = mutableSetOf<String>()
    store.observationsList.forEach { record ->
        require(record.ownerUid.isNotBlank() && record.ownerUid.length <= MAX_OWNER_LENGTH)
        require(record.ownerUid == record.ownerUid.trim())
        require(record.id > 0 && record.createdAtMillis > 0)
        PlantObservationRules.validate(record.toInput())
        require(record.note == record.note.trim())
        require(ids.add(record.ownerUid to record.id))
        require(requests.add(record.ownerUid to record.requestId))
        require(record.photoUrisList.all { photos.add(it) })
    }
    return store
}

private const val MAX_OWNER_LENGTH = 128
