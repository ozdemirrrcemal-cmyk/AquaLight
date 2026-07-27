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
    fun acceptsMatchingObservedActions() {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            "com.aqua.aqualight.UNTRUSTED_ACTION"
        )

        actions.forEach { action ->
            assertTrue(
                CareTaskBootIntentVerifier.matchesObservedAction(
                    Intent(action),
                    action
                )
            )
        }
    }

    @Test
    fun rejectsMissingAndMismatchedActions() {
        assertFalse(CareTaskBootIntentVerifier.matchesObservedAction(Intent(), null))
        assertFalse(
            CareTaskBootIntentVerifier.matchesObservedAction(
                Intent(Intent.ACTION_BOOT_COMPLETED),
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        )
    }
}
