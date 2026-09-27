package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.context.HealthLivestockContext
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import com.aqua.aqualight.application.aquarium.health.water.WaterHazardSeverity
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessmentEngine
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterEvaluationCodecTest {
    @Test
    fun `proto round trip preserves historical context raw facts assessment and unrounded canonical values`() {
        val context = context()
        val input = input()
        val result = WaterQualityAssessmentEngine.assess(input, context)
        val stored = WaterEvaluationCodec.encode(input, context, result)
        val restored = StoredWaterEvaluation.parseFrom(stored.toByteArray())
        assertEquals(result, WaterEvaluationCodec.decode(restored, 7))
        assertEquals(stored.contextJson, restored.contextJson)
        val document = JsonParser.parseString(restored.contextJson).asJsonObject
        assertEquals("CURRENT_AT_ENTRY", document.get("timeBasis").asString)
        assertEquals(1L, document.getAsJsonArray("livestock")[0].asJsonObject.get("livestockId").asLong)
        assertEquals(7.00001, restored.canonicalMeasurementsList.single().value, 0.0)
        assertEquals(WaterHazardSeverity.ADVISORY, result.hazardSeverity)
    }

    @Test
    fun `changed current catalog cannot rewrite the frozen result`() {
        val old = WaterEvaluationCodec.encode(input(), context(),
            WaterQualityAssessmentEngine.assess(input(), context()))
        val changed = WaterQualityAssessmentEngine.assess(input(), context(maximum = 10.0))
        assertEquals(WaterHazardSeverity.NONE, changed.hazardSeverity)
        assertEquals(WaterHazardSeverity.ADVISORY, WaterEvaluationCodec.decode(old, 7).hazardSeverity)
    }

    @Test
    fun `zero is a present value while unresolved sources stay absent`() {
        val measuredZero = input().copy(measurements = input().measurements.map { it.copy(value = 0.0) })
        val zero = WaterCanonicalSnapshotCodec.encode(measuredZero).single()
        assertTrue(zero.hasValue())
        assertEquals(0.0, zero.value, 0.0)
        val unknown = measuredZero.copy(measurements = measuredZero.measurements.map {
            it.copy(selection = it.selection.copy(method = WaterMeasurementMethod.DIGITAL))
        })
        assertFalse(WaterCanonicalSnapshotCodec.encode(unknown).single().hasValue())
    }

    @Test
    fun `tampered context wrong tank unsupported schema and broken findings are rejected`() {
        val stored = WaterEvaluationCodec.encode(input(), context(),
            WaterQualityAssessmentEngine.assess(input(), context()))
        assertTrue(runCatching { WaterEvaluationCodec.decode(stored, 8) }.isFailure)
        assertTrue(runCatching {
            WaterEvaluationCodec.decode(stored.toBuilder().setContextJson("{}").build(), 7)
        }.isFailure)
        assertTrue(runCatching {
            WaterEvaluationCodec.decode(stored.toBuilder().setSchemaVersion(99).build(), 7)
        }.exceptionOrNull() is WaterAnalysisReadFailure.UnsupportedValue)
        assertTrue(runCatching {
            WaterEvaluationCodec.decode(stored.toBuilder().setHazardSeverity("NONE").build(), 7)
        }.isFailure)
    }

    @Test
    fun `internally inconsistent within range claims are rejected rather than shown as normal`() {
        val stored = WaterEvaluationCodec.encode(input(), context(),
            WaterQualityAssessmentEngine.assess(input(), context()))
        val badFinding = stored.findingsList.single { it.direction == "ABOVE" }.toBuilder()
            .setDirection("WITHIN").setSeverity("NONE").build()
        val forged = stored.toBuilder().setHazardSeverity("NONE")
            .clearFindings().addFindings(badFinding).clearRecommendations().build()
        assertTrue(runCatching { WaterEvaluationCodec.decode(forged, 7) }.isFailure)
        val emptyContext = stored.toBuilder().setContextJson("{}")
            .setContextSha256(WaterHealthContextDocument.sha256("{}")).build()
        assertTrue(runCatching { WaterEvaluationCodec.decode(emptyContext, 7) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `legacy records do not acquire an empty or recomputed assessment`() {
        val legacy = WaterAnalysisRecord(1, "owner", 7, 1_800_000_000_000L, null, null,
            listOf(WaterMeasurementRecord(WaterParameter.PH, 7.0, WaterMeasurementMethod.MANUAL, null,
                input().measurements.single().selection.basis, input().measurements.single().selection.unit)),
            1_800_000_000_000L)
        assertFalse(legacy.toStoredStrict().hasEvaluation())
        assertNull(legacy.toStoredStrict().toRecordStrict().evaluation)
        assertNull(legacy.toApplicationSnapshot().assessment)
    }

    private fun input() = WaterAnalysisInput(7, 1_800_000_000_000, null, null,
        listOf(WaterMeasurementInput(WaterParameter.PH, 7.00001,
            WaterMeasurementCatalog.defaultSelection(WaterParameter.PH))))

    private fun context(maximum: Double = 7.0) = AquariumHealthContext(
        HealthContextCapture(1_800_000_000_000, "revision"),
        HealthTankFacts(7, "Freshwater Fish", "Freshwater", null, 54.0),
        HealthCatalogRevisions("plant-1", "animal-1"), emptyList(),
        listOf(HealthLivestockContext(1, "animal", "Animal", 2, HealthEntityResolution.RESOLVED, "Freshwater",
            LivestockWaterRequirements(ph = LivestockParameterRange(maximum = maximum)))), emptyList()
    )
}
