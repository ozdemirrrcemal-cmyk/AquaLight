package com.aqua.aqualight.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionPolicyTest {

    @Test
    fun `password account never waits for Google access revocation`() {
        assertFalse(shouldRevokeGoogleAccess(listOf("password")))
    }

    @Test
    fun `Google account revokes Google access`() {
        assertTrue(shouldRevokeGoogleAccess(listOf("password", "google.com")))
    }
}
