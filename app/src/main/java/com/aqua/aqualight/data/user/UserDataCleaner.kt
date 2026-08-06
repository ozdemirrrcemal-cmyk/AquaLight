package com.aqua.aqualight.data.user

import android.content.Context
import android.net.Uri
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStore
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningCommitRecoveryStore
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.platform.media.AppMediaStorage
import java.io.File
import java.util.concurrent.CancellationException

/** Clears local data that belongs to the active user account. */
class UserDataCleaner private constructor(
    private val appContext: Context
) {
    enum class Step {
        SESSION_BOUND_SERVICES,
        CARE_TASKS,
        DEVICE_UPDATE_NOTIFICATIONS,
        AQUARIUM_TANKS,
        DEVICE_ASSIGNMENTS,
        PROVISIONING_SESSIONS,
        KNOWN_DEVICES,
        DEVICE_CREDENTIALS,
        APP_OWNED_FILES,
        USER_PREFERENCES
    }

    data class CleanupIssue(
        val step: Step,
        val error: Throwable
    )

    data class CleanupResult(
        val issues: List<CleanupIssue>
    ) {
        val hasErrors: Boolean
            get() = issues.isNotEmpty()

        companion object {
            val Success = CleanupResult(issues = emptyList())
        }
    }

    companion object {
        fun create(context: Context): UserDataCleaner {
            return UserDataCleaner(context.applicationContext)
        }
    }

    suspend fun clearLocalUserData(
        ownerUid: String? = null,
        clearUserPreferences: Boolean = true,
        stopSessionBoundServices: Boolean = true
    ): CleanupResult {
        val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()
        val issues = mutableListOf<CleanupIssue>()
        val tankDataStoreManager = AquariumTankDataStoreManager(appContext)
        val userPreferencesManager = UserPreferencesManager.create(appContext)
        val mediaState = captureMediaCleanupState(
            ownerUid = targetOwnerUid,
            tankManager = tankDataStoreManager,
            preferencesManager = userPreferencesManager,
            issues = issues
        )

        if (stopSessionBoundServices) {
            runCleanupStep(issues, Step.SESSION_BOUND_SERVICES) {
                SessionBoundServiceManager.stop(
                    context = appContext,
                    cancelNotifications = true,
                    expectedOwnerUid = targetOwnerUid
                ).exceptionOrNull()?.let { error -> throw error }
            }
        }
        runCleanupStep(issues, Step.CARE_TASKS) {
            NotificationPlatform.get(appContext).preferenceUseCase.cancelOwner(targetOwnerUid)
            CareTaskDataStoreManager.create(appContext).clearAllTasks(targetOwnerUid)
        }
        runCleanupStep(issues, Step.DEVICE_UPDATE_NOTIFICATIONS) {
            DeviceUpdateNotificationLedger.create(appContext).clearOwner(targetOwnerUid)
        }
        runCleanupStep(issues, Step.AQUARIUM_TANKS) {
            tankDataStoreManager.clearAllTanks(ownerUid = targetOwnerUid)
        }
        runCleanupStep(issues, Step.DEVICE_ASSIGNMENTS) {
            TankDeviceAssignmentStore.get(appContext).clearOwnerAssignments(targetOwnerUid)
        }
        runCleanupStep(issues, Step.PROVISIONING_SESSIONS) {
            clearProvisioningData(targetOwnerUid)
        }
        runCleanupStep(issues, Step.KNOWN_DEVICES) {
            DeviceKnownStore(appContext, targetOwnerUid).clearOwnerData()
        }
        runCleanupStep(issues, Step.DEVICE_CREDENTIALS) {
            DeviceCredentialStore(appContext, targetOwnerUid).clearOwner()
        }
        runCleanupStep(issues, Step.APP_OWNED_FILES) {
            clearAppOwnedUserFiles(targetOwnerUid, mediaState.profilePhotoUri, mediaState.tankPhotoUris)
        }
        if (clearUserPreferences) {
            runCleanupStep(issues, Step.USER_PREFERENCES) {
                userPreferencesManager.clearUserDataForOwner(targetOwnerUid)
            }
        }
        return CleanupResult(issues = issues.toList())
    }

    private suspend fun clearProvisioningData(ownerUid: String) {
        val failures = mutableListOf<Throwable>()

        suspend fun attempt(block: suspend () -> Unit) {
            runCatching { block() }.onFailure { error ->
                error.throwIfCleanupCancellation()
                failures += error
            }
        }

        attempt {
            AqlProvisioningHandoffSaver(appContext)
                .rollbackPendingRegistrationsForOwner(ownerUid)
                .getOrThrow()
        }
        attempt {
            AqlProvisioningDraftStore(
                context = appContext,
                ownerUidProvider = { ownerUid }
            ).clearOwner()
        }
        attempt {
            AqlProvisioningQrSecretStore(
                context = appContext,
                ownerUidProvider = { ownerUid }
            ).clearOwner()
        }
        attempt {
            ProvisioningCommitRecoveryStore(appContext).clearOwner(ownerUid)
        }

        if (failures.isNotEmpty()) {
            val combined = IllegalStateException(
                "One or more provisioning data cleanup operations failed."
            )
            failures.forEach(combined::addSuppressed)
            throw combined
        }
    }

    private fun String?.orCurrentOwnerUidOrReturn(): String {
        val explicitOwnerUid = UserDataScope.normalizeOwnerUid(this)
        if (explicitOwnerUid.isNotBlank()) return explicitOwnerUid
        return UserDataScope.currentUid()
    }

    private fun clearAppOwnedUserFiles(
        ownerUid: String,
        profilePhotoUri: String,
        tankPhotoUris: List<String>
    ) {
        (tankPhotoUris + profilePhotoUri)
            .filter(String::isNotBlank)
            .forEach { uri ->
                if (!AppMediaStorage.deleteInternalMedia(appContext, uri)) {
                    deleteAppOwnedUri(uri)
                }
            }

        AppMediaStorage.discardPendingMediaForOwner(appContext, ownerUid)
        File(appContext.cacheDir, "image_processing").deleteRecursively()
    }

    private fun deleteAppOwnedUri(value: String) {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return

        if (uri.scheme == "content") {
            runCatching {
                appContext.contentResolver.delete(uri, null, null)
            }
            return
        }

        val file = when (uri.scheme) {
            "file" -> uri.path?.let(::File)
            null, "" -> File(value)
            else -> null
        } ?: return

        if (file.isAppOwnedFile()) file.deleteRecursively()
    }

    private fun File.isAppOwnedFile(): Boolean {
        val canonicalFile = runCatching { canonicalFile }.getOrNull() ?: return false
        val allowedRoots = listOf(
            File(appContext.filesDir, "profile_photos"),
            File(appContext.filesDir, "tank_photos"),
            File(appContext.cacheDir, "tank_exports"),
            File(appContext.cacheDir, "image_processing")
        )

        return allowedRoots.any { root ->
            val canonicalRoot = runCatching { root.canonicalFile }.getOrNull()
                ?: return@any false
            canonicalFile.path == canonicalRoot.path ||
                canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
        }
    }
}

