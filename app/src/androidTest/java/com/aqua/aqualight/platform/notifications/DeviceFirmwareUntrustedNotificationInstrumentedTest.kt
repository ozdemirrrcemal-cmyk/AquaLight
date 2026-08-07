package com.aqua.aqualight.platform.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import com.aqua.aqualight.data.notifications.NotificationIdentity
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.notifications.OwnerNotificationPreferences
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFirmwareUntrustedNotificationInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun untrustedCancellationHidesAlertWithoutResettingTargetDedup() = runBlocking {
        grantNotificationPermissionWhenRequired()
        val suffix = UUID.randomUUID().toString()
        val ownerUid = "untrusted-owner-$suffix"
        val deviceUid = "untrusted-device-$suffix"
        val platform = NotificationPlatform.get(context)
        val hint = updateAvailableHint(deviceUid)
        prepareOwner(platform, ownerUid, deviceUid)

        try {
            assertTrue(platform.deviceFirmwareUpdates.publishAvailabilityHint(ownerUid, hint))
            assertTrue(awaitNotificationState(ownerUid, deviceUid, visible = true))

            platform.deviceFirmwareUpdates.cancelUntrustedAvailability(ownerUid, deviceUid)

            assertTrue(awaitNotificationState(ownerUid, deviceUid, visible = false))
            assertFalse(platform.deviceFirmwareUpdates.publishAvailabilityHint(ownerUid, hint))
        } finally {
            platform.deviceFirmwareUpdates.clearOwner(ownerUid)
        }
    }

    private suspend fun prepareOwner(
        platform: NotificationPlatform,
        ownerUid: String,
        deviceUid: String
    ) {
        OwnerNotificationPreferences.create(context).setEnabled(ownerUid, true)
        DeviceUpdateNotificationLedger.create(context).clearOwner(ownerUid)
        platform.renderer.cancelDeviceUpdate(ownerUid, deviceUid)
    }

    private fun updateAvailableHint(
        deviceUid: String
    ): DeviceFirmwareAvailabilityHint.UpdateAvailable {
        return DeviceFirmwareAvailabilityHint.UpdateAvailable(
            deviceUid = deviceUid,
            deviceName = "Aqua Light",
            currentVersion = "1.4.0",
            targetVersion = "1.5.0"
        )
    }

    private fun awaitNotificationState(
        ownerUid: String,
        deviceUid: String,
        visible: Boolean
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_STATE_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isVisible(ownerUid, deviceUid) == visible) return true
            SystemClock.sleep(NOTIFICATION_STATE_POLL_MILLIS)
        }
        return isVisible(ownerUid, deviceUid) == visible
    }

    private fun isVisible(ownerUid: String, deviceUid: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val tag = NotificationIdentity.tag(
            NotificationCategory.DEVICE_UPDATES,
            ownerUid,
            deviceUid
        )
        return manager.activeNotifications.any { active -> active.tag == tag }
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
