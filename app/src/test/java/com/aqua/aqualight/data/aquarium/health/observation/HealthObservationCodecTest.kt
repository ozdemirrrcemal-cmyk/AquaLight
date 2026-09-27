package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.observation.AlgaeAppearance
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeExtent
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeLocation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPreparation
import com.aqua.aqualight.application.aquarium.health.observation.LivestockFinding
import com.aqua.aqualight.application.aquarium.health.observation.ObservationFollowUp
import com.aqua.aqualight.application.aquarium.health.observation.ObservationIdentity
import com.aqua.aqualight.application.aquarium.health.observation.ObservationNotes
import com.aqua.aqualight.application.aquarium.health.observation.ObservationOperatingEvidence
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterEvidencePolicy
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterWindow
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import com.aqua.aqualight.application.aquarium.health.observation.ReportedCo2Pattern
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.context.HealthLivestockContext
import com.aqua.aqualight.application.aquarium.health.context.HealthPlantContext
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthObservationCodecTest {
    private val context = AquariumHealthContext(HealthContextCapture(TIME, "test-context-v1"),
        HealthTankFacts(2, "Freshwater Fish", "Freshwater", null, null), HealthCatalogRevisions("p1", "l1"),
        listOf(HealthPlantContext(3, "plant-catalog", "Original plant", HealthEntityResolution.PARTIAL, null)),
        listOf(HealthLivestockContext(4, "fish-catalog", "Original group", 2,
            HealthEntityResolution.CUSTOM_UNVERIFIED, null, null)), emptyList())

    @Test
    fun allThreeTypesRoundTripTheirOwnObservationsAndFrozenContext() {
        val types = listOf(HealthObservation.Algae(setOf(AlgaeLocation.GLASS), setOf(AlgaeAppearance.FILM),
            AlgaeExtent.LOCAL, ObservationOperatingEvidence(480, "user reading", ReportedCo2Pattern.NOT_USED, "none")),
            HealthObservation.Plant(3, setOf(PlantFinding.ALGAE_PRESENT)),
            HealthObservation.Livestock(4, 1, setOf(LivestockFinding.APPETITE_CHANGE)))
        types.forEach { type ->
            val prepared = prepared(type)
            val raw = HealthObservationRecordCodec.encode("owner-a", 10, TIME, prepared)
            val reopened = HealthObservationRecordCodec.decode(StoredHealthObservation.parseFrom(raw.toByteArray()))
            assertEquals(prepared.input, reopened.input)
            assertEquals(prepared.assessment, reopened.assessment)
            assertEquals(context.revision, reopened.evidence.context.revision)
            assertEquals(prepared.subject, reopened.subject)
            assertEquals(raw, raw.toEntity().toStored())
        }
    }

    @Test
    fun unsupportedVersionAndChangedContextFailWithoutReplacingHistory() {
        val raw = record()
        assertThrows(IllegalArgumentException::class.java) {
            HealthObservationRecordCodec.decode(raw.toBuilder().setSchemaVersion(99).build())
        }
        assertThrows(IllegalArgumentException::class.java) {
            HealthObservationRecordCodec.decode(raw.toBuilder().setEvidence(raw.evidence.toBuilder()
                .setContextJson(raw.evidence.contextJson.replace("Original plant", "Renamed"))).build())
        }
        assertEquals("Original plant", HealthObservationRecordCodec.decode(raw).subject?.displayName)
    }

    @Test
    fun corruptPayloadCannotHideBehindValidIndexedIdentity() {
        val row = record().toEntity()
        row.payload[0] = (row.payload[0].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { row.toStored() }
    }

    @Test
    fun missingWaterCannotClaimAnAgeOrAvailableFinding() {
        val raw = record()
        assertThrows(IllegalArgumentException::class.java) {
            HealthObservationRecordCodec.decode(raw.toBuilder().setEvidence(raw.evidence.toBuilder()
                .setWaterAgeAtObservationMillis(0)).build())
        }
        assertTrue(HealthObservationRecordCodec.decode(raw).assessment.waterFindings.isEmpty())
    }

    private fun record() = HealthObservationRecordCodec.encode("owner-a", 10, TIME,
        prepared(HealthObservation.Plant(3, setOf(PlantFinding.LEAF_DAMAGE))))

    private fun prepared(observation: HealthObservation): PreparedHealthObservation {
        val input = HealthObservationInput(ObservationIdentity(2, TIME, UUID.randomUUID().toString()), observation,
            ObservationNotes("User observation", null, ObservationFollowUp(ObservationPhase.OBSERVATION, null)))
        val water = ObservationWaterEvidencePolicy.resolve(2, TIME, null, ObservationWaterWindow(100, "test-window"))
        return prepareHealthObservation(input, HealthObservationPreparation(context, water))
    }

    private companion object { const val TIME = 1_780_000_000_000L }
}
