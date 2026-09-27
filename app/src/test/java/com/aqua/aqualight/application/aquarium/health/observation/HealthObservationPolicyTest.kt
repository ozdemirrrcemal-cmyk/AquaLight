package com.aqua.aqualight.application.aquarium.health.observation

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HealthObservationPolicyTest {
    @Test
    fun registeredIdentityAndUnknownVersusEmptyFindingsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(HealthObservation.Plant(0, setOf(PlantFinding.UNKNOWN)))
        }
        assertThrows(IllegalArgumentException::class.java) { validate(HealthObservation.Plant(1, emptySet())) }
        assertThrows(IllegalArgumentException::class.java) {
            validate(HealthObservation.Plant(1, setOf(PlantFinding.UNKNOWN, PlantFinding.ALGAE_PRESENT)))
        }
        assertEquals(1L, validate(HealthObservation.Plant(1, setOf(PlantFinding.UNKNOWN))).observation.subjectId)
    }

    @Test
    fun savedInputDoesNotRetainMutableCallerCollections() {
        val findings = mutableSetOf(PlantFinding.LEAF_DAMAGE)
        val frozen = validate(HealthObservation.Plant(1, findings)).observation as HealthObservation.Plant
        findings.clear()
        assertEquals(setOf(PlantFinding.LEAF_DAMAGE), frozen.findings)
        assertThrows(UnsupportedOperationException::class.java) {
            (frozen.findings as MutableSet<PlantFinding>).clear()
        }
    }

    @Test
    fun quantityMustBePositiveButUnknownRemainsUnknown() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(HealthObservation.Livestock(1, 0, setOf(LivestockFinding.UNKNOWN)))
        }
        val frozen = validate(HealthObservation.Livestock(1, null, setOf(LivestockFinding.UNKNOWN)))
        assertEquals(null, (frozen.observation as HealthObservation.Livestock).affectedQuantity)
    }

    @Test
    fun interventionRequiresAnExplicitPreviousObservationAndDescription() {
        val input = input(HealthObservation.Plant(1, setOf(PlantFinding.ALGAE_PRESENT)))
        val note = ObservationNotes("", null, ObservationFollowUp(ObservationPhase.INTERVENTION, 7))
        assertThrows(IllegalArgumentException::class.java) {
            HealthObservationPolicy.validate(input.copy(notes = note), TIME)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HealthObservationPolicy.validate(input.copy(notes = note.copy(text = "cleaned glass",
                followUp = ObservationFollowUp(ObservationPhase.INTERVENTION, null))), TIME)
        }
    }

    private fun validate(observation: HealthObservation) = HealthObservationPolicy.validate(input(observation), TIME)
    private fun input(observation: HealthObservation) = HealthObservationInput(
        ObservationIdentity(2, TIME, UUID.randomUUID().toString()), observation,
        ObservationNotes("", null, ObservationFollowUp(ObservationPhase.OBSERVATION, null)))

    private companion object { const val TIME = 1_780_000_000_000L }
}
