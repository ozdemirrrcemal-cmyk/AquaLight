package com.aqua.aqualight.data.auth

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialAccountDeletionArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun deletionHasDurableCloudAuthAndLocalRecoveryStages() {
        val manager = source(
            "app/src/main/java/com/aqua/aqualight/data/auth/AccountDeletionManager.kt"
        )
        val application = source(
            "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
        )

        listOf(
            "STARTED",
            "CLOUD_CLEARED",
            "AUTH_DELETE_REQUESTED",
            "ACCOUNT_DELETED",
            "clearCloudUserData",
            "clearLocalUserData",
            "checkpointStore.clear"
        ).forEach { required ->
            assertTrue("Missing deletion boundary: $required", manager.contains(required))
        }
        assertTrue(application.contains("resumePendingDeletion()"))
    }

    @Test
    fun everyOwnerLocalDataCategoryRemainsInTheDeletionBoundary() {
        val cleaner = source(
            "app/src/main/java/com/aqua/aqualight/data/user/UserDataCleaner.kt"
        )
        listOf(
            "SESSION_BOUND_SERVICES",
            "CARE_TASKS",
            "AQUARIUM_TANKS",
            "DEVICE_ASSIGNMENTS",
            "PROVISIONING_SESSIONS",
            "KNOWN_DEVICES",
            "OTA_TRANSACTIONS",
            "DEVICE_CREDENTIALS",
            "APP_OWNED_FILES",
            "USER_PREFERENCES"
        ).forEach { required ->
            assertTrue("Missing local deletion category: $required", cleaner.contains(required))
        }
    }

    private fun source(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
