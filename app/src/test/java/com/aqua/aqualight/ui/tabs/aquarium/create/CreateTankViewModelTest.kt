package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateTankViewModelTest {

    @Test
    fun newDraftHasNoImplicitSetupDateOrTankDimensions() {
        val draft = CreateTankViewModel(SavedStateHandle()).tankDraft

        assertNull(draft.setupDateEpochDay)
        assertEquals(0, draft.widthCm)
        assertEquals(0, draft.lengthCm)
        assertEquals(0, draft.heightCm)
        assertTrue(draft.tankType.isBlank())
    }

    @Test
    fun draftSurvivesViewModelRecreationAndCanBeClearedAfterCommit() {
        val state = SavedStateHandle()
        val first = CreateTankViewModel(state)
        first.updateTankName("Process-safe reef")
        first.updateTankDescription("Restored after process death")
        first.updateTankPhoto("content://aqualight/tank-photo")
        first.updateTankPlants(
            listOf(
                AquariumPlantTag(
                    id = 91L,
                    plantName = "Anubias",
                    category = "Rhizome"
                )
            )
        )

        val recreated = CreateTankViewModel(state)

        assertEquals("Process-safe reef", recreated.tankDraft.name)
        assertEquals("Restored after process death", recreated.tankDraft.description)
        assertEquals("content://aqualight/tank-photo", recreated.tankDraft.photoUri)
        assertEquals(91L, recreated.tankDraft.plants.single().id)

        recreated.completeTank()
        val afterCommit = CreateTankViewModel(state)
        assertEquals("", afterCommit.tankDraft.name)
        assertNull(afterCommit.tankDraft.photoUri)
        assertEquals(0, afterCommit.tankDraft.widthCm)
        assertEquals(0, afterCommit.tankDraft.lengthCm)
        assertEquals(0, afterCommit.tankDraft.heightCm)
    }
}
