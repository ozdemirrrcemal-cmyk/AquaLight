package com.aqua.aqualight.data.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationChannelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationChannelRegistryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun allPermanentChannelsAreIdempotentAndUnversioned() {
        assertEquals("care_reminders", NotificationChannelRegistry.CARE_REMINDERS)
        assertEquals("device_alerts", NotificationChannelRegistry.DEVICE_ALERTS)
        assertEquals("device_updates", NotificationChannelRegistry.DEVICE_UPDATES)
        assertFalse(NotificationChannelRegistry.CARE_REMINDERS.contains("_v"))
        assertFalse(NotificationChannelRegistry.DEVICE_ALERTS.contains("_v"))
        assertFalse(NotificationChannelRegistry.DEVICE_UPDATES.contains("_v"))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            NotificationCategory.entries.forEach { category ->
                assertEquals(
                    NotificationChannelState.NOT_REQUIRED,
                    NotificationChannelRegistry.readState(context, category)
                )
            }
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        NotificationCategory.entries.forEach { category ->
            manager.deleteNotificationChannel(NotificationChannelRegistry.channelId(category))
            assertEquals(
                NotificationChannelState.MISSING,
                NotificationChannelRegistry.readState(context, category)
            )
        }

        NotificationChannelRegistry.ensureAll(context)
        NotificationCategory.entries.forEach { category ->
            assertEquals(
                NotificationChannelState.ENABLED,
                NotificationChannelRegistry.readState(context, category)
            )
        }

        NotificationChannelRegistry.ensureAll(context)
        NotificationCategory.entries.forEach { category ->
            assertEquals(
                NotificationChannelState.ENABLED,
                NotificationChannelRegistry.readState(context, category)
            )
        }
    }
}
