package com.aqua.aqualight.ui.tabs.aquarium.create

import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreateTankInfoValidationPolicyTest {

    @Test
    fun emptyDraftRequiresSetupDateBeforeOtherFinalDetails() {
        assertEquals(
            CreateTankInfoValidationPolicy.Issue.SETUP_DATE_REQUIRED,
            CreateTankInfoValidationPolicy.firstIssue(AquariumTankDraft())
        )
    }

    @Test
    fun setupDateWithoutExplicitDimensionsRequiresTankSize() {
        assertEquals(
            CreateTankInfoValidationPolicy.Issue.SIZE_REQUIRED,
            CreateTankInfoValidationPolicy.firstIssue(
                AquariumTankDraft(setupDateEpochDay = 20_000L)
            )
        )
    }

    @Test
    fun completeDimensionsStillRequireTankType() {
        assertEquals(
            CreateTankInfoValidationPolicy.Issue.TANK_TYPE_REQUIRED,
            CreateTankInfoValidationPolicy.firstIssue(
                validFinalDetailsDraft().copy(tankType = "")
            )
        )
    }

    @Test
    fun setupDateDimensionsAndTankTypeCompleteFinalDetails() {
        assertNull(
            CreateTankInfoValidationPolicy.firstIssue(validFinalDetailsDraft())
        )
    }

    private fun validFinalDetailsDraft(): AquariumTankDraft = AquariumTankDraft(
        setupDateEpochDay = 20_000L,
        widthCm = 60,
        lengthCm = 35,
        heightCm = 40,
        tankType = "planted"
    )
}
