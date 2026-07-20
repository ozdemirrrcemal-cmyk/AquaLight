package com.aqua.aqualight.data.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialStoreMigrationPolicyTest {

    @Test
    fun firstCommercialSchemasStartAtVersionOneWithoutMigrationLayer() {
        assertEquals(1, CommercialStoreSchema.AQUARIUM_TANKS_VERSION)
        assertEquals(1, CommercialStoreSchema.CARE_TASKS_VERSION)
        assertEquals(1, CommercialStoreSchema.USER_PREFERENCES_VERSION)

        val policy = File(
            locateRepositoryRoot(),
            "docs/stage5-data-integrity-contract.md"
        ).readText()
        val normalizedPolicy = policy.replace(Regex("\\s+"), " ")

        assertTrue(
            normalizedPolicy.contains(
                "Status: N/A for the first commercial release schema"
            )
        )
        assertTrue(
            normalizedPolicy.contains(
                "has not shipped a public Tank, Care Task, or encrypted User Preferences schema"
            )
        )
        assertTrue(
            normalizedPolicy.contains(
                "there is no legitimate source schema to migrate"
            )
        )
        assertTrue(normalizedPolicy.contains("no legacy `DataMigration` is installed"))
        assertTrue(
            normalizedPolicy.contains(
                "The first post-release schema change must increment"
            )
        )
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
}
