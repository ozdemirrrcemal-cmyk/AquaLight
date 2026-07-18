package com.aqua.aqualight.data.media

import android.content.Context
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reconciles crash/process-death media candidates against authoritative owner-scoped stores. */
class AppMediaRecoveryManager(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext = context.applicationContext
    private val preferences = UserPreferencesManager.create(appContext)
    private val tanks = AquariumTankDataStoreManager(appContext)

    suspend fun reconcileActiveOwner() {
        val ownerUid = UserDataScope.currentUid().takeIf(String::isNotBlank) ?: return
        reconcileOwner(ownerUid)
    }

    suspend fun reconcileOwner(ownerUid: String) = withContext(dispatcher) {
        val normalizedOwnerUid = ownerUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
        val referencedUris = buildSet {
            preferences.profilePhotoUrlForOwner(normalizedOwnerUid)
                .takeIf(String::isNotBlank)
                ?.let(::add)
            tanks.tanksSnapshotForOwner(normalizedOwnerUid)
                .mapNotNull { tank -> tank.photoUri?.takeIf(String::isNotBlank) }
                .forEach(::add)
        }

        AppMediaStorage.reconcilePendingMedia(
            context = appContext,
            ownerUid = normalizedOwnerUid,
            referencedUris = referencedUris
        )
        AppMediaStorage.reconcilePendingDeletions(
            context = appContext,
            ownerUid = normalizedOwnerUid,
            referencedUris = referencedUris
        )
        AppMediaStorage.reconcileUnreferencedCommittedMedia(
            context = appContext,
            ownerUid = normalizedOwnerUid,
            referencedUris = referencedUris
        )
    }
}
