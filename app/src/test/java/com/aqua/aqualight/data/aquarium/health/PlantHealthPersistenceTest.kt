package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.DataStore
import com.aqua.aqualight.application.aquarium.health.PlantObservationInput
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantHealthPersistenceTest {
    private val store = MemoryStore()
    private val media = RecordingMedia()
    private var plants = mapOf(1L to setOf(2L))
    private val manager = PlantHealthDataStoreManager(store, { plants }, media, { 100L })

    @Test fun retryPersistsOnlyOneRecordAndKeepsItsOriginalTimestamp() = runBlocking {
        UserDataScope.withOwnerUid("owner-a") {
            val id = manager.create(input())
            assertEquals(id, manager.create(input()))
            val records = manager.observationsForPlant(1L, 2L).first()
            assertEquals(1, records.size)
            assertEquals("note", records.single().note)
            assertEquals(100L, records.single().createdAtMillis)
            assertFails { manager.create(input().copy(note = "different")) }
        }
    }

    @Test fun ownersCannotReadOrDeleteEachOthersRecords() = runBlocking {
        val id = UserDataScope.withOwnerUid("owner-a") { manager.create(input()) }
        UserDataScope.withOwnerUid("owner-b") {
            assertTrue(manager.observationsForPlant(1L, 2L).first().isEmpty())
            manager.deleteObservation(1L, 2L, id)
            manager.create(input())
        }
        UserDataScope.withOwnerUid("owner-a") {
            assertEquals(listOf(id), manager.observationsForPlant(1L, 2L).first().map { it.id })
        }
        assertEquals(2, store.data.value.observationsCount)
    }

    @Test fun missingPlantAndForeignPhotoAreRejectedBeforePersistence() = runBlocking {
        UserDataScope.withOwnerUid("owner-a") {
            assertFails { manager.create(input().copy(plantId = 9L)) }
            assertFails { manager.create(input().copy(photoUris = listOf("foreign"))) }
        }
        assertEquals(0, store.data.value.observationsCount)
    }

    @Test fun deletingMetadataPreparesPhotoCleanupBeforeRemovingReferences() = runBlocking {
        UserDataScope.withOwnerUid("owner-a") {
            val id = manager.create(input().copy(photoUris = listOf("owned")))
            manager.discardDraftPhotos(listOf("owned", "unused"))
            assertEquals(listOf("unused"), media.rolledBack)
            manager.deleteObservation(1L, 2L, id)
            assertEquals(listOf("owned"), media.prepared)
            assertEquals(listOf("owned"), media.deleted)
            assertTrue(manager.observationsForPlant(1L, 2L).first().isEmpty())
        }
    }

    @Test fun plantAndTankRemovalCleanTheirRecordsAndPhotos() = runBlocking {
        UserDataScope.withOwnerUid("owner-a") {
            manager.create(input().copy(photoUris = listOf("owned")))
            plants = mapOf(1L to emptySet())
            manager.reconcileTankPlants(1L)
            assertEquals(listOf("owned"), media.deleted)
            assertEquals(0, store.data.value.observationsCount)
            plants = mapOf(1L to setOf(2L))
            manager.create(input())
            manager.deleteForTank(1L)
            assertEquals(0, store.data.value.observationsCount)
        }
    }

    @Test fun startupRecoveryAndAccountCleanupRespectOtherOwners() = runBlocking {
        UserDataScope.withOwnerUid("owner-a") { manager.create(input()) }
        UserDataScope.withOwnerUid("owner-b") { manager.create(input()) }
        manager.clearAllForOwner("owner-a")
        assertEquals("owner-b", store.data.value.observationsList.single().ownerUid)
        plants = emptyMap()
        assertTrue(manager.reconcileAndGetMediaUrisForOwner("owner-b").isEmpty())
        assertEquals(0, store.data.value.observationsCount)
    }

    @Test fun committedRecordSurvivesPhotoCommitFailureAndCanBeRetried() = runBlocking {
        UserDataScope.withOwnerUid("owner-a") {
            media.failCommit = true
            val request = input().copy(photoUris = listOf("owned"))
            assertFails { manager.create(request) }
            assertEquals(1, store.data.value.observationsCount)
            manager.discardDraftPhotos(request.photoUris)
            assertFalse("owned" in media.rolledBack)
            media.failCommit = false
            manager.create(request)
            assertEquals(1, store.data.value.observationsCount)
        }
    }

    @Test fun deletingPlantDuringSaveDoesNotLeaveAnOrphanedRecord() = runBlocking {
        val racing = PlantHealthDataStoreManager(store, { plants }, media, { plants = emptyMap(); 100L })
        UserDataScope.withOwnerUid("owner-a") {
            assertFails { racing.create(input()) }
        }
        assertEquals(0, store.data.value.observationsCount)
    }

    private fun input() = PlantObservationInput("request", 1L, 2L, listOf("algae"), " note ", emptyList())

    private suspend fun assertFails(block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        catch (_: IllegalStateException) { failed = true }
        assertTrue("Expected validation or persistence failure", failed)
    }

    private class MemoryStore : DataStore<PlantHealthStore> {
        override val data = MutableStateFlow(PlantHealthStore.newBuilder()
            .setSchemaVersion(CommercialStoreSchema.PLANT_HEALTH_VERSION).build())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (PlantHealthStore) -> PlantHealthStore) = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private class RecordingMedia : PlantObservationMedia {
        val prepared = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val rolledBack = mutableListOf<String>()
        var failCommit = false
        override fun requirePendingOwner(uri: String, ownerUid: String) { require(uri == "owned") }
        override fun commit(uri: String) { check(!failCommit) }
        override fun prepareDeletion(uri: String, ownerUid: String) { prepared.add(uri) }
        override fun delete(uri: String, ownerUid: String) { deleted.add(uri) }
        override fun rollbackDraft(uri: String, ownerUid: String) { rolledBack.add(uri) }
    }
}
