package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import com.aqua.aqualight.data.aquarium.OwnerArchiveMutationGate
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterMigrationEntity
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.UserDataRestoreJournal
import com.aqua.aqualight.data.user.archive.requireNoActiveRestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Caller owns the immutable session write/transition barrier before admission. */
internal class WaterAnalysisRoomCutover(
    context: Context,
    private val legacy: DataStore<WaterAnalysesStore>,
    private val database: WaterAnalysisDatabase
) {
    private val journal = UserDataRestoreJournal(context.applicationContext)
    val sources = WaterAnalysisCutoverSourceFiles(context.applicationContext)

    suspend fun isActive(owner: String): Boolean = withContext(Dispatchers.IO) {
        database.analyses().migration(owner)?.state == WaterMigrationEntity.ACTIVE
    }

    suspend fun requiresResume(owner: String): Boolean = withContext(Dispatchers.IO) {
        val checkpoint = database.analyses().migration(owner)
        checkpoint?.state != WaterMigrationEntity.ACTIVE && (checkpoint != null || sources.hasRetained(owner))
    }

    /** A failed copy cannot authorize new legacy archive/deletion writes before it resumes. */
    suspend fun requireSettledAuthority(owner: String): Boolean {
        check(!requiresResume(owner)) { "Water history migration must resume before changing legacy history." }
        return isActive(owner)
    }

    suspend fun activate(owner: String) = OwnerArchiveMutationGate.shared.withOwner(owner) {
        check(UserDataScope.requireCurrentUid() == owner)
        if (!isActive(owner)) {
            journal.requireNoActiveRestore(owner)
            check(TankCareIntegrityJournal.pendingForOwner(owner).isEmpty()) {
                "Tank deletion must recover before water history cutover."
            }
            val legacyStore = legacy.data.first()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable + Dispatchers.IO) {
                check(database.deletions().manifestPage(owner, 0L).isEmpty()) {
                    "Water deletion staging must recover before cutover."
                }
                val source = sources.retain(owner, legacyStore)
                WaterAnalysisRoomActivation(database).activate(source)
            }
            currentCoroutineContext().ensureActive()
        }
    }
}
