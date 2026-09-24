package com.aqua.aqualight.application.aquarium.health.algae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun profilesDoNotEmbedSourceLinks() {
        AlgaeKnowledgeCatalog.records.forEach { profile ->
            assertFalse(
                profile.evidenceKeys.any { key ->
                    key.contains("http", ignoreCase = true)
                }
            )
        }
    }
}
