package com.aqua.aqualight.data.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialStoreMigrationPolicyTest {

    @Test
    fun unreleasedTankV1IsRejectedAndCommercialDateOnlyContractIsV2() {
        assertEquals(2, CommercialStoreSchema.AQUARIUM_TANKS_VERSION)
        assertEquals(1, CommercialStoreSchema.CARE_TASKS_VERSION)
        assertEquals(1, CommercialStoreSchema.USER_PREFERENCES_VERSION)

        val policy = File(
            locateRepositoryRoot(),
            "docs/stage5-data-integrity-contract.md"
        ).readText()

        assertTrue(policy.contains("Status: no public migration source exists"))
        assertTrue(policy.contains("has not shipped a public Tank, Care Task, or encrypted User"))
        assertTrue(policy.contains("unreleased development contract"))
        assertTrue(policy.contains("deliberately rejected rather than migrated"))
        assertTrue(policy.contains("No legacy `DataMigration`, alias, or compatibility"))
        assertTrue(policy.contains("After public release, every schema change must increment"))
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
