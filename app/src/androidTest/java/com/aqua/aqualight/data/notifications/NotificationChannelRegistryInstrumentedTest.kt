package com.aqua.aqualight.data.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationChannelRegistryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun channelStateIsDeterministicAcrossApiLevelsAndRecreation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            assertEquals(
                NotificationChannelState.NOT_REQUIRED,
                NotificationChannelRegistry.readState(context)
                    .careReminderChannelState
            )
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(
            NotificationChannelRegistry.CARE_REMINDERS_CHANNEL_ID
        )

        assertEquals(
            NotificationChannelState.MISSING,
            NotificationChannelRegistry.readState(context)
                .careReminderChannelState
        )

        NotificationChannelRegistry.ensureChannels(context)
        assertEquals(
            NotificationChannelState.ENABLED,
            NotificationChannelRegistry.readState(context)
                .careReminderChannelState
        )

        // Repeated startup calls must be harmless and must not create a second ID.
        NotificationChannelRegistry.ensureChannels(context)
        assertEquals(
            NotificationChannelState.ENABLED,
            NotificationChannelRegistry.readState(context)
                .careReminderChannelState
        )
    }
}
