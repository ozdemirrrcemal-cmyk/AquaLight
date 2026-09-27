package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage

/** Uses the existing pending/committed media journal; health records never adopt foreign media. */
internal class HealthObservationMedia(context: Context) {
    private val appContext = context.applicationContext

    fun requireCandidate(owner: String, uri: String?) {
        require(uri == null || AppMediaStorage.pendingMediaOwner(appContext, uri, AppMediaScope.HEALTH) == owner) {
            "Health photos must be pending candidates owned by the current session."
        }
    }

    fun committed(uri: String?) = AppMediaStorage.commitPendingMedia(appContext, uri)

    fun deleted(owner: String, uri: String?) {
        AppMediaStorage.deleteAfterCommit(appContext, owner, uri)
    }
}

/** Includes deletion staging: rollback still owns these images until the tank transaction finishes. */
internal fun healthObservationPhotoReferences(context: Context, owner: String): Set<String> {
    val database = HealthObservationDatabase.getInstance(context.applicationContext)
    val photos = mutableSetOf<String>()
    database.runInTransaction {
        var after = 0L
        var page = database.observations().ownerPage(owner, after)
        while (page.isNotEmpty()) {
            page.forEach { row -> row.toStored().input.photoUri.takeIf(String::isNotBlank)?.let(photos::add) }
            after = page.last().observationId
            page = database.observations().ownerPage(owner, after)
        }
    }
    return photos
}
