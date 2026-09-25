package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantPhotoTargetViewModelTest {
    @Test
    fun selectionCannotMoveToAnotherPlantOrOwnerWhileResultIsPending() {
        val target = PlantPhotoTargetViewModel(SavedStateHandle())
        assertTrue(target.select("owner-a", 11L))
        assertTrue(target.begin())
        assertFalse(target.select("owner-a", 12L))
        assertFalse(target.select("owner-b", 11L))
        assertFalse(target.begin())
        assertEquals("owner-a", target.ownerUid)
        assertEquals(11L, target.plantId)
        target.finish()
        assertTrue(target.select("owner-a", 12L))
        assertTrue(target.begin())
        assertEquals(12L, target.plantId)
    }

    @Test
    fun restoredPendingResultRetainsOriginalTargetAndLock() {
        val handle = SavedStateHandle()
        val initial = PlantPhotoTargetViewModel(handle)
        initial.select("owner-a", 42L)
        initial.begin()
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = PlantPhotoTargetViewModel(restoredHandle)
        assertTrue(restored.isInProgress)
        assertEquals("owner-a", restored.ownerUid)
        assertEquals(42L, restored.plantId)
        assertFalse(restored.select("owner-b", 99L))
    }

    @Test
    fun missingTargetCannotStartAndCancellationAllowsRetry() {
        val target = PlantPhotoTargetViewModel(SavedStateHandle())
        assertFalse(target.begin())
        target.select("owner-a", 11L)
        assertTrue(target.begin())
        target.finish()
        assertTrue(target.begin())
    }
}
