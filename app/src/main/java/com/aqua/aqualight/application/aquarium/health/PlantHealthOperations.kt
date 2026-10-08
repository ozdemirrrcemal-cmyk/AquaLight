package com.aqua.aqualight.application.aquarium.health

import kotlinx.coroutines.flow.Flow

data class PlantObservationInput(
    val requestId: String,
    val tankId: Long,
    val plantId: Long,
    val symptomKeys: List<String>,
    val note: String,
    val photoUris: List<String>
)

data class PlantObservationSnapshot(
    val id: Long,
    val tankId: Long,
    val plantId: Long,
    val symptomKeys: List<String>,
    val note: String,
    val photoUris: List<String>,
    val createdAtMillis: Long
)

/** Visible signs reported by the user; these are not analysis or diagnostic results. */
object PlantObservationRules {
    const val HEALTHY = "healthy"
    const val ALGAE = "algae"
    const val OTHER = "other"
    const val MAX_PHOTOS = 3
    const val MAX_NOTE_LENGTH = 4000
    private const val MAX_REQUEST_LENGTH = 64
    private const val MAX_URI_LENGTH = 2048
    val symptomKeys = setOf(
        HEALTHY, "yellowing", "melting", "damage", "slow_growth", "brown_spots",
        ALGAE, "deformation", OTHER
    )

    fun toggle(selected: Set<String>, key: String): Set<String> {
        require(key in symptomKeys)
        if (key in selected) return selected - key
        return if (key == HEALTHY) setOf(HEALTHY) else (selected - HEALTHY) + key
    }

    fun canSave(keys: Collection<String>, note: String): Boolean =
        knownUniqueSigns(keys) && consistentHealth(keys) &&
            note.length <= MAX_NOTE_LENGTH && hasExplanation(keys, note)

    private fun knownUniqueSigns(keys: Collection<String>) =
        keys.isNotEmpty() && keys.all { it in symptomKeys } && keys.distinct().size == keys.size

    private fun consistentHealth(keys: Collection<String>) = HEALTHY !in keys || keys.size == 1

    private fun hasExplanation(keys: Collection<String>, note: String) = OTHER !in keys || note.isNotBlank()

    fun validate(input: PlantObservationInput) {
        require(input.requestId.isNotBlank() && input.requestId.length <= MAX_REQUEST_LENGTH)
        require(input.tankId > 0 && input.plantId > 0)
        require(canSave(input.symptomKeys, input.note))
        require(input.photoUris.size <= MAX_PHOTOS)
        require(input.photoUris.distinct().size == input.photoUris.size)
        require(input.photoUris.all { it.isNotBlank() && it.length <= MAX_URI_LENGTH })
    }
}

interface PlantHealthOperations {
    fun observationsForPlant(tankId: Long, plantId: Long): Flow<List<PlantObservationSnapshot>>
    suspend fun createObservation(input: PlantObservationInput): Long
    suspend fun containsRequest(requestId: String): Boolean
    suspend fun deleteObservation(tankId: Long, plantId: Long, observationId: Long)
    suspend fun discardDraftPhotos(photoUris: List<String>)
}
