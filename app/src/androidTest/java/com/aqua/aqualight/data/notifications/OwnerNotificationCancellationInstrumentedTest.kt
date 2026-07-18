package com.aqua.aqualight.data.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.application.notifications.DeviceAlertNotification
import com.aqua.aqualight.application.notifications.NotificationDispatchResult
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerNotificationCancellationInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun cancellingOneOwnerLeavesOtherOwnersVisibleNotificationUntouched() = runBlocking {
        grantNotificationPermissionWhenRequired()
        val platform = NotificationPlatform.get(context)
        platform.permissionPolicy.ensureChannels()

        val suffix = UUID.randomUUID().toString()
        val ownerA = "visible-owner-a-$suffix"
        val ownerB = "visible-owner-b-$suffix"
        val deviceA = "device-a-$suffix"
        val deviceB = "device-b-$suffix"
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefixA = NotificationIdentity.ownerTagPrefix(ownerA)
        val prefixB = NotificationIdentity.ownerTagPrefix(ownerB)

        try {
            OwnerNotificationPreferences.create(context).apply {
                setEnabled(ownerA, true)
                setEnabled(ownerB, true)
            }

            assertEquals(
                NotificationDispatchResult.POSTED,
                platform.dispatchUseCase.dispatchDeviceAlert(
                    DeviceAlertNotification(
                        ownerUid = ownerA,
                        deviceUid = deviceA,
                        title = "Owner A alert",
                        message = "Owner A device alert"
                    )
                )
            )
            assertEquals(
                NotificationDispatchResult.POSTED,
                platform.dispatchUseCase.dispatchDeviceAlert(
                    DeviceAlertNotification(
                        ownerUid = ownerB,
                        deviceUid = deviceB,
                        title = "Owner B alert",
                        message = "Owner B device alert"
                    )
                )
            )

            assertTrue(
                "Owner A notification was not exposed by Android in time.",
                awaitNotificationState {
                    manager.activeNotifications.any { notification ->
                        notification.tag?.startsWith(prefixA) == true
                    }
                }
            )
            assertTrue(
                "Owner B notification was not exposed by Android in time.",
                awaitNotificationState {
                    manager.activeNotifications.any { notification ->
                        notification.tag?.startsWith(prefixB) == true
                    }
                }
            )

            platform.renderer.cancelOwner(ownerA)

            assertTrue(
                "Owner A notification remained visible after owner cancellation.",
                awaitNotificationState {
                    manager.activeNotifications.none { notification ->
                        notification.tag?.startsWith(prefixA) == true
                    }
                }
            )
            assertFalse(
                manager.activeNotifications.any { notification ->
                    notification.tag?.startsWith(prefixA) == true
                }
            )
            assertTrue(
                "Cancelling owner A removed owner B's notification.",
                manager.activeNotifications.any { notification ->
                    notification.tag?.startsWith(prefixB) == true
                }
            )
        } finally {
            platform.renderer.cancelOwner(ownerA)
            platform.renderer.cancelOwner(ownerB)
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
