package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage

/** Independent copies prepared before the retryable DataStore transaction. */
internal class TankDuplicateMedia(
    private val context: Context,
    private val ownerUid: String,
    private val tankId: Long,
    source: StoredTank
) {
    private val sourcePhotoAtPreparation = source.photoUri
    private val sourcePlantPhotosAtPreparation = source.plantsList.associate { it.id to it.photoUri }
    private val sourceLivestockPhotos = source.livestockList.associate { it.id to it.photoUri }
    private val livestockPhotos = linkedMapOf<Long, String?>()
    private var tankPhoto: String? = null
    private val plantPhotos = linkedMapOf<Long, String?>()

    init {
        runCatching {
            tankPhoto = copy(source.photoUri, AppMediaScope.TANK)
            source.plantsList.forEach { plant ->
                plantPhotos[plant.id] = copy(plant.photoUri, AppMediaScope.PLANT)
            }
            source.livestockList.forEach { item ->
                livestockPhotos[item.id] = copy(item.photoUri, AppMediaScope.LIVESTOCK)
            }
        }.onFailure { rollback() }.getOrThrow()
    }

    fun applyTo(source: StoredTank): StoredTank.Builder {
        check(source.photoUri == sourcePhotoAtPreparation) {
            "Tank photo changed while duplication was being prepared."
        }
        check(source.plantsList.associate { it.id to it.photoUri } == sourcePlantPhotosAtPreparation) {
            "Plant photos changed while duplication was being prepared."
        }
        check(source.livestockList.associate { it.id to it.photoUri } == sourceLivestockPhotos) {
            "Livestock photos changed while duplication was being prepared."
        }
        val livestock = source.livestockList.map { item ->
            item.toBuilder().setPhotoUri(livestockPhotos[item.id].orEmpty()).build()
        }
        val plants = source.plantsList.map { plant ->
            plant.toBuilder().setPhotoUri(plantPhotos[plant.id].orEmpty().trim()).build()
        }
        return source.toBuilder().setPhotoUri(tankPhoto.orEmpty().trim())
            .clearPlants().addAllPlants(plants).clearLivestock().addAllLivestock(livestock)
    }

    fun rollback() {
        (plantPhotos.values + livestockPhotos.values + tankPhoto).forEach { uri ->
            runCatching { AppMediaStorage.rollbackPendingMedia(context, uri) }
        }
    }

    private fun copy(uri: String?, scope: AppMediaScope): String? {
        val copy = AppMediaStorage.copyInternalMedia(context, uri, scope, tankId.toString(), ownerUid)
        check(!AppMediaStorage.isAppOwned(context, uri) || !copy.isNullOrBlank()) {
            "Photo could not be copied with independent ownership."
        }
        return copy
    }
}
