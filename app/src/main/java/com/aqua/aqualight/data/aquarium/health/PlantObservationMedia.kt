package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.platform.media.CommittedMediaDeletionMode

internal interface PlantObservationMedia {
    fun requirePendingOwner(uri: String, ownerUid: String)
    fun commit(uri: String)
    fun prepareDeletion(uri: String, ownerUid: String)
    fun delete(uri: String, ownerUid: String)
    fun rollbackDraft(uri: String, ownerUid: String)
}

internal class AppPlantObservationMedia(context: Context) : PlantObservationMedia {
    private val appContext = context.applicationContext

    override fun requirePendingOwner(uri: String, ownerUid: String) {
        require(AppMediaStorage.pendingMediaOwner(appContext, uri, AppMediaScope.PLANT) == ownerUid) {
            "Observation photos must be pending plant media owned by the active owner."
        }
    }

    override fun commit(uri: String) = AppMediaStorage.commitPendingMedia(appContext, uri)

    override fun prepareDeletion(uri: String, ownerUid: String) {
        AppMediaStorage.deleteAfterCommit(appContext, ownerUid, uri, CommittedMediaDeletionMode.PREPARE)
    }

    override fun delete(uri: String, ownerUid: String) {
        AppMediaStorage.deleteAfterCommit(appContext, ownerUid, uri)
    }

    override fun rollbackDraft(uri: String, ownerUid: String) {
        if (AppMediaStorage.pendingMediaOwner(appContext, uri, AppMediaScope.PLANT) == ownerUid) {
            AppMediaStorage.rollbackPendingMedia(appContext, uri)
        }
    }
}
