package com.aqua.aqualight.data.care.reminder

import android.app.AlarmManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareTaskBootIntentVerifierInstrumentedTest {

    @Test
    fun acceptsOnlyMatchingSupportedSystemActions() {
        val supportedActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )

        supportedActions.forEach { action ->
            assertTrue(
                CareTaskBootIntentVerifier.isVerified(
                    Intent(action),
                    action
                )
            )
        }
    }

    @Test
    fun rejectsMissingUnsupportedAndMismatchedActions() {
        assertFalse(CareTaskBootIntentVerifier.isVerified(Intent(), null))
        assertFalse(
            CareTaskBootIntentVerifier.isVerified(
                Intent("com.aqua.aqualight.UNTRUSTED_ACTION"),
                "com.aqua.aqualight.UNTRUSTED_ACTION"
            )
        )
        assertFalse(
            CareTaskBootIntentVerifier.isVerified(
                Intent(Intent.ACTION_BOOT_COMPLETED),
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        )
    }
}
