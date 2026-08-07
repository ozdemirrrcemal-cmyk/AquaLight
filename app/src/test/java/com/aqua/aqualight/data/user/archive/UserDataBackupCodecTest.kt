package com.aqua.aqualight.data.user.archive

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataBackupCodecTest {

    private val codec = UserDataBackupCodec()

    @Test
    fun `backup codec round trips a validated manifest and media`() {
        val photo = "image-bytes".toByteArray()
        val manifest = manifest(
            photo = ArchiveMediaReference(
                entryName = "media/tanks/7.jpg",
                byteSize = photo.size,
                sha256 = sha256(photo)
            )
        )

        val encoded = codec.encode(
            manifest = manifest,
            mediaByEntryName = mapOf("media/tanks/7.jpg" to photo)
        )
        val decoded = codec.decode(encoded)

        assertEquals(manifest, decoded.manifest)
        assertArrayEquals(photo, decoded.mediaByEntryName.getValue("media/tanks/7.jpg"))
    }

    @Test
    fun `decoder rejects a backup with an unsupported schema`() {
        val invalid = manifest().copy(schemaVersion = USER_DATA_BACKUP_SCHEMA_VERSION + 1)
        val encoded = rawZip(Gson().toJson(invalid))

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun `decoder rejects path traversal entries`() {
        val encoded = rawZip(
            manifestJson = Gson().toJson(manifest()),
            extraEntries = mapOf("../outside" to byteArrayOf(1))
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun `decoder rejects mismatched media integrity`() {
        val actualPhoto = "actual".toByteArray()
        val manifest = manifest(
            photo = ArchiveMediaReference(
                entryName = "media/tanks/7.jpg",
                byteSize = actualPhoto.size,
                sha256 = sha256("different".toByteArray())
            )
        )
        val encoded = rawZip(
            manifestJson = Gson().toJson(manifest),
            extraEntries = mapOf("media/tanks/7.jpg" to actualPhoto)
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun `decoder enforces the aggregate uncompressed archive limit`() {
        val photo = ByteArray(64) { index -> index.toByte() }
        val manifest = manifest(
            photo = ArchiveMediaReference(
                entryName = "media/tanks/7.jpg",
                byteSize = photo.size,
                sha256 = sha256(photo)
            )
        )
        val manifestJson = Gson().toJson(manifest)
        val aggregateSize = manifestJson.toByteArray(StandardCharsets.UTF_8).size + photo.size
        val constrainedCodec = UserDataBackupCodec(
            maxUncompressedArchiveBytes = aggregateSize - 1
        )
        val encoded = rawZip(
            manifestJson = manifestJson,
            extraEntries = mapOf("media/tanks/7.jpg" to photo)
        )

        assertThrows(IllegalArgumentException::class.java) {
            constrainedCodec.decode(encoded)
        }
    }

    @Test
    fun `backup manifest is owner neutral`() {
        val fields = ArchiveAquarium::class.java.declaredFields.map { field -> field.name }
        val assignmentFields = ArchiveDeviceAssignment::class.java.declaredFields
            .map { field -> field.name }

        assertFalse("ownerUid" in fields)
        assertFalse("ownerUid" in assignmentFields)
        assertTrue("deviceUid" in assignmentFields)
    }

    private fun manifest(photo: ArchiveMediaReference? = null): UserDataBackupManifest {
        return UserDataBackupManifest(
            format = USER_DATA_BACKUP_FORMAT,
            schemaVersion = USER_DATA_BACKUP_SCHEMA_VERSION,
            createdAtMillis = 1_000L,
            sourceAppVersion = "test",
            aquariums = listOf(
                ArchiveAquarium(
                    id = 7L,
                    name = "Display Tank",
                    description = "",
                    photo = photo,
                    setupDateEpochDay = null,
                    widthCm = 60,
                    lengthCm = 30,
                    heightCm = 36,
                    sizeUnit = "cm",
                    volumeUnit = "L",
                    tankType = "freshwater",
                    tankStyle = "nature",
                    createdAtMillis = 900L,
                    smartCareEnabled = true,
                    careRemindersEnabled = true,
                    plants = emptyList(),
                    materials = emptyList(),
                    livestock = emptyList()
                )
            ),
            careTasks = emptyList(),
            deviceAssignments = emptyList()
        )
    }

    private fun rawZip(
        manifestJson: String,
        extraEntries: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(UserDataBackupLimits.MANIFEST_ENTRY))
            zip.write(manifestJson.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            extraEntries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
