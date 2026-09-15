package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TankInfoValidationPolicyTest {

    @Test
    fun setupDateIsRequiredBeforeOtherTankInfo() {
        val error = TankInfoValidationPolicy.validate(
            draft = validDraft().copy(setupDateEpochDay = null),
            isTankSizeConfirmed = true
        )

        assertEquals(TankInfoValidationError.SETUP_DATE_REQUIRED, error)
    }

    @Test
    fun untouchedDefaultDimensionsAreNotAccepted() {
        val error = TankInfoValidationPolicy.validate(
            draft = validDraft().copy(widthCm = 10, lengthCm = 10, heightCm = 10),
            isTankSizeConfirmed = false
        )

        assertEquals(TankInfoValidationError.TANK_SIZE_REQUIRED, error)
    }

    @Test
    fun confirmedDimensionsMustStillBeValid() {
        val error = TankInfoValidationPolicy.validate(
            draft = validDraft().copy(widthCm = 0),
            isTankSizeConfirmed = true
        )

        assertEquals(TankInfoValidationError.INVALID_TANK_SIZE, error)
    }

    @Test
    fun tankTypeRemainsRequiredAfterNewRequiredFields() {
        val error = TankInfoValidationPolicy.validate(
            draft = validDraft().copy(tankType = ""),
            isTankSizeConfirmed = true
        )

        assertEquals(TankInfoValidationError.TANK_TYPE_REQUIRED, error)
    }

    @Test
    fun completeTankInfoIsAccepted() {
        val error = TankInfoValidationPolicy.validate(
            draft = validDraft(),
            isTankSizeConfirmed = true
        )

        assertNull(error)
    }

    private fun validDraft() = AquariumTankDraft(
        setupDateEpochDay = 20_000L,
        widthCm = 60,
        lengthCm = 30,
        heightCm = 36,
        tankType = "Planted"
    )
}
