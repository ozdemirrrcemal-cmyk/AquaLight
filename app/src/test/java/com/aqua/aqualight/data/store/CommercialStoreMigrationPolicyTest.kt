package com.aqua.aqualight.data.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialStoreMigrationPolicyTest {

    @Test
    fun tankCatalogCutoverHasNoLegacyMigrationLayer() {
        assertEquals(2, CommercialStoreSchema.AQUARIUM_TANKS_VERSION)
        assertEquals(1, CommercialStoreSchema.CARE_TASKS_VERSION)
        assertEquals(1, CommercialStoreSchema.USER_PREFERENCES_VERSION)
        assertEquals(1, CommercialStoreSchema.LIGHT_LIBRARY_VERSION)

        val policy = File(
            locateRepositoryRoot(),
            "docs/stage5-data-integrity-contract.md"
        ).readText()
        val normalizedPolicy = policy.replace(Regex("\\s+"), " ")

        assertTrue(
            normalizedPolicy.contains(
                "Aquarium Tanks schema version `2` is a deliberate clean cutover"
            )
        )
        assertTrue(
            normalizedPolicy.contains(
                "Version `1` tank stores are rejected"
            )
        )
        assertTrue(normalizedPolicy.contains("no legacy `DataMigration` is installed"))
        assertTrue(
            normalizedPolicy.contains(
                "Every livestock record has a non-blank stable identity"
            )
        )
        assertTrue(normalizedPolicy.contains("Blank identities are invalid"))
        assertTrue(normalizedPolicy.contains("there is no name/category inference fallback"))
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
