package com.aqua.aqualight.data.user.archive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataArchiveArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `data management presentation stays on central UI and application boundaries`() {
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/" +
                "DataManagementFragment.kt"
        )
        val layout = source("app/src/main/res/layout/fragment_data_management.xml")

        assertTrue(fragment.contains("setupAquaHeader(fragment = this)"))
        assertTrue(fragment.contains("defaultViewModelFactory"))
        assertTrue(fragment.contains("FeedbackBottomSheet"))
        assertTrue(fragment.contains("UserDataCreateDocumentContract"))
        assertTrue(fragment.contains("UserDataOpenBackupDocumentContract"))
        assertTrue(layout.contains("@layout/layout_aqua_header"))
        assertTrue(layout.contains("@style/Widget.Aqua.Card"))
        assertTrue(layout.contains("@color/background_color"))
        assertTrue(layout.contains("@dimen/aqua_"))

        FORBIDDEN_PRESENTATION_TOKENS.forEach { token ->
            assertFalse("Data management presentation must not contain $token", fragment.contains(token))
        }
    }

    @Test
    fun `data management uses the central settings navigation graph and Safe Args`() {
        val navigation = source("app/src/main/res/navigation/nav_settings.xml")
        val appSettings = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/AppSettingsFragment.kt"
        )

        assertTrue(navigation.contains("@+id/dataManagementFragment"))
        assertTrue(navigation.contains("action_appSettingsFragment_to_dataManagementFragment"))
        assertTrue(navigation.contains("@string/screen_title_data_management"))
        assertTrue(navigation.contains("@layout/fragment_data_management"))
        assertTrue(
            appSettings.contains(
                "AppSettingsFragmentDirections.actionAppSettingsFragmentToDataManagementFragment()"
            )
        )
    }

    @Test
    fun `data management copy is centralized for English and Turkish`() {
        val english = source("app/src/main/res/values/data_management_strings.xml")
        val turkish = source("app/src/main/res/values-tr/data_management_strings.xml")
        val layout = source("app/src/main/res/layout/fragment_data_management.xml")

        REQUIRED_STRING_NAMES.forEach { resourceName ->
            assertTrue(english.contains("name=\"$resourceName\""))
            assertTrue(turkish.contains("name=\"$resourceName\""))
            if (resourceName.endsWith("_title") || resourceName.endsWith("_subtitle")) {
                assertTrue(layout.contains("@string/$resourceName"))
            }
        }
    }

    @Test
    fun `backup whitelist cannot serialize credentials or ownership`() {
        val models = source(
            "app/src/main/java/com/aqua/aqualight/data/user/archive/UserDataArchiveModels.kt"
        )
        val collector = source(
            "app/src/main/java/com/aqua/aqualight/data/user/archive/" +
                "UserDataArchiveSnapshotCollector.kt"
        )
        val restorer = source(
            "app/src/main/java/com/aqua/aqualight/data/user/archive/UserDataBackupRestorer.kt"
        )

        assertFalse(models.contains("ownerUid"))
        FORBIDDEN_BACKUP_TOKENS.forEach { token ->
            assertFalse("Backup collector must not read $token", collector.contains(token))
        }
        assertTrue(restorer.contains("UserDataScope.requireCurrentUid() == ownerUid"))
        assertTrue(restorer.contains("TankDeviceAssignmentResult.DeviceNotFound -> skipped"))
        assertTrue(restorer.contains("TankDeviceAssignmentResult.Conflict"))
    }

    @Test
    fun `archive operations are composed and recovered through central owner boundaries`() {
        val graph = source(
            "app/src/main/java/com/aqua/aqualight/composition/OwnerDependencyGraph.kt"
        )
        val factory = source(
            "app/src/main/java/com/aqua/aqualight/composition/OwnerViewModelFactory.kt"
        )
        val sessionServices = source(
            "app/src/main/java/com/aqua/aqualight/data/auth/SessionBoundServiceManager.kt"
        )
        val cleaner = source(
            "app/src/main/java/com/aqua/aqualight/data/user/UserDataCleaner.kt"
        )

        assertTrue(graph.contains("val userDataArchiveOperations: UserDataArchiveOperations"))
        assertTrue(graph.contains("DefaultUserDataArchiveOperations("))
        assertTrue(graph.contains("context = appContext"))
        assertTrue(factory.contains("DataManagementViewModel::class.java"))
        assertTrue(factory.contains("archiveOperations = graph.userDataArchiveOperations"))
        assertTrue(sessionServices.contains("UserDataRestoreRecovery.create"))
        assertTrue(sessionServices.contains(".recover(normalizedOwnerUid)"))
        assertTrue(cleaner.contains("UserDataRestoreJournal(appContext).clearOwner(ownerUid)"))
        assertTrue(cleaner.contains("UserDataRestoreProvenanceStore(appContext).clearOwner(ownerUid)"))
    }

    private fun source(relativePath: String): String {
        return File(repositoryRoot, relativePath).readText()
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private companion object {
        val FORBIDDEN_PRESENTATION_TOKENS = listOf(
            "ContentResolver",
            "Firebase",
            "DataStore",
            "Gson",
            "ZipInputStream",
            "AquariumTankDataStoreManager",
            "CareTaskDataStoreManager",
            "DevicesRepository"
        )

        val FORBIDDEN_BACKUP_TOKENS = listOf(
            "FirebaseAuth",
            "GoogleSignIn",
            "ProvisioningQrSecret",
            "runtimeToken",
            "saveRuntimeToken",
            "DeviceKnownStore",
            "DeviceRuntimeRepository"
        )

        val REQUIRED_STRING_NAMES = listOf(
            "screen_title_data_management",
            "data_management_backup_title",
            "data_management_backup_subtitle",
            "data_management_restore_title",
            "data_management_restore_subtitle",
            "data_management_export_title",
            "data_management_export_subtitle"
        )
    }
}
