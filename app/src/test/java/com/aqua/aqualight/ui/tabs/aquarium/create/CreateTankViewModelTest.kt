package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateTankViewModelTest {

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
    }

    @Test
    fun tankSizeRequiresExplicitSaveAndConfirmationSurvivesRecreation() {
        val state = SavedStateHandle()
        val first = CreateTankViewModel(state)

        assertEquals(10, first.tankDraft.widthCm)
        assertEquals(10, first.tankDraft.lengthCm)
        assertEquals(10, first.tankDraft.heightCm)
        assertFalse(first.isTankSizeConfirmed)

        first.updateTankSize(
            widthCm = 80,
            lengthCm = 40,
            heightCm = 45
        )

        val recreated = CreateTankViewModel(state)
        assertTrue(recreated.isTankSizeConfirmed)
        assertEquals(80, recreated.tankDraft.widthCm)
        assertEquals(40, recreated.tankDraft.lengthCm)
        assertEquals(45, recreated.tankDraft.heightCm)

        recreated.completeTank()
        assertFalse(CreateTankViewModel(state).isTankSizeConfirmed)
    }

    @Test
    fun tankSizeConfirmationIsDiscardedWithoutAValidDraft() {
        val state = SavedStateHandle(
            mapOf(
                "createTank.draftJson" to "{invalid-json",
                "createTank.tankSizeConfirmed" to true
            )
        )

        val restored = CreateTankViewModel(state)

        assertEquals(10, restored.tankDraft.widthCm)
        assertFalse(restored.isTankSizeConfirmed)
    }
}
