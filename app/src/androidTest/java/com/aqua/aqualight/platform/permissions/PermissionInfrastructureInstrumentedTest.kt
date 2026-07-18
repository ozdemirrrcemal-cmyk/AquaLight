package com.aqua.aqualight.platform.permissions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionBottomSheet
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionContinuationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionInfrastructureInstrumentedTest {

    @Test
    fun requestHistoryIsSharedAndDurableForTheInstallation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permission = "com.aqua.aqualight.test.PERMISSION_${System.nanoTime()}"
        val first = PermissionRequestHistoryStore(context)

        assertFalse(first.wasRequested(permission))
        first.markRequested(listOf(permission))

        val reopened = PermissionRequestHistoryStore(context)
        assertTrue(reopened.wasRequested(permission))
        assertTrue(reopened.wereAllRequested(listOf(permission)))
    }

    @Test
    fun capabilitySheetHasNoArgumentConstructorAndRecreatableArguments() {
        val constructor = CapabilityPermissionBottomSheet::class.java.getDeclaredConstructor()
        assertNotNull(constructor.newInstance())

        val original = CapabilityPermissionBottomSheet.newInstance(
            capability = AppCapability.CAMERA_QR,
            mode = CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS,
            requestKey = "test_permission_result"
        )
        val recreated = constructor.newInstance().apply {
            arguments = original.arguments
        }

        assertEquals(original.arguments, recreated.arguments)
    }

    @Test
    fun settingsContinuationSurvivesRecreationAndCompletesExactlyOnce() {
        val original = CapabilityPermissionContinuationState().apply {
            begin(
                capability = AppCapability.NOTIFICATIONS,
                actionToken = "enable_notifications",
                notificationChannelId = "care_reminders"
            )
            markWaitingForSettings()
        }
        val recreated = CapabilityPermissionContinuationState().apply {
            restore(original.snapshot())
        }

        assertTrue(recreated.waitingForSettings)
        assertEquals("care_reminders", recreated.pendingNotificationChannelId)
        assertEquals(
            "enable_notifications",
            recreated.consumeSettingsReturn(isGranted = true)
        )
        assertNull(recreated.consumeSettingsReturn(isGranted = true))
        assertNull(recreated.consumeIfGranted(isGranted = true))
    }

    @Test
    fun deniedSettingsReturnClearsPendingContinuation() {
        val state = CapabilityPermissionContinuationState().apply {
            begin(
                capability = AppCapability.BLE_PROVISIONING,
                actionToken = "resume_provisioning"
            )
            markWaitingForSettings()
        }

        assertNull(state.consumeSettingsReturn(isGranted = false))
        assertFalse(state.waitingForSettings)
        assertNull(state.pendingCapability)
        assertNull(state.pendingActionToken)
    }
}
