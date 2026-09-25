package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage

internal class TankPlantPhotoMutations(
    private val context: Context,
    private val mutateTank: suspend (Long, (StoredTank) -> StoredTank) -> Unit
) {
    /** Returns the superseded plant URI only after the owner-scoped store commit succeeds. */
    suspend fun updatePhoto(
        tankId: Long,
        plantId: Long,
        photoUri: String?
    ): String? {
        require(plantId > 0L) { "plantId must be positive" }
        val normalizedPhotoUri = photoUri.orEmpty().trim()
        val ownerUid = UserDataScope.requireCurrentUid()
        val candidateOwner = AppMediaStorage.pendingMediaOwner(context, normalizedPhotoUri, AppMediaScope.PLANT)
        val validCandidate = normalizedPhotoUri.isBlank() || candidateOwner == ownerUid
        var previousPhotoUri: String? = null
        mutateTank(tankId) { storedTank ->
            var replaced = false
            val updatedPlants = storedTank.plantsList.map { plant ->
                if (plant.id == plantId) {
                    require(storedTank.ownerUid == ownerUid) { "Plant photo owner changed." }
                    require(plant.photoUri == normalizedPhotoUri || validCandidate) {
                        "New plant photo is not pending media for this owner."
                    }
                    replaced = true
                    previousPhotoUri = plant.photoUri.takeIf { uri ->
                        uri.isNotBlank() && uri != normalizedPhotoUri
                    }
                    plant.toBuilder()
                        .setPhotoUri(normalizedPhotoUri)
                        .build()
                } else {
                    plant
                }
            }
            require(replaced) { "Plant not found in the selected tank." }
            storedTank.toBuilder()
                .clearPlants()
                .addAllPlants(updatedPlants)
                .build()
        }
        return previousPhotoUri
    }

}

internal fun requireNewPlantPhoto(context: Context, photoUri: String?, ownerUid: String) {
    if (photoUri.isNullOrBlank()) return
    require(AppMediaStorage.pendingMediaOwner(context, photoUri, AppMediaScope.PLANT) == ownerUid) {
        "New plant photos must be pending app-owned plant media for the record owner."
    }
}
