package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage

internal data class LivestockPhotoCommit(val previousPhotoUri: String?, val photoUri: String?)

/** Only stable owner/tank/record identity can select a livestock record; species names never do. */
internal class TankLivestockMutations(
    private val context: Context,
    private val mutateTank: suspend (Long, (StoredTank) -> StoredTank) -> Unit,
    private val serialize: (SavedAquariumLivestock) -> StoredLivestock
) {
    suspend fun save(
        tankId: Long,
        livestock: SavedAquariumLivestock,
        isNew: Boolean,
        photoChanged: Boolean
    ): LivestockPhotoCommit {
        val owner = UserDataScope.requireCurrentUid()
        val candidate = livestock.photoUri.orEmpty().trim()
        val validCandidate = candidate.isBlank() ||
            AppMediaStorage.pendingMediaOwner(context, candidate, AppMediaScope.LIVESTOCK) == owner
        var result = LivestockPhotoCommit(null, null)
        mutateTank(tankId) { tank ->
            require(tank.ownerUid == owner) { "Livestock owner changed." }
            val existing = tank.livestockList.firstOrNull { it.id == livestock.id }
            require((existing == null) == isNew) { "Livestock record is missing or already exists." }
            val photo = if (isNew || photoChanged) candidate else existing?.photoUri.orEmpty()
            require(photo == existing?.photoUri || photo.isBlank() || validCandidate) {
                "New livestock photo must be pending media for this owner."
            }
            val replacement = serialize(livestock).toBuilder().setPhotoUri(photo).build()
            result = LivestockPhotoCommit(
                existing?.photoUri?.takeIf { it.isNotBlank() && it != photo },
                photo.takeIf(String::isNotBlank)
            )
            val items = if (isNew) tank.livestockList + replacement else tank.livestockList.map {
                if (it.id == livestock.id) replacement else it
            }
            tank.toBuilder().clearLivestock().addAllLivestock(items).build()
        }
        return result
    }

    suspend fun remove(tankId: Long, livestockId: Long): String? {
        require(livestockId > 0L) { "livestockId must be positive" }
        var previous: String? = null
        mutateTank(tankId) { tank ->
            val item = requireNotNull(tank.livestockList.firstOrNull { it.id == livestockId }) {
                "Livestock record not found in the selected tank."
            }
            previous = item.photoUri.takeIf(String::isNotBlank)
            tank.toBuilder().clearLivestock()
                .addAllLivestock(tank.livestockList.filterNot { it.id == livestockId }).build()
        }
        return previous
    }
}
