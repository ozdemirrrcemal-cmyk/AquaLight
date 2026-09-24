package com.aqua.aqualight.application.aquarium.health.algae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgaeKnowledgeCatalogTest {

    @Test
    fun catalogHasOneReviewedProfileForEverySupportedType() {
        assertEquals(
            AlgaeTypeId.entries.toSet(),
            AlgaeKnowledgeCatalog.records.map(AlgaeKnowledgeProfile::id).toSet()
        )
    }

    @Test
    fun everyProfileUsesRegisteredEvidenceRecords() {
        AlgaeKnowledgeCatalog.records.forEach { profile ->
            assertTrue(profile.evidence.isNotEmpty())
            profile.evidence.forEach { evidenceId ->
                AlgaeEvidenceCatalog.requireRecord(evidenceId)
            }
        }
    }

    @Test
    fun evidenceCatalogHasOneRecordPerEvidenceId() {
        assertEquals(
            AlgaeEvidenceId.entries.toSet(),
            AlgaeEvidenceCatalog.records.map(AlgaeEvidenceRecord::id).toSet()
        )
    }
}
