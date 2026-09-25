package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockPhotoFormViewModelTest {
    @Test
    fun newRecordIdentityAndOwnerStayFixedAcrossRecreation() {
        val state = SavedStateHandle()
        val original = LivestockPhotoFormViewModel(state)
        original.initialize("owner-a", 0)
        val id = requireNotNull(original.recordId)
        assertTrue(id > 0)
        val restored = LivestockPhotoFormViewModel(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        restored.initialize("owner-b", 99)
        assertEquals(id, restored.recordId)
        assertEquals("owner-a", restored.ownerUid)
    }

    @Test
    fun editingKeepsTheExistingRecordId() {
        val form = LivestockPhotoFormViewModel(SavedStateHandle())
        form.initialize("owner-a", 42)
        assertEquals(42L, form.recordId)
    }
}
