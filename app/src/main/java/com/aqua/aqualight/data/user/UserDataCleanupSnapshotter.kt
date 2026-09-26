package com.aqua.aqualight.data.user

import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import java.util.concurrent.CancellationException

internal data class UserDataCleanupMediaSnapshot(
    val aquariumPhotoUris: List<String>,
    val profilePhotoUri: String,
    val issues: List<UserDataCleaner.CleanupIssue>
)

internal object UserDataCleanupSnapshotter {

    suspend fun capture(
        ownerUid: String,
        tankStore: AquariumTankDataStoreManager,
        preferences: UserPreferencesManager
    ): UserDataCleanupMediaSnapshot {
        val issues = mutableListOf<UserDataCleaner.CleanupIssue>()

        val aquariumPhotoUris = runCatching {
            tankStore.tanksSnapshotForOwner(ownerUid)
                .flatMap { tank -> tank.cleanupPhotoUris() }
        }.getOrElse { error ->
            error.throwIfCancellation()
            issues += UserDataCleaner.CleanupIssue(
                step = UserDataCleaner.Step.AQUARIUM_TANKS,
                error = error
            )
            emptyList()
        }

        val profilePhotoUri = runCatching {
            preferences.profilePhotoUrlForOwner(ownerUid)
        }.getOrElse { error ->
            error.throwIfCancellation()
            issues += UserDataCleaner.CleanupIssue(
                step = UserDataCleaner.Step.USER_PREFERENCES,
                error = error
            )
            ""
        }

        return UserDataCleanupMediaSnapshot(
            aquariumPhotoUris = aquariumPhotoUris,
            profilePhotoUri = profilePhotoUri,
            issues = issues.toList()
        )
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) throw this
    }
}

private fun com.aqua.aqualight.data.aquarium.model.SavedAquariumTank.cleanupPhotoUris():
    List<String> =
    buildList {
        photoUri?.takeIf(String::isNotBlank)?.let(::add)
        plants.mapNotNull { plant ->
            plant.photoUri?.takeIf(String::isNotBlank)
        }.forEach(::add)
        livestock.mapNotNull { item ->
            item.photoUri?.takeIf(String::isNotBlank)
        }.forEach(::add)
    }
