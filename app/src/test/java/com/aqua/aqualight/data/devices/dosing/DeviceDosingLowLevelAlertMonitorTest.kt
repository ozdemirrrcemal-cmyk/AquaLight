package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingLowLevelAlertCopy
import com.aqua.aqualight.application.notifications.CareReminderNotification
import com.aqua.aqualight.application.notifications.DeviceAlertNotification
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationDeliveryReadiness
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.application.notifications.NotificationPermissionPolicy
import com.aqua.aqualight.application.notifications.NotificationPreferenceRepository
import com.aqua.aqualight.application.notifications.NotificationRenderer
import com.aqua.aqualight.application.notifications.NotificationChannelState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.sampleDosingChannelSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingLowLevelAlertMonitorTest {

    @Test
    fun `false to true transition dispatches once and survives monitor recreation`() = runTest {
        val ledger = InMemoryDeviceDosingLowLevelAlertLedger().apply {
            setEnabled(DEVICE_UID, SLOT_ID, true)
        }
        val renderer = RecordingRenderer()
        val dispatch = dispatchUseCase(renderer = renderer)
        val monitor = monitor(ledger, dispatch)
        val normal = sampleDosingChannelSnapshot().copy(
            reservoir = sampleDosingChannelSnapshot().reservoir.copy(
                lowLevelAlertEnabled = true,
                lowLevelActive = false
            )
        )
        val low = normal.copy(reservoir = normal.reservoir.copy(lowLevelActive = true))

        monitor.reconcile(normal)
        monitor.reconcile(low)
        monitor.reconcile(low)
        monitor(ledger, dispatch).reconcile(low)

        assertEquals(1, renderer.deviceAlerts.size)

        monitor.reconcile(normal)
        monitor.reconcile(low)

        assertEquals(2, renderer.deviceAlerts.size)
    }

    @Test
    fun `owner preference and android readiness remain central dispatch gates`() = runTest {
        val snapshot = sampleDosingChannelSnapshot().copy(
            reservoir = sampleDosingChannelSnapshot().reservoir.copy(
                lowLevelAlertEnabled = true,
                lowLevelActive = false
            )
        )
        val low = snapshot.copy(reservoir = snapshot.reservoir.copy(lowLevelActive = true))

        val ownerBlockedLedger = InMemoryDeviceDosingLowLevelAlertLedger().apply {
            setEnabled(DEVICE_UID, SLOT_ID, true)
        }
        val ownerBlockedRenderer = RecordingRenderer()
        val ownerBlocked = monitor(
            ownerBlockedLedger,
            dispatchUseCase(renderer = ownerBlockedRenderer, ownerEnabled = false)
        )
        ownerBlocked.reconcile(snapshot)
        ownerBlocked.reconcile(low)
        ownerBlocked.reconcile(low)
        assertEquals(0, ownerBlockedRenderer.deviceAlerts.size)

        val androidBlockedLedger = InMemoryDeviceDosingLowLevelAlertLedger().apply {
            setEnabled(DEVICE_UID, SLOT_ID, true)
        }
        val androidBlockedRenderer = RecordingRenderer()
        val androidBlocked = monitor(
            androidBlockedLedger,
            dispatchUseCase(renderer = androidBlockedRenderer, deliveryReady = false)
        )
        androidBlocked.reconcile(snapshot)
        androidBlocked.reconcile(low)
        androidBlocked.reconcile(low)
        assertEquals(0, androidBlockedRenderer.deviceAlerts.size)
    }

    private fun monitor(
        ledger: DeviceDosingLowLevelAlertLedger,
        dispatch: NotificationDispatchUseCase
    ) = DeviceDosingLowLevelAlertMonitor(
        ownerUid = OWNER_UID,
        ledger = ledger,
        notificationDispatch = dispatch,
        textResolver = { channelTitle ->
            DeviceDosingLowLevelAlertCopy("Low reservoir", "$channelTitle is low")
        }
    )

    private fun dispatchUseCase(
        renderer: RecordingRenderer,
        ownerEnabled: Boolean = true,
        deliveryReady: Boolean = true
    ) = NotificationDispatchUseCase(
        repository = object : NotificationPreferenceRepository {
            override fun enabledFlow(ownerUid: String): Flow<Boolean> = flowOf(ownerEnabled)
            override suspend fun isEnabled(ownerUid: String): Boolean = ownerEnabled
            override suspend fun setEnabled(ownerUid: String, enabled: Boolean) = Unit
        },
        permissionPolicy = object : NotificationPermissionPolicy {
            override fun ensureChannels() = Unit
            override fun evaluate(category: NotificationCategory) = NotificationDeliveryReadiness(
                runtimePermissionGranted = deliveryReady,
                appNotificationsEnabled = deliveryReady,
                channelState = if (deliveryReady) {
                    NotificationChannelState.ENABLED
                } else {
                    NotificationChannelState.BLOCKED
                }
            )
            override fun channelId(category: NotificationCategory): String = category.name
        },
        renderer = renderer
    )

    private class RecordingRenderer : NotificationRenderer {
        val deviceAlerts = mutableListOf<DeviceAlertNotification>()
        override fun renderCareReminder(notification: CareReminderNotification) = Unit
        override fun renderDeviceAlert(notification: DeviceAlertNotification) {
            deviceAlerts += notification
        }
        override fun renderDeviceUpdate(notification: DeviceUpdateNotification) = Unit
        override fun cancelCareReminder(ownerUid: String, taskId: Long) = Unit
        override fun cancelOwner(ownerUid: String) = Unit
    }

    private companion object {
        const val OWNER_UID = "owner-1"
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel2"
    }
}
