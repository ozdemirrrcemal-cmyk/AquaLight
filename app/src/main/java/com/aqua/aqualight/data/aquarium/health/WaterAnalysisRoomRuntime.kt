package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.UserDataRestoreJournal
import com.aqua.aqualight.data.user.archive.requireNoActiveRestore

/** One data-layer composition for queries, writes, migration, archive and dependent deletion. */
internal class WaterAnalysisRoomRuntime(
    context: Context,
    legacy: DataStore<WaterAnalysesStore>,
    private val tanks: AquariumTankDataStoreManager
) {
    val database = WaterAnalysisDatabase.getInstance(context.applicationContext)
    val cutover = WaterAnalysisRoomCutover(context, legacy, database)
    val queries = WaterAnalysisRoomQueries(database)
    val deletion = RoomWaterAnalysisDeletionIntegrity(database)
    val archive = WaterAnalysisRoomArchiveStore(database) { owner ->
        tanks.tanksSnapshotForOwner(owner).map { it.id }.toSet()
    }
    private val journal = UserDataRestoreJournal(context.applicationContext)

    fun writer(session: OwnerSessionWriteLease) = WaterAnalysisRoomWriter(database, session, ::requireTank)

    private suspend fun requireTank(owner: String, tankId: Long) {
        check(UserDataScope.requireCurrentUid() == owner)
        journal.requireNoActiveRestore(owner)
        if (tanks.tanksSnapshotForOwner(owner).none { it.id == tankId }) {
            throw StoreInvariantViolation("Water analysis targets a missing aquarium.")
        }
    }
}
