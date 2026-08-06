package com.aqua.aqualight.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationDispatchResult
import com.aqua.aqualight.ui.main.MainActivity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceUpdateNotificationRendererInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun availableUpdateUsesDeviceChannelAndSharedActionIntent() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.postAvailableUpdate()
            val notification = fixture.awaitNotification()
            val action = requireNotNull(notification.actions).single()

            assertEquals(NotificationChannelRegistry.DEVICE_UPDATES, notification.channelId)
            assertEquals(
                "Update ready for Aqua Light",
                notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            )
            assertEquals(
                "1.4.0 → 1.5.0",
                notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            )
            assertEquals("Update", action.title.toString())
            assertEquals(notification.contentIntent, action.actionIntent)
            assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        } finally {
            fixture.cleanup()
        }
    }

    @Test
    fun deviceUpdateRouteIntentCarriesExactOwnerAndDevice() {
        val fixture = createFixture()
        val intent = fixture.platform.renderer.deviceUpdateLaunchIntent(
            ownerUid = fixture.ownerUid,
            deviceUid = fixture.deviceUid
        )

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(
            NotificationIdentity.contentData(
                NotificationCategory.DEVICE_UPDATES,
                fixture.ownerUid,
                fixture.deviceUid
            ),
            intent.data
        )
        assertTrue(intent.getBooleanExtra(MainActivity.EXTRA_START_IN_APP, false))
        assertEquals(fixture.ownerUid, intent.getStringExtra(MainActivity.EXTRA_OWNER_UID))
        assertEquals(
            fixture.deviceUid,
            intent.getStringExtra(MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UID)
        )
    }

    @Test
    fun progressUpdateReplacesAvailabilityAsOngoingWithoutAction() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.postAvailableUpdate()
            fixture.awaitNotification()
            fixture.postProgressUpdate()
            val notification = fixture.awaitNotification { value ->
                value.flags and Notification.FLAG_ONGOING_EVENT != 0
            }

            assertTrue(notification.actions.isNullOrEmpty())
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(
                fixture.platform.renderer.isDeviceUpdateOperationNotificationActive(
                    fixture.ownerUid,
                    fixture.deviceUid
                )
            )
        } finally {
            fixture.cleanup()
        }
    }

    private fun createFixture(): NotificationFixture {
        grantNotificationPermissionWhenRequired()
        val suffix = UUID.randomUUID().toString()
        return NotificationFixture(
            ownerUid = "update-owner-$suffix",
            deviceUid = "update-device-$suffix"
        )
    }

    private inner class NotificationFixture(
        val ownerUid: String,
        val deviceUid: String
    ) {
        val platform = NotificationPlatform.get(context)
        private val manager = context.getSystemService(NotificationManager::class.java)
        private val tag = NotificationIdentity.tag(
            NotificationCategory.DEVICE_UPDATES,
            ownerUid,
            deviceUid
        )

        suspend fun postAvailableUpdate() {
            OwnerNotificationPreferences.create(context).setEnabled(ownerUid, true)
            assertEquals(
                NotificationDispatchResult.POSTED,
                platform.dispatchUseCase.dispatchDeviceUpdate(
                    DeviceUpdateNotification(
                        ownerUid = ownerUid,
                        deviceUid = deviceUid,
                        title = "Update ready for Aqua Light",
                        message = "1.4.0 → 1.5.0",
                        actionLabel = "Update"
                    )
                )
            )
        }

        suspend fun postProgressUpdate() {
            assertEquals(
                NotificationDispatchResult.POSTED,
                platform.dispatchUseCase.dispatchDeviceUpdate(
                    DeviceUpdateNotification(
                        ownerUid = ownerUid,
                        deviceUid = deviceUid,
                        title = "Updating Aqua Light",
                        message = "Downloading · 42%",
                        progressPercent = 42,
                        ongoing = true
                    )
                )
            )
        }

        fun awaitNotification(
            predicate: (Notification) -> Boolean = { true }
        ): Notification {
            assertTrue(
                "Device update notification was not exposed by Android in time.",
                awaitNotificationState {
                    manager.activeNotifications
                        .firstOrNull { active -> active.tag == tag }
                        ?.notification
                        ?.let(predicate) == true
                }
            )
            return manager.activeNotifications.first { active -> active.tag == tag }.notification
        }

        fun cleanup() {
            platform.renderer.cancelOwner(ownerUid)
        }
    }

    private fun awaitNotificationState(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_STATE_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(NOTIFICATION_STATE_POLL_MILLIS)
        }
        return condition()
    }

    private fun grantNotificationPermissionWhenRequired() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    private companion object {
        const val NOTIFICATION_STATE_TIMEOUT_MILLIS = 5_000L
        const val NOTIFICATION_STATE_POLL_MILLIS = 50L
    }
}
