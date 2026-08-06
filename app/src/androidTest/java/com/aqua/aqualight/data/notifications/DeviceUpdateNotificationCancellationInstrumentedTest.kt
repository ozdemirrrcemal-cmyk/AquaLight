package com.aqua.aqualight.data.notifications

import android.Manifest
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
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceUpdateNotificationCancellationInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun cancellingOneDeviceUpdateLeavesOtherDeviceUpdateVisible() = runBlocking {
        grantNotificationPermissionWhenRequired()
        val platform = NotificationPlatform.get(context)
        platform.permissionPolicy.ensureChannels()
        val suffix = UUID.randomUUID().toString()
        val owner = "firmware-owner-$suffix"
        val deviceA = "firmware-device-a-$suffix"
        val deviceB = "firmware-device-b-$suffix"
        val manager = context.getSystemService(NotificationManager::class.java)
        val tagA = NotificationIdentity.tag(NotificationCategory.DEVICE_UPDATES, owner, deviceA)
        val tagB = NotificationIdentity.tag(NotificationCategory.DEVICE_UPDATES, owner, deviceB)

        try {
            OwnerNotificationPreferences.create(context).setEnabled(owner, true)
            assertEquals(
                NotificationDispatchResult.POSTED,
                platform.dispatchUseCase.dispatchDeviceUpdate(notification(owner, deviceA))
            )
            assertEquals(
                NotificationDispatchResult.POSTED,
                platform.dispatchUseCase.dispatchDeviceUpdate(notification(owner, deviceB))
            )
            assertTrue(awaitNotificationState { manager.activeNotifications.any { it.tag == tagA } })
            assertTrue(awaitNotificationState { manager.activeNotifications.any { it.tag == tagB } })

            platform.lifecycleUseCase.cancelDeviceUpdate(owner, deviceA)

            assertTrue(awaitNotificationState { manager.activeNotifications.none { it.tag == tagA } })
            assertFalse(manager.activeNotifications.any { it.tag == tagA })
            assertTrue(manager.activeNotifications.any { it.tag == tagB })
        } finally {
            platform.renderer.cancelOwner(owner)
        }
    }

    private fun notification(ownerUid: String, deviceUid: String) = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = deviceUid,
        title = "Firmware update",
        message = "A firmware update is available."
    )

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
