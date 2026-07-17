package com.aqua.aqualight.data.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialStoreArchitectureTest {

    private val repositoryRoot: File = locateRepositoryRoot()
    private val productionRoot = File(repositoryRoot, "app/src/main")

    @Test
    fun productionCodeContainsNoUnreleasedLegacyOwnershipBridge() {
        val productionText = productionFiles()
            .joinToString("\n") { file -> file.readText() }

        FORBIDDEN_LEGACY_TOKENS.forEach { token ->
            assertFalse(
                "Commercial production code must not contain legacy token: $token",
                productionText.contains(token)
            )
        }
    }

    @Test
    fun authoritativeStoreFilesAreOpenedOnlyByTheirManagers() {
        assertOnlyFilesContain(
            token = "aquarium_tanks.pb",
            expectedRelativePaths = setOf(
                "app/src/main/java/com/aqua/aqualight/data/aquarium/store/" +
                    "AquariumTankDataStoreManager.kt"
            )
        )
        assertOnlyFilesContain(
            token = "care_tasks.pb",
            expectedRelativePaths = setOf(
                "app/src/main/java/com/aqua/aqualight/data/care/" +
                    "CareTaskDataStoreManager.kt"
            )
        )
        assertOnlyFilesContain(
            token = "user_prefs.pb",
            expectedRelativePaths = setOf(
                "app/src/main/java/com/aqua/aqualight/data/user/" +
                    "UserPreferencesManager.kt"
            )
        )
    }

    @Test
    fun everyCommercialProtoDeclaresAnExplicitSchemaVersion() {
        listOf(
            "app/src/main/proto/aquarium_tanks.proto",
            "app/src/main/proto/care_tasks.proto",
            "app/src/main/proto/user_prefs.proto"
        ).forEach { relativePath ->
            val text = File(repositoryRoot, relativePath).readText()
            assertTrue(
                "$relativePath must declare schema_version.",
                SCHEMA_VERSION_PATTERN.containsMatchIn(text)
            )
        }
    }

    @Test
    fun serializersContainNoSchemaZeroCompatibilityFallback() {
        listOf(
            "app/src/main/java/com/aqua/aqualight/data/aquarium/store/" +
                "AquariumTanksSerializer.kt",
            "app/src/main/java/com/aqua/aqualight/data/care/" +
                "CareTasksCommercialSerializer.kt",
            "app/src/main/java/com/aqua/aqualight/data/user/" +
                "UserPreferencesSerializer.kt"
        ).forEach { relativePath ->
            val text = File(repositoryRoot, relativePath).readText()
            assertFalse(
                "$relativePath must not accept schema version zero.",
                text.contains("schemaVersion == 0") ||
                    text.contains("schema_version == 0")
            )
        }
    }

    @Test
    fun managersUseTheCentralCommercialRulesAndStrictSerializers() {
        val tankManager = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/data/aquarium/store/" +
                "AquariumTankDataStoreManager.kt"
        ).readText()
        val careManager = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/data/care/" +
                "CareTaskDataStoreManager.kt"
        ).readText()
        val preferenceManager = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/data/user/" +
                "UserPreferencesManager.kt"
        ).readText()

        assertTrue(tankManager.contains("TankStoreRules.validateStore"))
        assertTrue(tankManager.contains("TankStoreRules.validateTank"))
        assertTrue(careManager.contains("CareTasksCommercialSerializer"))
        assertTrue(careManager.contains("CareTaskStoreRules.nextUniqueId"))
        assertTrue(careManager.contains("toCareTaskStrict"))
        assertFalse(careManager.contains("getOrElse"))
        assertTrue(preferenceManager.contains("ReplaceFileCorruptionHandler"))
        assertTrue(preferenceManager.contains("updateValidated"))
        assertFalse(preferenceManager.contains("emit(UserPreferences"))
    }

    @Test
    fun careTaskFormUsesSharedLimitsWithoutSilentCoercion() {
        val form = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/" +
                "AddCareTaskFragment.kt"
        ).readText()

        assertTrue(form.contains("CareTaskInputLimits.parseRepeatIntervalDays"))
        assertTrue(form.contains("CareTaskInputLimits.parseMissedReminderDays"))
        assertFalse(form.contains("coerceAtLeast(1)"))
    }

    private fun assertOnlyFilesContain(
        token: String,
        expectedRelativePaths: Set<String>
    ) {
        val actual = productionFiles()
            .filter { file -> file.readText().contains(token) }
            .mapTo(mutableSetOf()) { file ->
                file.relativeTo(repositoryRoot).invariantSeparatorsPath
            }

        assertEquals(
            "Unexpected direct access to authoritative store file $token.",
            expectedRelativePaths,
            actual
        )
    }

    private fun productionFiles(): List<File> {
        return productionRoot.walkTopDown()
            .filter(File::isFile)
            .filter { file ->
                file.extension in setOf("kt", "java", "proto", "xml")
            }
            .toList()
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile

        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) {
                return candidate
            }
            candidate = candidate.parentFile
        }

        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private companion object {
        val FORBIDDEN_LEGACY_TOKENS = setOf(
            "LEGACY_OWNER_UID",
            "assignLegacyTanksToOwner",
            "assignLegacyTasksToOwner",
            "UserDataOwnershipMigrator",
            "includeLegacy = true"
        )

        val SCHEMA_VERSION_PATTERN = Regex(
            "\\b(?:u?int32)\\s+schema_version\\s*=\\s*\\d+\\s*;"
        )
    }
}
