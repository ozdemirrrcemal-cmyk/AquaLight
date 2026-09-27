package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.observation.StoredHealthObservation
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthHistoryBackupCodecTest {
    private val codec = UserDataBackupCodec()

    @Test
    fun backupAndPortableExportPreserveObservationEvidenceAndPhotoOwnership() {
        val root = Files.createTempDirectory("health-backup").toFile()
        val image = File(root, "image.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val source = HealthArchiveTestRows.row(photo = "content://original/photo")
        val photo = ArchivedObservationPhoto(source.id, 7,
            ArchiveMediaReference(HealthHistoryArchive.photoEntry(7, source.id), 3, sha256(image)))
        val stream = File(root, "health.bin")
        val reference = HealthHistoryArchive.write(sequenceOf(source), 1, stream, listOf(photo))
        val manifest = RestoreFixture.backup().manifest.copy(healthHistory = reference)
        val archive = File(root, "archive.aqlbackup")
        codec.encode(manifest, mapOf(photo.media.entryName to image), archive, healthHistoryFile = stream)
        val decoded = codec.decode(archive, File(root, "decoded"))
        assertEquals(reference, decoded.manifest.healthHistory)
        assertEquals(1, decoded.manifest.archivedPhotoCount())
        HealthHistoryArchive.visit(requireNotNull(decoded.healthHistoryFile), 1) { assertEquals(source, it) }
        assertTrue(image.readBytes().contentEquals(
            decoded.mediaByEntryName.getValue(photo.media.entryName).readBytes()))
        val json = StringWriter()
        val writer = com.google.gson.stream.JsonWriter(json)
        writer.beginObject()
        HealthHistoryPortableWriter.write(writer, reference to stream)
        writer.endObject().close()
        val row = JsonParser.parseString(json.toString()).asJsonObject
            .getAsJsonArray("healthObservations")[0].asJsonObject
        assertEquals("PLANT", row.get("kind").asString)
        assertEquals("Original plant", row.getAsJsonObject("subject").get("displayName").asString)
        assertFalse(row.get("photoIncluded").asBoolean)
        assertEquals(source, StoredHealthObservation.parseFrom(
            Base64.getDecoder().decode(row.get("wireBase64").asString)))
        image.writeBytes(byteArrayOf(9, 9, 9))
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(manifest, mapOf(photo.media.entryName to image), archive, healthHistoryFile = stream)
        }
    }

    @Test
    fun versionThreeRetainsWaterHistoryWithoutInventingHealthRecords() {
        val root = Files.createTempDirectory("health-legacy").toFile()
        val legacy = RestoreFixture.backup().manifest.copy(schemaVersion = 3, healthHistory = null)
        val archive = zip(root, Gson().toJson(legacy), health = false)
        val decoded = codec.decode(archive, File(root, "decoded"))
        assertEquals(WaterHistoryArchive.emptyReference, decoded.manifest.waterHistory)
        assertEquals(null, decoded.manifest.healthHistory)
        assertEquals(null, decoded.healthHistoryFile)
        assertEquals(4, decoded.manifest.schemaVersion)
    }

    @Test
    fun missingStreamFractionalCountAndLegacyObservationInjectionAreRejected() {
        val root = Files.createTempDirectory("health-invalid").toFile()
        val current = Gson().toJsonTree(RestoreFixture.backup().manifest).asJsonObject
        val missingStream = zip(root, current.toString(), health = false)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(missingStream, File(root, "missing")) }
        current.getAsJsonObject("healthHistory").addProperty("recordCount", 0.5)
        val fractional = zip(root, current.toString(), health = true)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(fractional, File(root, "fractional")) }
        val injected = RestoreFixture.backup().manifest.copy(schemaVersion = 3)
        val legacy = zip(root, Gson().toJson(injected), health = true)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(legacy, File(root, "injected")) }
    }

    private fun zip(root: File, manifest: String, health: Boolean): File = File(root, "raw.aqlbackup").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            val entries = linkedMapOf(UserDataBackupLimits.MANIFEST_ENTRY to manifest.toByteArray(),
                WaterHistoryArchive.ENTRY to WaterHistoryArchive.emptyBytes)
            if (health) entries[HealthHistoryArchive.ENTRY] = HealthHistoryArchive.emptyBytes
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
}
