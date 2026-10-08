package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightQuickSetupSavedStateTest {

    @Test
    fun draftSurvivesHelperRecreationForSameDevice() {
        val handle = SavedStateHandle()
        val first = DeviceLightQuickSetupSavedState(handle)

        first.bindDevice("light-1")
        first.saveWaterHeight("38")
        first.saveFixtureHeight("18")
        first.saveFirstLightMinute(615)
        first.saveCo2Precharged(true)
        first.persistStage(DeviceLightQuickSetupStage.REVIEW)

        val restored = DeviceLightQuickSetupSavedState(handle)
        val state = restored.bindDevice("light-1")

        assertEquals("38", state.waterHeightText)
        assertEquals("18", state.fixtureHeightText)
        assertEquals(615, state.firstLightOnMinuteOfDay)
        assertTrue(state.co2Precharged)
        assertEquals(DeviceLightQuickSetupStage.REVIEW, restored.restoredStage(co2Present = true))
    }

    @Test
    fun transientApplyingStageIsNeverPersisted() {
        val handle = SavedStateHandle()
        val state = DeviceLightQuickSetupSavedState(handle)

        state.bindDevice("light-1")
        state.persistStage(DeviceLightQuickSetupStage.REVIEW)
        state.persistStage(DeviceLightQuickSetupStage.APPLYING)

        val restored = DeviceLightQuickSetupSavedState(handle)

        assertEquals(DeviceLightQuickSetupStage.REVIEW, restored.restoredStage(co2Present = true))
    }

    @Test
    fun switchingDeviceClearsUserDraft() {
        val handle = SavedStateHandle()
        val state = DeviceLightQuickSetupSavedState(handle)

        state.bindDevice("light-1")
        state.saveWaterHeight("40")
        state.saveFixtureHeight("16")
        state.saveCo2Precharged(true)
        state.persistStage(DeviceLightQuickSetupStage.CO2_CONFIRMATION)

        val secondDevice = state.bindDevice("light-2")

        assertEquals("", secondDevice.waterHeightText)
        assertEquals("", secondDevice.fixtureHeightText)
        assertFalse(secondDevice.co2Precharged)
        assertEquals(
            DeviceLightQuickSetupStage.PROFILE,
            state.restoredStage(co2Present = true)
        )
    }

    @Test
    fun co2StageFallsBackWhenCurrentAquariumHasNoCo2() {
        val handle = SavedStateHandle()
        val state = DeviceLightQuickSetupSavedState(handle)

        state.bindDevice("light-1")
        state.persistStage(DeviceLightQuickSetupStage.CO2_CONFIRMATION)

        assertEquals(
            DeviceLightQuickSetupStage.LIGHT_TIME,
            state.restoredStage(co2Present = false)
        )
    }
}
