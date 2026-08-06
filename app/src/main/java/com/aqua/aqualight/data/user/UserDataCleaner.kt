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

        fun recordIssue(step: Step, error: Throwable) {
            error.throwIfCancellation()
            issues += CleanupIssue(step = step, error = error)
        }

        val tankPhotoUris = runCatching {
            tankDataStoreManager.tanksSnapshotForOwner(targetOwnerUid)
                .mapNotNull { tank -> tank.photoUri }
        }.getOrElse { error ->
            recordIssue(Step.AQUARIUM_TANKS, error)
            emptyList()
        }

        val profilePhotoUri = runCatching {
            userPreferencesManager.profilePhotoUrlForOwner(targetOwnerUid)
        }.getOrElse { error ->
            recordIssue(Step.USER_PREFERENCES, error)
            ""
        }

        suspend fun runStep(step: Step, block: suspend () -> Unit) {
            runCatching { block() }.onFailure { error ->
                recordIssue(step, error)
            }
        }

        if (stopSessionBoundServices) {
            runStep(Step.SESSION_BOUND_SERVICES) {
                val stopResult = SessionBoundServiceManager.stop(
                    context = appContext,
                    cancelNotifications = true,
                    expectedOwnerUid = targetOwnerUid
                )
                stopResult.exceptionOrNull()?.let { error -> throw error }
            }
        }

        runStep(Step.CARE_TASKS) {
            NotificationPlatform.get(appContext)
                .preferenceUseCase
                .cancelOwner(targetOwnerUid)
            CareTaskDataStoreManager.create(appContext)
                .clearAllTasks(ownerUid = targetOwnerUid)
        }

        runStep(Step.AQUARIUM_TANKS) {
            tankDataStoreManager.clearAllTanks(ownerUid = targetOwnerUid)
        }

        runStep(Step.DEVICE_ASSIGNMENTS) {
            TankDeviceAssignmentStore.get(appContext)
                .clearOwnerAssignments(ownerUid = targetOwnerUid)
        }

        runStep(Step.PROVISIONING_SESSIONS) {
            clearProvisioningData(targetOwnerUid)
        }

        runStep(Step.KNOWN_DEVICES) {
            DeviceKnownStore(
                context = appContext,
                ownerUid = targetOwnerUid
            ).clearOwnerData()
        }

        runStep(Step.DEVICE_CREDENTIALS) {
            DeviceCredentialStore(
                context = appContext,
                ownerUid = targetOwnerUid
            ).clearOwner()
        }

        runStep(Step.APP_OWNED_FILES) {
            clearAppOwnedUserFiles(
                ownerUid = targetOwnerUid,
                profilePhotoUri = profilePhotoUri,
                tankPhotoUris = tankPhotoUris
            )
        }

        if (clearUserPreferences) {
            runStep(Step.USER_PREFERENCES) {
                userPreferencesManager.clearUserDataForOwner(
                    ownerUid = targetOwnerUid
                )
            }
        }

        return CleanupResult(issues = issues.toList())
    }

    private suspend fun clearProvisioningData(ownerUid: String) {
        val failures = mutableListOf<Throwable>()

        suspend fun attempt(block: suspend () -> Unit) {
            runCatching { block() }.onFailure { error ->
                error.throwIfCancellation()
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

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) throw this
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