private data class MediaCleanupState(
    val profilePhotoUri: String,
    val tankPhotoUris: List<String>
)

private suspend fun captureMediaCleanupState(
    ownerUid: String,
    tankManager: AquariumTankDataStoreManager,
    preferencesManager: UserPreferencesManager,
    issues: MutableList<UserDataCleaner.CleanupIssue>
): MediaCleanupState {
    val tankPhotoUris = runCatching {
        tankManager.tanksSnapshotForOwner(ownerUid).mapNotNull { tank -> tank.photoUri }
    }.getOrElse { error ->
        error.throwIfCleanupCancellation()
        issues += UserDataCleaner.CleanupIssue(UserDataCleaner.Step.AQUARIUM_TANKS, error)
        emptyList()
    }
    val profilePhotoUri = runCatching {
        preferencesManager.profilePhotoUrlForOwner(ownerUid)
    }.getOrElse { error ->
        error.throwIfCleanupCancellation()
        issues += UserDataCleaner.CleanupIssue(UserDataCleaner.Step.USER_PREFERENCES, error)
        ""
    }
    return MediaCleanupState(profilePhotoUri, tankPhotoUris)
}

private suspend fun runCleanupStep(
    issues: MutableList<UserDataCleaner.CleanupIssue>,
    step: UserDataCleaner.Step,
    block: suspend () -> Unit
) {
    runCatching { block() }.onFailure { error ->
        error.throwIfCleanupCancellation()
        issues += UserDataCleaner.CleanupIssue(step, error)
    }
}

private fun Throwable.throwIfCleanupCancellation() {
    if (this is CancellationException) throw this
}
