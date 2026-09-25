package com.aqua.aqualight.application.aquarium

/** Saves form fields and the optional photo edit in one owner-scoped record transaction. */
interface AquariumLivestockPhotoOperations {
    suspend fun removeLivestockWithPhoto(tankId: Long, livestockId: Long, expectedOwnerUid: String)

    suspend fun saveLivestockWithPhoto(
        tankId: Long,
        livestock: AquariumLivestock,
        expectedOwnerUid: String,
        isNew: Boolean,
        photoChanged: Boolean
    )
}
