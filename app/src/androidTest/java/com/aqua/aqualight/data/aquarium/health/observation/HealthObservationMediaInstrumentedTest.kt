package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.media.AppMediaRecoveryManager
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthObservationMediaInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun candidateRequiresExactOwnerAndScopeAndCannotBeReadoptedAfterCommit() {
        val owner = "health-media-${UUID.randomUUID()}"
        val photo = pending(owner)
        val media = HealthObservationMedia(context)
        try {
            media.requireCandidate(owner, photo)
            assertThrows(IllegalArgumentException::class.java) { media.requireCandidate("other-owner", photo) }
            assertNull(AppMediaStorage.pendingMediaOwner(context, photo, AppMediaScope.PLANT))
            media.committed(photo)
            assertThrows(IllegalArgumentException::class.java) { media.requireCandidate(owner, photo) }
        } finally {
            media.deleted(owner, photo)
        }
    }

    @Test
    fun processRecoveryRetainsCommittedAndRollbackStagedPhotosThenRemovesDeletedHistoryMedia() = runBlocking {
        val owner = "health-recovery-${UUID.randomUUID()}"
        val photo = pending(owner)
        val database = HealthObservationDatabase.getInstance(context)
        val maintenance = HealthObservationRoomMaintenance(database)
        val prepared = HealthRoomFixture.prepared().let { value ->
            value.copy(input = value.input.copy(notes = value.input.notes.copy(photoUri = photo)))
        }
        try {
            // Cross the existing orphan grace window. A staged record must protect even an old photo.
            val photoFile = requireNotNull(AppMediaStorage.resolveInternalMediaFile(context, photo))
            assertTrue(photoFile.setLastModified(System.currentTimeMillis() - TWO_DAYS_MILLIS))
            HealthObservationRoomCommit(database).create(owner, prepared, HealthRoomFixture.TIME) { }
            val transaction = UUID.randomUUID().toString()
            maintenance.prepare(owner, HealthRoomFixture.TANK, transaction)
            maintenance.remove(owner, HealthRoomFixture.TANK, allowUnstaged = false)
            AppMediaRecoveryManager(context).reconcileOwner(owner)
            assertNull(AppMediaStorage.pendingMediaOwner(context, photo, AppMediaScope.HEALTH))
            assertNotNull(AppMediaStorage.resolveInternalMediaFile(context, photo)?.takeIf(File::isFile))
            maintenance.restore(owner, HealthRoomFixture.TANK, transaction)
            maintenance.prepare(owner, HealthRoomFixture.TANK, transaction)
            maintenance.remove(owner, HealthRoomFixture.TANK, allowUnstaged = false)
            maintenance.finish(owner, HealthRoomFixture.TANK, transaction)
            AppMediaRecoveryManager(context).reconcileOwner(owner)
            assertNull(AppMediaStorage.resolveInternalMediaFile(context, photo)?.takeIf(File::isFile))
        } finally {
            maintenance.clearOwner(owner)
            AppMediaStorage.deleteAfterCommit(context, owner, photo)
        }
    }

    private fun pending(owner: String): String {
        val scope = AppMediaScope.HEALTH
        val crop = requireNotNull(AppMediaStorage.createCropOutputUri(context, scope, "observation"))
        File(requireNotNull(crop.path)).writeBytes(byteArrayOf(1, 2, 3))
        return requireNotNull(AppMediaStorage.promoteCropOutput(context, scope, "observation", owner, crop)).toString()
    }

    private companion object { const val TWO_DAYS_MILLIS = 2L * 24L * 60L * 60L * 1000L }
}
