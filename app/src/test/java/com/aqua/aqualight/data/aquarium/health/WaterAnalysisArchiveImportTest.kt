package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementInput
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessmentEngine
import com.aqua.aqualight.data.user.archive.WaterHistoryArchive
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WaterAnalysisArchiveImportTest {
    @Test
    fun `owner tank event and request remap preserves frozen evidence and original identity`() {
        val source = source()
        val target = WaterAnalysisImportIdentity.remap(source, WaterAnalysisImportTarget("new-owner", 90, 99, TX))
        assertEquals("new-owner", target.ownerUid)
        assertEquals(90L, target.tankId)
        assertEquals(99L, target.id)
        assertNotEquals(source.requestId, target.requestId)
        assertEquals(source, WaterAnalysisImportIdentity.original(target))
        assertEquals(source.evaluation, target.evaluation)
        assertEquals(source.toRecordStrict().toApplicationSnapshot().assessment,
            target.toRecordStrict().toApplicationSnapshot().assessment)
        assertEquals(source.measuredAtMillis, target.measuredAtMillis)
        assertEquals(source.createdAtMillis, target.createdAtMillis)
    }

    @Test
    fun `reexport and second import preserve the first source evidence`() {
        val first = WaterAnalysisImportIdentity.remap(source(), WaterAnalysisImportTarget("B", 90, 99, TX))
        val second = WaterAnalysisImportIdentity.remap(first,
            WaterAnalysisImportTarget("C", 190, 199, UUID.randomUUID().toString()))
        assertEquals(source(), WaterAnalysisImportIdentity.original(second))
        assertEquals(first.evaluation, second.evaluation)
    }

    @Test
    fun `changed imported context or original identity fails validation`() {
        val imported = WaterAnalysisImportIdentity.remap(source(), WaterAnalysisImportTarget("B", 90, 99, TX))
        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisStoreRules.validateStoredAnalysis(imported.toBuilder().setMeasuredAtMillis(TIME + 1).build())
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisStoreRules.validateStoredAnalysis(imported.toBuilder().setImportOrigin(
                imported.importOrigin.toBuilder().setSourceAnalysisId(100)).build())
        }
    }

    @Test
    fun `same archive is idempotent and collision with different evidence fails atomically`() {
        val request = request(source())
        val original = WaterAnalysisStoreRules.defaultStore().toBuilder().addAnalyses(
            source().toBuilder().setOwnerUid("target").setTankId(500).clearEvaluation().build()).build()
        val first = WaterAnalysisArchiveImport.merge(original, request)
        assertEquals(1, first.second)
        assertEquals(2, first.first.analysesCount)
        assertEquals(original.analysesList.single(), first.first.analysesList.first())
        val retry = WaterAnalysisArchiveImport.merge(first.first, request)
        assertEquals(0, retry.second)
        assertEquals(first.first, retry.first)
        val changed = request(source().toBuilder().setMeasuredAtMillis(TIME + 1).build())
        assertThrows(IllegalArgumentException::class.java) { WaterAnalysisArchiveImport.merge(first.first, changed) }
        assertEquals(2, first.first.analysesCount)
    }

    private fun request(source: StoredWaterAnalysis): WaterHistoryRestoreRequest {
        val file = File(Files.createTempDirectory("water-import").toFile(), "history.bin")
        val reference = WaterHistoryArchive.write(sequenceOf(source), 1, file)
        return WaterHistoryRestoreRequest("target", TX, mapOf(7L to 90L), reference, file)
    }

    private fun source(): StoredWaterAnalysis {
        val input = WaterAnalysisInput(7, TIME, null, null,
            listOf(WaterMeasurementInput(WaterParameter.PH, 7.123456,
                WaterMeasurementCatalog.defaultSelection(WaterParameter.PH))))
        val context = AquariumHealthContext(HealthContextCapture(TIME, "old-context"),
            HealthTankFacts(7, "Freshwater Fish", "Freshwater", null, 54.0),
            HealthCatalogRevisions("old-plants", "old-livestock"), emptyList(), emptyList(), emptyList())
        return StoredWaterAnalysis.newBuilder().setId(1).setOwnerUid("original-owner").setTankId(7)
            .setMeasuredAtMillis(TIME).setCreatedAtMillis(TIME + 1).setRequestId(TX)
            .addMeasurements(StoredWaterMeasurement.newBuilder().setParameter("PH").setValue(7.123456)
                .setMethod("MANUAL").setBasis("PH").setUnit("NONE"))
            .setEvaluation(WaterEvaluationCodec.encode(
                input, context, WaterQualityAssessmentEngine.assess(input, context)))
            .build()
    }

    private companion object {
        const val TIME = 1_800_000_000_000L
        const val TX = "4e343e40-d04d-40f7-86df-b3fdf3854e62"
    }
}
