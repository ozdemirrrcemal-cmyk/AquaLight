package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.StoredWaterAnalysis
import com.aqua.aqualight.data.aquarium.health.StoredWaterMeasurement
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WaterHistoryArchiveTest {
    @Test
    fun `ten thousand events round trip exact bytes without loading a second event list`() {
        val file = tempFile()
        val count = 10_000
        val reference = WaterHistoryArchive.write((1..count).asSequence().map { event(it.toLong()) }, count, file)
        WaterHistoryArchive.validate(reference, file, setOf(7))
        var seen = 0
        WaterHistoryArchive.visit(file, count) { actual ->
            seen += 1
            assertEquals(event(seen.toLong()), actual)
        }
        assertEquals(count, seen)
    }

    @Test
    fun `zero events have an explicit validated stream`() {
        val file = tempFile()
        val reference = WaterHistoryArchive.write(emptySequence(), 0, file)
        assertEquals(WaterHistoryArchive.emptyReference, reference)
        WaterHistoryArchive.validate(reference, file, emptySet())
    }

    @Test
    fun `checksum count trailing bytes and truncated record fail before import`() {
        val file = tempFile()
        val reference = WaterHistoryArchive.write(sequenceOf(event()), 1, file)
        assertThrows(IllegalArgumentException::class.java) {
            WaterHistoryArchive.validate(reference.copy(sha256 = "0".repeat(64)), file, setOf(7))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaterHistoryArchive.validate(reference.copy(recordCount = 2), file, setOf(7))
        }
        file.appendBytes(byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) {
            WaterHistoryArchive.validate(
                reference.copy(byteSize = file.length(), sha256 = sha256(file)), file, setOf(7))
        }
        file.writeBytes(file.readBytes().dropLast(3).toByteArray())
        assertThrows(java.io.EOFException::class.java) { WaterHistoryArchive.visit(file, 1) {} }
    }

    @Test
    fun `missing tank duplicate identity and mixed owners are rejected`() {
        val cases = listOf(
            listOf(event()) to emptySet<Long>(),
            listOf(event(), event()) to setOf(7L),
            listOf(event(), event(2).toBuilder().setOwnerUid("other").build()) to setOf(7L)
        )
        cases.forEach { (records, tanks) ->
            val file = tempFile()
            val reference = WaterHistoryArchive.write(records.asSequence(), records.size, file)
            assertThrows(IllegalArgumentException::class.java) {
                WaterHistoryArchive.validate(reference, file, tanks)
            }
        }
    }

    @Test
    fun `failed snapshot removes partial artifact`() {
        val file = tempFile()
        assertThrows(IllegalArgumentException::class.java) {
            WaterHistoryArchive.write(sequenceOf(event()), 2, file)
        }
        assertFalse(file.exists())
    }

    private fun tempFile(): File = File(Files.createTempDirectory("water-archive").toFile(), "history.bin")

    private fun event(id: Long = 1): StoredWaterAnalysis = StoredWaterAnalysis.newBuilder()
        .setId(id).setOwnerUid("owner").setTankId(7).setMeasuredAtMillis(TIME).setCreatedAtMillis(TIME)
        .addMeasurements(StoredWaterMeasurement.newBuilder().setParameter("PH").setValue(0.0)
            .setMethod("MANUAL").setBasis("PH").setUnit("NONE")).build()

    private companion object { const val TIME = 1_800_000_000_000L }
}
