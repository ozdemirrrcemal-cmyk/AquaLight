package com.aqua.aqualight.application.aquarium

/** Owner-bound attachment of a photo to one tank plant record. */
interface AquariumPlantPhotoOperations {
    suspend fun updatePlantPhoto(
        tankId: Long,
        plantId: Long,
        photoUri: String?,
        expectedOwnerUid: String
    )
}
