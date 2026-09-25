package com.aqua.aqualight.data.user.archive

import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
    fun `backup preview counts tank plant and livestock photos by record`() {
        val photo = ArchiveMediaReference("media/tanks/7.jpg", 12, "a".repeat(64))
        val aquarium = manifest(photo = photo, plantPhoto = photo).aquariums.single()
        val livestock = ArchiveLivestock(
            id = 19L, name = "Neon tetra", category = "fish", quantity = 2,
            addedDateEpochDay = null, note = "", catalogEntryId = "", photo = photo
        )
        val withPhotos = aquarium.copy(livestock = listOf(livestock, livestock.copy(id = 20L, photo = null)))

        assertEquals(3, manifest().copy(aquariums = listOf(withPhotos)).archivedPhotoCount())
    }

    @Test
    fun `backup codec round trips a validated manifest and media`() {
        val root = tempDirectory()
        val photo = "image-bytes".toByteArray()
        val photoFile = File(root, "photo.jpg").apply { writeBytes(photo) }
        val manifest = manifest(
            photo = ArchiveMediaReference(
                entryName = "media/tanks/7.jpg",
                byteSize = photo.size,
                sha256 = sha256(photo)
            )
        )
        val encoded = File(root, "backup.aqlbackup")
        val decodedMedia = File(root, "decoded")

        codec.encode(
            manifest = manifest,
            mediaByEntryName = mapOf("media/tanks/7.jpg" to photoFile),
            destination = encoded
        )
        val decoded = codec.decode(encoded, decodedMedia)

        assertEquals(manifest, decoded.manifest)
        assertArrayEquals(
            photo,
            decoded.mediaByEntryName.getValue("media/tanks/7.jpg").readBytes()
        )
    }

    @Test
    fun `backup codec round trips plant photo media`() {
        val root = tempDirectory()
        val photo = "plant-image-bytes".toByteArray()
        val photoFile = File(root, "plant-photo.jpg").apply { writeBytes(photo) }
        val entryName = "media/tanks/7_plant_11.jpg"
        val manifest = manifest(
            plantPhoto = ArchiveMediaReference(
                entryName = entryName,
                byteSize = photo.size,
                sha256 = sha256(photo)
            )
        )
        val encoded = File(root, "plant-backup.aqlbackup")
        val decodedMedia = File(root, "decoded-plant")

        codec.encode(
            manifest = manifest,
            mediaByEntryName = mapOf(entryName to photoFile),
            destination = encoded
        )
        val decoded = codec.decode(encoded, decodedMedia)

        assertEquals(manifest, decoded.manifest)
        assertArrayEquals(photo, decoded.mediaByEntryName.getValue(entryName).readBytes())
    }

    @Test
    fun `livestock photos round trip and mismatched record media names are rejected`() {
        val root = tempDirectory()
        val bytes = "livestock-photo".toByteArray()
        val file = File(root, "livestock.jpg").apply { writeBytes(bytes) }
        val entry = "media/tanks/7_livestock_42.jpg"
        val reference = ArchiveMediaReference(entry, bytes.size, sha256(bytes))
        val item = ArchiveLivestock(42, "Fish", "Fish", 4, null, "", "custom:42", reference)
        val original = manifest()
        val archive = original.copy(aquariums = listOf(original.aquariums.single().copy(livestock = listOf(item))))
        val zip = File(root, "livestock.aqlbackup")
        codec.encode(archive, mapOf(entry to file), zip)
        val decoded = codec.decode(zip, File(root, "livestock-decoded"))
        assertEquals(archive, decoded.manifest)
        assertArrayEquals(bytes, decoded.mediaByEntryName.getValue(entry).readBytes())
        val wrong = archive.copy(aquariums = listOf(archive.aquariums.single().copy(
            livestock = listOf(item.copy(id = 43, catalogEntryId = "custom:43"))
        )))
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(wrong, mapOf(entry to file), File(root, "wrong.aqlbackup"))
        }
    }

    @Test
    fun `decoder rejects a backup with an unsupported schema`() {
        val invalid = manifest().copy(schemaVersion = USER_DATA_BACKUP_SCHEMA_VERSION + 1)
        val encoded = rawZip(Gson().toJson(invalid))

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encoded, File(encoded.parentFile, "decoded-invalid-schema"))
        }
    }

    @Test
    fun `decoder rejects the previous backup schema without compatibility`() {
        val invalid = manifest().copy(schemaVersion = 1)
        val encoded = rawZip(Gson().toJson(invalid))

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encoded, File(encoded.parentFile, "decoded-previous-schema"))
        }
    }


    @Test
    fun `decoder rejects path traversal entries`() {
        val encoded = rawZip(
            manifestJson = Gson().toJson(manifest()),
            extraEntries = mapOf("../outside" to byteArrayOf(1))
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encoded, File(encoded.parentFile, "decoded-traversal"))
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
            codec.decode(encoded, File(encoded.parentFile, "decoded-mismatch"))
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
            constrainedCodec.decode(encoded, File(encoded.parentFile, "decoded-aggregate"))
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

    @Test
    fun `maximum photo count round trips including manifest entry`() {
        val root = tempDirectory()
        val (manifest, media) = plantPhotoArchive(root, UserDataBackupLimits.MAX_ZIP_ENTRIES - 1)
        val encoded = File(root, "maximum.aqlbackup")
        codec.encode(manifest, media, encoded)
        val decoded = codec.decode(encoded, File(root, "decoded-maximum"))
        assertEquals(manifest, decoded.manifest)
        assertEquals(media.keys, decoded.mediaByEntryName.keys)
    }

    @Test
    fun `writer rejects photo count that reader cannot restore`() {
        val root = tempDirectory()
        val (manifest, media) = plantPhotoArchive(root, UserDataBackupLimits.MAX_ZIP_ENTRIES)
        val encoded = File(root, "overflow.aqlbackup")
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(manifest, media, encoded)
        }
        assertFalse(encoded.exists())
        val raw = rawZip(Gson().toJson(manifest), media.mapValues { it.value.readBytes() })
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(raw, File(root, "decoded-overflow"))
        }
    }

    @Test
    fun `writer enforces reader uncompressed limit before exporting`() {
        val root = tempDirectory()
        val (manifest, media) = plantPhotoArchive(root, 2)
        val bytes = Gson().toJson(manifest).toByteArray(StandardCharsets.UTF_8).size +
            media.values.sumOf(File::length).toInt()
        val encoded = File(root, "too-large.aqlbackup")
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec(maxUncompressedArchiveBytes = bytes - 1)
                .encode(manifest, media, encoded)
        }
        assertFalse(encoded.exists())
    }

    private fun plantPhotoArchive(
        root: File,
        count: Int
    ): Pair<UserDataBackupManifest, Map<String, File>> {
        val bytes = "small-plant-photo".toByteArray()
        val file = File(root, "source.jpg").apply { writeBytes(bytes) }
        val media = linkedMapOf<String, File>()
        val base = manifest()
        val aquariums = (1..count).chunked(UserDataBackupLimits.MAX_ITEMS_PER_AQUARIUM)
            .mapIndexed { tankIndex, plantIds ->
                val tankId = 7L + tankIndex
                val plants = plantIds.map { id ->
                    val entryName = "media/tanks/${tankId}_plant_$id.jpg"
                    media[entryName] = file
                    ArchivePlant(
                        id = id.toLong(), catalogId = "plant:anubias_barteri",
                        plantName = "Anubias", category = "Epiphyte", markerX = 0.5f, markerY = 0.5f,
                        photo = ArchiveMediaReference(entryName, bytes.size, sha256(bytes))
                    )
                }
                base.aquariums.single().copy(id = tankId, plants = plants)
            }
        return base.copy(aquariums = aquariums) to media
    }

    private fun manifest(
        photo: ArchiveMediaReference? = null,
        plantPhoto: ArchiveMediaReference? = null
    ): UserDataBackupManifest {
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
                    plants = if (plantPhoto == null) {
                        emptyList()
                    } else {
                        listOf(
                            ArchivePlant(
                                id = 11L,
                                catalogId = "plant:anubias_barteri",
                                plantName = "Anubias",
                                category = "Epiphyte",
                                markerX = 0.25f,
                                markerY = 0.75f,
                                photo = plantPhoto
                            )
                        )
                    },
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
    ): File {
        val root = tempDirectory()
        val output = File(root, "raw.aqlbackup")
        ZipOutputStream(output.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(UserDataBackupLimits.MANIFEST_ENTRY))
            zip.write(manifestJson.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            extraEntries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output
    }

    private fun tempDirectory(): File {
        return Files.createTempDirectory("aql-user-data-codec-").toFile().apply {
            deleteOnExit()
        }
    }
}
