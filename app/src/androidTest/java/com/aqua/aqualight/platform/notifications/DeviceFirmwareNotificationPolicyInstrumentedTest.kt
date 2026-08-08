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
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.AppProcessForegroundState
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.notifications.NotificationIdentity
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.notifications.OwnerNotificationPreferences
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFirmwareNotificationPolicyInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun availabilityCheckFailureNeverCreatesSystemNotification() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.publisher.publishOtaState(
                ownerUid = fixture.ownerUid,
                state = DeviceOtaState.Failed(
                    deviceUid = fixture.deviceUid,
                    failure = DeviceOtaFailure(
                        reason = DeviceOtaFailureReason.CONNECTION,
                        recoverable = true,
                        stage = DeviceOtaFailureStage.AVAILABILITY_CHECK
                    )
                ),
                deviceName = "Dose Pro 4"
            )

            fixture.awaitNoNotification()
        } finally {
            fixture.cleanup()
        }
    }

    @Test
    fun executionFailureCreatesOperationFailureNotification() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.publisher.publishOtaState(
                ownerUid = fixture.ownerUid,
                state = DeviceOtaState.Failed(
                    deviceUid = fixture.deviceUid,
                    failure = DeviceOtaFailure(
                        reason = DeviceOtaFailureReason.DOWNLOAD_TIMEOUT,
                        recoverable = true,
                        stage = DeviceOtaFailureStage.UPDATE_EXECUTION
                    )
                ),
                deviceName = "Dose Pro 4"
            )

            assertNotNull(fixture.awaitNotification())
        } finally {
            fixture.cleanup()
        }
    }

    @Test
    fun foregroundAvailabilityIsSuppressedAndCancelsVisibleAvailability() = runBlocking {
        val fixture = createFixture()
        try {
            val hint = fixture.updateAvailableHint()
            assertTrue(fixture.publisher.publishAvailabilityHint(fixture.ownerUid, hint))
            fixture.awaitNotification()

            AppProcessForegroundState.update(true)

            assertFalse(fixture.publisher.publishAvailabilityHint(fixture.ownerUid, hint))
            fixture.awaitNoNotification()
        } finally {
            AppProcessForegroundState.update(false)
            fixture.cleanup()
        }
    }

    private suspend fun createFixture(): Fixture {
        grantNotificationPermissionWhenRequired()
        AppProcessForegroundState.update(false)
        val suffix = UUID.randomUUID().toString()
        val ownerUid = "policy-owner-$suffix"
        val deviceUid = "policy-device-$suffix"
        val platform = NotificationPlatform.get(context)
        OwnerNotificationPreferences.create(context).setEnabled(ownerUid, true)
        platform.deviceFirmwareUpdates.clearOwner(ownerUid)
        return Fixture(ownerUid, deviceUid, platform)
    }

    private inner class Fixture(
        val ownerUid: String,
        val deviceUid: String,
        platform: NotificationPlatform
    ) {
        val publisher = platform.deviceFirmwareUpdates
        private val manager = context.getSystemService(NotificationManager::class.java)
        private val tag = NotificationIdentity.tag(
            NotificationCategory.DEVICE_UPDATES,
            ownerUid,
            deviceUid
        )

        fun updateAvailableHint() = DeviceFirmwareAvailabilityHint.UpdateAvailable(
            deviceUid = deviceUid,
            deviceName = "Dose Pro 4",
            currentVersion = "1.0.0",
            targetVersion = "1.1.0"
        )

        fun activeNotification(): Notification? = manager.activeNotifications
            .firstOrNull { active -> active.tag == tag }
            ?.notification

        fun awaitNotification(): Notification {
            assertTrue(awaitState { activeNotification() != null })
            return requireNotNull(activeNotification())
        }

        fun awaitNoNotification() {
            assertTrue(awaitState { activeNotification() == null })
        }

        suspend fun cleanup() {
            publisher.clearOwner(ownerUid)
        }
    }

    private fun awaitState(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(POLL_MILLIS)
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
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
