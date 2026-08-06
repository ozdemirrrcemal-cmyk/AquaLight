package com.aqua.aqualight.platform.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import com.aqua.aqualight.data.notifications.NotificationIdentity
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.notifications.OwnerNotificationPreferences
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDeviceFirmwareUpdateNotificationPublisherInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun backgroundHintPostsOnceForOwnerDeviceAndTargetVersion() = runBlocking {
        val fixture = createFixture()
        try {
            val hint = DeviceFirmwareAvailabilityHint.UpdateAvailable(
                deviceUid = fixture.deviceUid,
                deviceName = "Aqua Light",
                currentVersion = "1.4.0",
                targetVersion = "1.5.0"
            )

            assertTrue(fixture.publisher.publishAvailabilityHint(hint))
            assertFalse(fixture.publisher.publishAvailabilityHint(hint))
            val notification = fixture.awaitNotification()

            assertEquals(
                context.getString(
                    R.string.device_update_background_notification_title,
                    "Aqua Light"
                ),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            )
            assertEquals(
                context.getString(R.string.device_update_background_notification_action),
                requireNotNull(notification.actions).single().title.toString()
            )
        } finally {
            fixture.cleanup()
        }
    }

    @Test
    fun liveAvailabilityStateDoesNotCreateSystemAlert() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.publisher.publish(
                state = DeviceOtaState.UpdateAvailable(preparedPlan(fixture.deviceUid)),
                deviceName = "Aqua Light"
            )

            assertTrue(fixture.activeNotification() == null)
        } finally {
            fixture.cleanup()
        }
    }

    private suspend fun createFixture(): PublisherFixture {
        grantNotificationPermissionWhenRequired()
        val suffix = UUID.randomUUID().toString()
        val ownerUid = "publisher-owner-$suffix"
        val deviceUid = "publisher-device-$suffix"
        val platform = NotificationPlatform.get(context)
        val ledger = DeviceUpdateNotificationLedger.create(context)
        OwnerNotificationPreferences.create(context).setEnabled(ownerUid, true)
        ledger.clearOwner(ownerUid)
        platform.renderer.cancelDeviceUpdate(ownerUid, deviceUid)
        return PublisherFixture(ownerUid, deviceUid, platform, ledger)
    }

    private inner class PublisherFixture(
        val ownerUid: String,
        val deviceUid: String,
        val platform: NotificationPlatform,
        private val ledger: DeviceUpdateNotificationLedger
    ) {
        val publisher = AndroidDeviceFirmwareUpdateNotificationPublisher(
            context = context,
            ownerUid = ownerUid,
            dispatchUseCase = platform.dispatchUseCase,
            ledger = ledger
        )
        private val manager = context.getSystemService(NotificationManager::class.java)
        private val tag = NotificationIdentity.tag(
            NotificationCategory.DEVICE_UPDATES,
            ownerUid,
            deviceUid
        )

        fun activeNotification(): Notification? {
            return manager.activeNotifications
                .firstOrNull { active -> active.tag == tag }
                ?.notification
        }

        fun awaitNotification(): Notification {
            assertTrue(awaitNotificationState { activeNotification() != null })
            return requireNotNull(activeNotification())
        }

        suspend fun cleanup() {
            platform.renderer.cancelOwner(ownerUid)
            ledger.clearOwner(ownerUid)
        }
    }

    private fun preparedPlan(deviceUid: String): PreparedDeviceFirmwareUpdate {
        return PreparedDeviceFirmwareUpdate(
            deviceUid = deviceUid,
            currentVersion = "1.4.0",
            targetVersion = "1.5.0",
            channel = "stable",
            environment = "light_aqua_light",
            productKey = "LIGHT_AQUA_LIGHT",
            productId = "com.aqualight.light.aqua_light",
            model = "aqua_light",
            hardwareRevision = "1.0",
            displayName = "Aqua Light",
            filename = "AquaLight-light_aqua_light-v1.5.0-ota.bin",
            downloadUrl = "https://example.invalid/firmware.bin",
            sha256 = "a".repeat(64),
            sizeBytes = 1,
            applyNow = true,
            releaseContent = DeviceFirmwareReleaseContent.EMPTY
        )
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
