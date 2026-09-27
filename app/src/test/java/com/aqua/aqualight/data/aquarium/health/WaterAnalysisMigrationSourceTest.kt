package com.aqua.aqualight.data.aquarium.health

import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterAnalysisMigrationSourceTest {
    @Test
    fun tenThousandRowsAreOwnerScopedBoundedAndReplayable() {
        val rows = (1..RECORD_COUNT).map { record(it.toLong()) }
        val otherOwner = record(1).toBuilder().setOwnerUid("another-owner").build()
        val source = source(rows.reversed() + otherOwner)
        var checkpoint = 0L
        var count = 0
        val restored = mutableListOf<StoredWaterAnalysis>()
        while (count < RECORD_COUNT) {
            val batch = source.batchAfter(checkpoint)
            assertEquals(batch, source.batchAfter(checkpoint))
            assertEquals(WaterAnalysisMigrationSource.BATCH_SIZE, batch.size)
            assertTrue(batch.all { it.ownerUid == OWNER })
            restored.addAll(batch)
            checkpoint = batch.last().id
            count += batch.size
        }
        assertEquals(rows, restored)
        assertEquals(RECORD_COUNT, source.manifest.recordCount)
        assertTrue(source.batchAfter(checkpoint).isEmpty())
        source.verify(restored.asSequence())
    }

    @Test
    fun missingExtraDuplicateOutOfOrderAndForeignRowsCannotVerify() {
        val rows = listOf(record(1), record(2))
        val source = source(rows)
        val invalid = listOf(
            emptyList(), rows.dropLast(1), rows + record(3), rows + rows.last(), rows.reversed(),
            rows.map { it.toBuilder().setOwnerUid("another-owner").build() }
        )
        invalid.forEach { changed ->
            assertThrows(WaterAnalysisMigrationMismatch::class.java) { source.verify(changed.asSequence()) }
        }
    }

    @Test
    fun changedTimesIdentityRawValueSourceAndPresenceCannotVerify() {
        val row = record(1)
        val source = source(listOf(row))
        val changes = listOf(
            row.toBuilder().setTankId(row.tankId + 1),
            row.toBuilder().setMeasuredAtMillis(row.measuredAtMillis + 1),
            row.toBuilder().setCreatedAtMillis(row.createdAtMillis + 1),
            row.toBuilder().setRequestId("123e4567-e89b-12d3-a456-426614174000"),
            row.toBuilder().setHasTemperature(true).setTemperatureCelsius(1.0).setTemperatureSource("MANUAL"),
            row.toBuilder().setMeasurements(0, row.getMeasurements(0).toBuilder().setValue(1.0)),
            row.toBuilder().setMeasurements(0, row.getMeasurements(0).toBuilder().setMethod("DIGITAL"))
        )
        changes.forEach { changed ->
            assertThrows(WaterAnalysisMigrationMismatch::class.java) { source.verify(sequenceOf(changed.build())) }
        }
    }

    @Test
    fun originalFileFingerprintChangesWhileRecordChecksumIgnoresStoreOrdering() {
        val rows = listOf(record(1), record(2))
        val forward = source(rows)
        val reversed = source(rows.reversed())
        assertNotEquals(forward.manifest.sourceSha256, reversed.manifest.sourceSha256)
        assertEquals(forward.manifest.recordsSha256, reversed.manifest.recordsSha256)
        val legacy = read(store(rows).toBuilder().setSchemaVersion(1).build())
        assertNotEquals(forward.manifest.sourceSha256, legacy.manifest.sourceSha256)
        assertEquals(forward.manifest.recordsSha256, legacy.manifest.recordsSha256)
    }

    @Test
    fun unknownRecordFieldsMustSurviveAndEmptyOwnerDoesNotAdoptOtherOwners() {
        val row = record(1)
        val bytes = ByteArrayOutputStream().apply { write(row.toByteArray()) }
        CodedOutputStream.newInstance(bytes).also {
            it.writeString(UNKNOWN_FIELD, "historical metadata")
            it.flush()
        }
        val enriched = StoredWaterAnalysis.parseFrom(bytes.toByteArray())
        val source = source(listOf(enriched))
        source.verify(sequenceOf(enriched))
        assertThrows(WaterAnalysisMigrationMismatch::class.java) { source.verify(sequenceOf(row)) }
        val absent = WaterAnalysisMigrationSource.readFrom(
            ByteArrayInputStream(store(listOf(row)).toByteArray()), "absent-owner"
        )
        assertEquals(0, absent.manifest.recordCount)
        assertTrue(absent.batchAfter(0).isEmpty())
        absent.verify(emptySequence())
        assertThrows(WaterAnalysisMigrationMismatch::class.java) { absent.verify(sequenceOf(row)) }
    }

    @Test
    fun checkpointBoundariesAndReturnedBatchesAreSafe() {
        val source = source(listOf(record(2), record(4)))
        assertEquals(listOf(record(4)), source.batchAfter(3))
        assertTrue(source.batchAfter(Long.MAX_VALUE).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { source.batchAfter(-1) }
        assertThrows(UnsupportedOperationException::class.java) {
            (source.batchAfter(0) as MutableList<StoredWaterAnalysis>).clear()
        }
        assertEquals(listOf(record(2), record(4)), source.batchAfter(0))
    }

    private fun record(id: Long): StoredWaterAnalysis = WaterAnalysisLegacyFixture.record().toBuilder()
        .setId(id).build()

    private fun store(rows: List<StoredWaterAnalysis>): WaterAnalysesStore =
        WaterAnalysisLegacyFixture.store().toBuilder().clearAnalyses().addAllAnalyses(rows).build()

    private fun source(rows: List<StoredWaterAnalysis>): WaterAnalysisMigrationSource = read(store(rows))

    private fun read(store: WaterAnalysesStore): WaterAnalysisMigrationSource =
        WaterAnalysisMigrationSource.readFrom(ByteArrayInputStream(store.toByteArray()), OWNER)

    private companion object {
        const val OWNER = "legacy-owner"
        const val RECORD_COUNT = 10_000
        const val UNKNOWN_FIELD = 127
    }
}
