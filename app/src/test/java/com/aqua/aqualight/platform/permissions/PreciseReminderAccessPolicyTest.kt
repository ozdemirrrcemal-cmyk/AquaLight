package com.aqua.aqualight.platform.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciseReminderAccessPolicyTest {

    @Test
    fun `special access starts at Android 12`() {
        assertFalse(PreciseReminderAccessPolicy.requiresSpecialAccess(27))
        assertFalse(PreciseReminderAccessPolicy.requiresSpecialAccess(30))
        assertTrue(PreciseReminderAccessPolicy.requiresSpecialAccess(31))
        assertTrue(PreciseReminderAccessPolicy.requiresSpecialAccess(36))
        assertTrue(PreciseReminderAccessPolicy.requiresSpecialAccess(37))
    }
}
