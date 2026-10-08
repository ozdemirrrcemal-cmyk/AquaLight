package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantObservationRulesTest {
    @Test fun healthyIsExclusiveInBothSelectionDirections() {
        assertEquals(setOf("healthy"), PlantObservationRules.toggle(setOf("yellowing", "damage"), "healthy"))
        assertEquals(setOf("algae"), PlantObservationRules.toggle(setOf("healthy"), "algae"))
        assertEquals(emptySet<String>(), PlantObservationRules.toggle(setOf("healthy"), "healthy"))
    }

    @Test fun invalidOrContradictorySignsCannotBeSaved() {
        assertFalse(PlantObservationRules.canSave(emptySet(), ""))
        assertFalse(PlantObservationRules.canSave(listOf("algae", "algae"), ""))
        assertFalse(PlantObservationRules.canSave(setOf("healthy", "algae"), ""))
        assertFalse(PlantObservationRules.canSave(setOf("unknown"), ""))
        assertTrue(PlantObservationRules.canSave(setOf("yellowing", "damage"), ""))
    }

    @Test fun otherRequiresAnExplanationAndNotesHaveABoundedLength() {
        assertFalse(PlantObservationRules.canSave(setOf("other"), "  "))
        assertTrue(PlantObservationRules.canSave(setOf("other"), "New leaves look unusually small"))
        assertFalse(PlantObservationRules.canSave(setOf("healthy"), "x".repeat(4001)))
    }
}
