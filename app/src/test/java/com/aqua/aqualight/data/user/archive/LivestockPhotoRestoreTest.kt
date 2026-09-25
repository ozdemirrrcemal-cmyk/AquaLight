package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.user.UserDataScope
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockPhotoRestoreTest {
    @Test
    fun restoreRemapsTankButKeepsPhotoOnTheCorrectLivestockAndIsRepeatable() = runBlocking {
        val prepared = mutableListOf<String>()
        val committed = mutableListOf<String?>()
        val media = operations(prepared, committed)
        val harness = RestoreHarness(media)
        val backup = backup()
        UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
            harness.restorer().restore(backup)
            val tank = harness.tanks.single()
            assertTrue(tank.id != RestoreFixture.SOURCE_TANK_ID)
            assertEquals(42L, tank.livestock.single().id)
            assertEquals("local-livestock-photo", tank.livestock.single().photoUri)
            assertEquals(listOf("restore_7_livestock_42"), prepared)
            assertEquals(listOf("local-livestock-photo"), committed)
            harness.restorer().restore(backup)
            assertEquals(1, harness.tanks.size)
            assertEquals(1, prepared.size)
        }
    }

    @Test
    fun failedLivestockPhotoRestoreRollsBackCreatedTank() = runBlocking {
        val media = operations(mutableListOf(), mutableListOf()).copy(
            livestock = LivestockRestoreMedia(prepare = { _, _, _ -> error("Unreadable image") })
        )
        val harness = RestoreHarness(media)
        UserDataScope.withOwnerUid(RestoreFixture.OWNER_UID) {
            assertTrue(runCatching { harness.restorer().restore(backup()) }.isFailure)
            assertTrue(harness.tanks.isEmpty())
            assertEquals(null, harness.transactions.pending(RestoreFixture.OWNER_UID))
        }
    }

    private fun operations(prepared: MutableList<String>, committed: MutableList<String?>) =
        UserDataRestoreMediaOperations(
            snapshotTankPhoto = { null },
            prepareRestoredTankPhoto = { _, _, _ -> error("Unexpected tank photo") },
            commit = { committed.add(it); Unit }, rollback = {},
            livestock = LivestockRestoreMedia(prepare = { owner, token, _ ->
                assertEquals(RestoreFixture.OWNER_UID, owner)
                prepared += token
                "local-livestock-photo"
            })
        )

    private fun backup(): DecodedUserDataBackup {
        val original = RestoreFixture.backup()
        val file = File(Files.createTempDirectory("livestock-restore").toFile(), "photo.jpg")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entry = "media/tanks/7_livestock_42.jpg"
        val item = ArchiveLivestock(42, "Fish", "Fish", 4, null, "", "custom:42",
            ArchiveMediaReference(entry, 3, sha256(file)))
        return original.copy(
            manifest = original.manifest.copy(aquariums = listOf(
                original.manifest.aquariums.single().copy(livestock = listOf(item))
            )),
            mediaByEntryName = mapOf(entry to file)
        )
    }
}
