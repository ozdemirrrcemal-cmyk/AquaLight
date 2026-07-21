package com.aqua.aqualight.ui.common.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAddressPolicyTest {

    @Test
    fun acceptsCommercialEmailAddresses() {
        listOf(
            "user@example.com",
            "first.last+tag@example.co.uk",
            "support@aqua-light.com"
        ).forEach { address ->
            assertTrue("Expected a valid address: $address", EmailAddressPolicy.isValid(address))
        }
    }

    @Test
    fun rejectsBlankMalformedAndOversizedAddresses() {
        listOf(
            "",
            "   ",
            "not-an-email",
            "missing-domain@",
            "@missing-local.com",
            "two@@example.com",
            "user name@example.com",
            "a".repeat(310) + "@example.com"
        ).forEach { address ->
            assertFalse("Expected an invalid address: $address", EmailAddressPolicy.isValid(address))
        }
    }
}
