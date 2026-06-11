package com.aqua.aqualight.data.user

import android.content.Context
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.LightAutomationDataStoreManager
import com.aqua.aqualight.data.devices.light.presets.LightPresetDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.LightProgramsDataStoreManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceDataCenter
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceSnapshotCache
import com.aqua.aqualight.data.devices.light.runtime.LightManualRuntimeStore
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import java.io.File

/**
 * Clears local data that belongs to the active user account.
 *
 * Until full UID-scoped stores are introduced, account deletion treats every
 * local aquarium/device/care/light store as belonging to the active session and
 * clears them together. Normal logout intentionally does not call this cleaner.
 */
class UserDataCleaner private constructor(
    private val appContext: Context
) {

    enum class Step {
        SESSION_BOUND_SERVICES,
        CARE_TASKS,
        AQUARIUM_TANKS,
        DEVICES,
        LIGHT_PROGRAMS,
        LIGHT_PRESETS,
        LIGHT_AUTOMATION,
        LIGHT_RUNTIME,
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
        clearUserPreferences: Boolean = true,
        stopSessionBoundServices: Boolean = true
    ): CleanupResult {
        val issues = mutableListOf<CleanupIssue>()

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
            step = Step.LIGHT_RUNTIME
        ) {
            clearLightRuntime()
        }

        runStep(
            step = Step.CARE_TASKS
        ) {
            CareTaskDataStoreManager.create(
                appContext
            ).clearAllTasks(
                cancelReminders = true
            )
        }

        runStep(
            step = Step.AQUARIUM_TANKS
        ) {
            AquariumTankDataStoreManager(
                appContext
            ).clearAllTanks()
        }

        runStep(
            step = Step.DEVICES
        ) {
            DevicesDataStoreManager.create(
                appContext
            ).clearAllDevices()
        }

        runStep(
            step = Step.LIGHT_PROGRAMS
        ) {
            LightProgramsDataStoreManager(
                appContext
            ).clearAllPrograms()
        }

        runStep(
            step = Step.LIGHT_PRESETS
        ) {
            LightPresetDataStoreManager(
                appContext
            ).clearAllPresets()
        }

        runStep(
            step = Step.LIGHT_AUTOMATION
        ) {
            LightAutomationDataStoreManager(
                appContext
            ).clearAllSettings()
        }

        runStep(
            step = Step.APP_OWNED_FILES
        ) {
            clearAppOwnedUserFiles()
        }

        if (clearUserPreferences) {
            runStep(
                step = Step.USER_PREFERENCES
            ) {
                UserPreferencesManager.create(
                    appContext
                ).clearAllUserData()
            }
        }

        return CleanupResult(
            issues = issues.toList()
        )
    }

    private fun clearLightRuntime() {
        LightDeviceDataCenter.stopAll()

        LightDeviceSnapshotCache.configure(
            context = appContext
        )
        LightDeviceSnapshotCache.clearAll()

        LightManualRuntimeStore.clearAll()
    }

    private fun clearAppOwnedUserFiles() {
        listOf(
            File(
                appContext.filesDir,
                "profile_photos"
            ),
            File(
                appContext.filesDir,
                "tank_photos"
            ),
            File(
                appContext.cacheDir,
                "tank_exports"
            ),
            File(
                appContext.cacheDir,
                "feedback_temp.jpg"
            )
        ).forEach { file ->
            file.deleteRecursively()
        }
    }
}
