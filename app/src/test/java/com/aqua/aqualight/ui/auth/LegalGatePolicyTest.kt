package com.aqua.aqualight.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegalGatePolicyTest {

    @Test
    fun termsMustBeAccepted() {
        assertEquals(
            LegalGatePolicy.Failure.TERMS_NOT_ACCEPTED,
            LegalGatePolicy.validate(termsAccepted = false)
        )
    }

    @Test
    fun gatePassesWhenTermsAreAccepted() {
        assertNull(
            LegalGatePolicy.validate(termsAccepted = true)
        )
    }
}
