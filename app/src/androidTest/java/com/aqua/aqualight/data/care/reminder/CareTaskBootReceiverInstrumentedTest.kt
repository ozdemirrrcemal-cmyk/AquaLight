package com.aqua.aqualight.data.care.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareTaskBootReceiverInstrumentedTest {

    @Test
    fun rejectsMissingAndUnsupportedActionsBeforeApplicationAccess() {
        val receiver = CareTaskBootReceiver()

        receiver.onReceive(failOnApplicationAccess(), Intent())
        receiver.onReceive(
            failOnApplicationAccess(),
            Intent("com.aqua.aqualight.UNTRUSTED_ACTION")
        )
    }

    @Test
    fun supportedSystemActionsReachTheAuthenticatedApplicationBoundary() {
        val receiver = CareTaskBootReceiver()
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )

        actions.forEach { action ->
            val error = runCatching {
                receiver.onReceive(
                    failOnApplicationAccess(),
                    Intent(action)
                )
            }.exceptionOrNull()

            assertTrue(
                "Expected $action to reach the authenticated application boundary.",
                error is AssertionError
            )
            assertEquals(APPLICATION_BOUNDARY_MESSAGE, error?.message)
        }
    }

    private fun failOnApplicationAccess(): Context {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        return object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context {
                throw AssertionError(APPLICATION_BOUNDARY_MESSAGE)
            }
        }
    }

    private companion object {
        const val APPLICATION_BOUNDARY_MESSAGE =
            "Receiver reached the authenticated application boundary."
    }
}
