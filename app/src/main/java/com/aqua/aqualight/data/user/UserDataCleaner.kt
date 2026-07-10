package com.aqua.aqualight.data.user

import android.content.Context
import android.net.Uri
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import java.io.File

/**
 * Clears local data that belongs to the active user account.
 *
 * Records are removed only for the target Firebase uid. Normal logout
 * intentionally does not call this cleaner.
 */
class UserDataCleaner private constructor(
    private val appContext: Context
) {

    enum class Step {
        SESSION_BOUND_SERVICES,
        CARE_TASKS,
        AQUARIUM_TANKS,
        DEVICES,
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
            val Success = CleanupResult(
                issues = emptyList()
            )
        }
    }

    companion object {
        fun create(
            context: Context
        ): UserDataCleaner {
            return UserDataCleaner(
                appContext = context.applicationContext
            )
        }
    }

    suspend fun clearLocalUserData(
        ownerUid: String? = null,
        clearUserPreferences: Boolean = true,
        stopSessionBoundServices: Boolean = true
    ): CleanupResult {
        val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()
        val issues = mutableListOf<CleanupIssue>()
        val tankDataStoreManager = AquariumTankDataStoreManager(
            appContext
        )
        val tankPhotoUris = tankDataStoreManager
            .tanksSnapshotForOwner(
                ownerUid = targetOwnerUid
            )
            .mapNotNull { tank ->
                tank.photoUri
            }
        val userPreferencesManager = UserPreferencesManager.create(
            appContext
        )
        val profilePhotoUri = userPreferencesManager.profilePhotoUrlForOwner(
            ownerUid = targetOwnerUid
        )

        suspend fun runStep(
            step: Step,
            block: suspend () -> Unit
        ) {
            runCatching {
                block()
            }.onFailure { error ->
                issues += CleanupIssue(
                    step = step,
                    error = error
                )
            }
        }

        if (stopSessionBoundServices) {
            runStep(
                step = Step.SESSION_BOUND_SERVICES
            ) {
                SessionBoundServiceManager.stop(
                    context = appContext,
                    cancelNotifications = true
                )
            }
        }

        runStep(
            step = Step.CARE_TASKS
        ) {
            CareTaskDataStoreManager.create(
                appContext
            ).clearAllTasks(
                ownerUid = targetOwnerUid,
                cancelReminders = true
            )
        }

        runStep(
            step = Step.AQUARIUM_TANKS
        ) {
            tankDataStoreManager.clearAllTanks(
                ownerUid = targetOwnerUid
            )
        }

        runStep(
            step = Step.DEVICES
        ) {
            TankDeviceAssignmentRepositoryProvider
                .get(appContext)
                .clearAssignmentsForOwner(
                    ownerUid = targetOwnerUid
                )
        }

        runStep(
            step = Step.APP_OWNED_FILES
        ) {
            clearAppOwnedUserFiles(
                profilePhotoUri = profilePhotoUri,
                tankPhotoUris = tankPhotoUris
            )
        }

        if (clearUserPreferences) {
            runStep(
                step = Step.USER_PREFERENCES
            ) {
                userPreferencesManager.clearUserDataForOwner(
                    ownerUid = targetOwnerUid
                )
            }
        }

        return CleanupResult(
            issues = issues.toList()
        )
    }

    private fun String?.orCurrentOwnerUidOrReturn(): String {
        val explicitOwnerUid = UserDataScope.normalizeOwnerUid(this)

        if (explicitOwnerUid.isNotBlank()) {
            return explicitOwnerUid
        }

        return UserDataScope.currentUid()
    }

    private fun clearAppOwnedUserFiles(
        profilePhotoUri: String,
        tankPhotoUris: List<String>
    ) {
        (tankPhotoUris + profilePhotoUri)
            .filter { uri ->
                uri.isNotBlank()
            }
            .forEach { uri ->
                deleteAppOwnedUri(uri)
            }

        File(
            appContext.cacheDir,
            "feedback_temp.jpg"
        ).delete()
    }

    private fun deleteAppOwnedUri(
        value: String
    ) {
        val uri = runCatching {
            Uri.parse(value)
        }.getOrNull() ?: return

        if (uri.scheme == "content") {
            runCatching {
                appContext.contentResolver.delete(
                    uri,
                    null,
                    null
                )
            }
            return
        }

        val file = when (uri.scheme) {
            "file" -> uri.path?.let(::File)
            null,
            "" -> File(value)
            else -> null
        } ?: return

        if (!file.isAppOwnedFile()) {
            return
        }

        file.deleteRecursively()
    }

    private fun File.isAppOwnedFile(): Boolean {
        val canonicalFile = runCatching {
            canonicalFile
        }.getOrNull() ?: return false

        val allowedRoots = listOf(
            File(appContext.filesDir, "profile_photos"),
            File(appContext.filesDir, "tank_photos"),
            File(appContext.cacheDir, "tank_exports")
        )

        return allowedRoots.any { root ->
            val canonicalRoot = runCatching {
                root.canonicalFile
            }.getOrNull() ?: return@any false

            canonicalFile.path == canonicalRoot.path ||
                canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
        }
    }
}
