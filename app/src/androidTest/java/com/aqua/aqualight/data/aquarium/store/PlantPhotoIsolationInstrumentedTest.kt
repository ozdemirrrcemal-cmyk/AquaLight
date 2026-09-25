package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlantPhotoIsolationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AquariumTankDataStoreManager(context)

    @Test
    fun sameSpeciesAndSamePlantIdsInOtherTankNeverChangeTheSelectedRecord() = runBlocking {
        val owner = "plant-target-${UUID.randomUUID()}"
        UserDataScope.withOwnerUid(owner) {
            val draft = draft()
            val first = store.addTankFromDraft(draft)
            val second = store.addTankFromDraft(draft)
            try {
                val photo = pending(owner)
                assertNull(store.updatePlantPhoto(first, 12L, photo))
                AppMediaStorage.commitPendingMedia(context, photo)
                // A stale editor reorders plants after the photo was saved.
                store.updateTankPlants(first, draft.plants.reversed())
                val tanks = store.tanksSnapshotForOwner(owner).associateBy { it.id }
                val selected = tanks.getValue(first).plants.associateBy { it.id }
                assertEquals(photo, selected.getValue(12L).photoUri)
                assertNull(selected.getValue(11L).photoUri)
                assertTrue(tanks.getValue(second).plants.all { it.photoUri == null })
                assertEquals(photo, store.updatePlantPhoto(first, 12L, null))
                assertTrue(store.tanksSnapshotForOwner(owner).all { tank ->
                    tank.plants.all { it.photoUri == null }
                })
                AppMediaStorage.deleteInternalMedia(context, photo)
            } finally {
                store.deleteTanks(listOf(first, second))
                AppMediaStorage.discardPendingMediaForOwner(context, owner)
            }
        }
    }

    @Test
    fun foreignOwnerWrongScopeMissingPlantAndSharedFileAreRejected() = runBlocking {
        val owner = "plant-owner-${UUID.randomUUID()}"
        val other = "${owner}_other"
        val foreign = pending(other)
        val wrongScope = pending(owner, AppMediaScope.TANK)
        UserDataScope.withOwnerUid(owner) {
            val tankId = store.addTankFromDraft(draft())
            try {
                assertTrue(runCatching { store.updatePlantPhoto(tankId, 11L, foreign) }.isFailure)
                assertTrue(runCatching { store.updatePlantPhoto(tankId, 11L, wrongScope) }.isFailure)
                val own = pending(owner)
                assertTrue(runCatching { store.updatePlantPhoto(tankId, 999L, own) }.isFailure)
                assertTrue(AppMediaStorage.isAppOwned(context, foreign))
                assertTrue(AppMediaStorage.isAppOwned(context, own))
                store.updatePlantPhoto(tankId, 11L, own)
                // Even before the pending journal is committed, a second record cannot share it.
                assertTrue(runCatching { store.updatePlantPhoto(tankId, 12L, own) }.isFailure)
                AppMediaStorage.commitPendingMedia(context, own)
                assertTrue(runCatching { store.updatePlantPhoto(tankId, 12L, own) }.isFailure)
                assertNull(store.updatePlantPhoto(tankId, 11L, own))
                UserDataScope.withOwnerUid(other) {
                    assertTrue(runCatching { store.updatePlantPhoto(tankId, 11L, foreign) }.isFailure)
                }
                val plants = store.tanksSnapshotForOwner(owner).single().plants
                assertEquals(own, plants.first { it.id == 11L }.photoUri)
                assertNull(plants.first { it.id == 12L }.photoUri)
            } finally {
                store.deleteTanks(listOf(tankId))
                AppMediaStorage.discardPendingMediaForOwner(context, owner)
                AppMediaStorage.discardPendingMediaForOwner(context, other)
            }
        }
    }

    private fun pending(owner: String, scope: AppMediaScope = AppMediaScope.PLANT): String {
        val crop = requireNotNull(AppMediaStorage.createCropOutputUri(context, scope, "7"))
        File(requireNotNull(crop.path)).writeBytes(byteArrayOf(1, 2, 3))
        return requireNotNull(AppMediaStorage.promoteCropOutput(context, scope, "7", owner, crop))
            .toString()
    }

    private fun draft() = TankDraft(
        name = "Photo test", widthCm = 60, lengthCm = 40, heightCm = 40,
        tankType = "Planted", plants = listOf(11L, 12L).map { id ->
            TankPlantTag(id, "plant:anubias_barteri", "Anubias", "Epiphyte")
        }
    )
}
