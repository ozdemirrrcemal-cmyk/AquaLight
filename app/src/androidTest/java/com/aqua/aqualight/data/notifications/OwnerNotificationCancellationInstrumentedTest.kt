package com.aqua.aqualight.data.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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

        val manager = context.getSystemService(NotificationManager::class.java)
        val prefixA = NotificationIdentity.ownerTagPrefix(ownerA)
        val prefixB = NotificationIdentity.ownerTagPrefix(ownerB)
        assertTrue(manager.activeNotifications.any { it.tag?.startsWith(prefixA) == true })
        assertTrue(manager.activeNotifications.any { it.tag?.startsWith(prefixB) == true })

        platform.renderer.cancelOwner(ownerA)

        assertFalse(manager.activeNotifications.any { it.tag?.startsWith(prefixA) == true })
        assertTrue(manager.activeNotifications.any { it.tag?.startsWith(prefixB) == true })

        platform.renderer.cancelOwner(ownerB)
    }

    private fun grantNotificationPermissionWhenRequired() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
