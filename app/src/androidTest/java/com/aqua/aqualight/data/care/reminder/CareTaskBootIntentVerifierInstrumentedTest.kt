package com.aqua.aqualight.data.care.reminder

import android.app.AlarmManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareTaskBootIntentVerifierInstrumentedTest {

    @Test
    fun acceptsOnlySupportedSystemActions() {
        val supportedActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )

        supportedActions.forEach { action ->
            assertEquals(
                action,
                CareTaskBootIntentVerifier.verifiedAction(Intent(action))
            )
        }
    }

    @Test
    fun rejectsMissingAndUnsupportedActions() {
        assertNull(CareTaskBootIntentVerifier.verifiedAction(Intent()))
        assertNull(
            CareTaskBootIntentVerifier.verifiedAction(
                Intent("com.aqua.aqualight.UNTRUSTED_ACTION")
            )
        )
    }
}
