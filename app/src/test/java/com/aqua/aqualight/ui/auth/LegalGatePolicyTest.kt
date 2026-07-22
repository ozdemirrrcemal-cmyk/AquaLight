package com.aqua.aqualight.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegalGatePolicyTest {

    @Test
    fun termsMustBeAcceptedBeforeAgeConfirmationIsEvaluated() {
        assertEquals(
            LegalGatePolicy.Failure.TERMS_NOT_ACCEPTED,
            LegalGatePolicy.validate(
                termsAccepted = false,
                adultConfirmed = true
            )
        )
    }

    @Test
    fun adultStatusMustBeConfirmedSeparately() {
        assertEquals(
            LegalGatePolicy.Failure.ADULT_NOT_CONFIRMED,
            LegalGatePolicy.validate(
                termsAccepted = true,
                adultConfirmed = false
            )
        )
    }

    @Test
    fun gatePassesOnlyWhenBothConfirmationsArePresent() {
        assertNull(
            LegalGatePolicy.validate(
                termsAccepted = true,
                adultConfirmed = true
            )
        )
    }
}
