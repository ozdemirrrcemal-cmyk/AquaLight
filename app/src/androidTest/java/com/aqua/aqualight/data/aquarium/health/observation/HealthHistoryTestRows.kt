package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun healthHistoryTestRows(context: Context, owner: String): List<StoredHealthObservation> =
    withContext(Dispatchers.IO) {
        val dao = HealthObservationDatabase.getInstance(context).observations()
        buildList {
            var after = 0L
            var page = dao.ownerPage(owner, after)
            while (page.isNotEmpty()) {
                addAll(page.filter { it.deleteState != 2 }.map { it.toStored() })
                after = page.last().observationId
                page = dao.ownerPage(owner, after)
            }
        }
    }

internal suspend fun seedHealthHistory(context: Context, owner: String, tank: Long) = withContext(Dispatchers.IO) {
    HealthObservationRoomCommit(HealthObservationDatabase.getInstance(context))
        .create(owner, HealthRoomFixture.prepared(tank = tank), HealthRoomFixture.TIME) { }
}
