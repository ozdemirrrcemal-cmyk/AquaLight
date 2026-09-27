package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationImportIdentity
import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationImportTarget
import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationRecordRules
import com.aqua.aqualight.data.aquarium.health.observation.StoredHealthObservation
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HealthHistoryArchiveTest {
    @Test
    fun streamRetainsEveryObservationAndRejectsWrongOwnerTankOrChecksum() {
        val file = tempFile()
        val rows = (1L..125L).map { HealthArchiveTestRows.row(it) }
        val reference = HealthHistoryArchive.write(rows.asSequence(), rows.size, file, emptyList())
        HealthHistoryArchive.validate(reference, file, setOf(HealthArchiveTestRows.TANK))
        val reopened = mutableListOf<StoredHealthObservation>()
        HealthHistoryArchive.visit(file, rows.size, reopened::add)
        assertEquals(rows, reopened)
        assertThrows(IllegalArgumentException::class.java) {
            HealthHistoryArchive.validate(reference, file, emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            HealthHistoryArchive.validate(reference.copy(sha256 = "0".repeat(64)), file, setOf(7))
        }
        val mixed = sequenceOf(rows.first(), rows.last().toBuilder().setOwnerUid("foreign").build())
        val invalid = HealthHistoryArchive.write(mixed, 2, file, emptyList())
        assertThrows(IllegalArgumentException::class.java) { HealthHistoryArchive.validate(invalid, file, setOf(7)) }
    }

    @Test
    fun photoOwnershipDeclarationMustMatchTheExactObservation() {
        val file = tempFile()
        val row = HealthArchiveTestRows.row(photo = "content://original/photo")
        val photo = ArchivedObservationPhoto(row.id, 7,
            ArchiveMediaReference(HealthHistoryArchive.photoEntry(7, row.id), 10, "a".repeat(64)))
        val reference = HealthHistoryArchive.write(sequenceOf(row), 1, file, listOf(photo))
        HealthHistoryArchive.validate(reference, file, setOf(7))
        listOf(emptyList(), listOf(photo.copy(observationId = 2)), listOf(photo, photo)).forEach { photos ->
            assertThrows(IllegalArgumentException::class.java) {
                HealthHistoryArchive.validate(reference.copy(photos = photos), file, setOf(7))
            }
        }
    }

    @Test
    fun remappingAndSecondExportPreserveOriginalEvidenceAndRejectTampering() {
        val source = HealthArchiveTestRows.row(photo = "content://source/photo")
        val imported = HealthObservationImportIdentity.remap(source, target("target-owner", 20, 10))
        val twice = HealthObservationImportIdentity.remap(imported, target("third-owner", 30, 11))
        assertEquals(source, HealthObservationImportIdentity.original(imported))
        assertEquals(source, HealthObservationImportIdentity.original(twice))
        assertEquals(source.evidence, twice.evidence)
        assertEquals(source.subject, twice.subject)
        assertNotEquals(source.input.photoUri, twice.input.photoUri)
        assertThrows(IllegalArgumentException::class.java) {
            HealthObservationRecordRules.validate(twice.toBuilder().setInput(twice.input.toBuilder()
                .setNote("rewritten evidence")).build())
        }
    }

    @Test
    fun incompleteOrTrailingStreamNeverBecomesAValidBackup() {
        val file = tempFile()
        assertThrows(IllegalArgumentException::class.java) {
            HealthHistoryArchive.write(sequenceOf(HealthArchiveTestRows.row()), 2, file, emptyList())
        }
        assertFalse(file.exists())
        val reference = HealthHistoryArchive.write(emptySequence(), 0, file, emptyList())
        assertEquals(HealthHistoryArchive.emptyReference, reference)
        file.appendBytes(byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) {
            HealthHistoryArchive.validate(reference.copy(byteSize = file.length(), sha256 = sha256(file)),
                file, emptySet())
        }
    }

    private fun target(owner: String, tank: Long, id: Long) = HealthObservationImportTarget(
        owner, tank, id, UUID.randomUUID().toString(), "content://$owner/new-photo", null)

    private fun tempFile() = File(Files.createTempDirectory("health-archive").toFile(), "health.bin")
}
