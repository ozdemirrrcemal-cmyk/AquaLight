package com.aqua.aqualight.application.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationLifecycleUseCaseTest {

    @Test
    fun `device update cancellation delegates owner and device unchanged`() {
        val events = mutableListOf<String>()
        val renderer = RecordingRenderer(events)
        val useCase = NotificationLifecycleUseCase(renderer)

        useCase.cancelDeviceUpdate(OWNER_UID, DEVICE_UID)

        assertEquals(listOf("cancel-update:$OWNER_UID:$DEVICE_UID"), events)
    }

    @Test
    fun `renderer default targeted cancellation remains source compatible`() {
        val renderer = object : NotificationRenderer {
            override fun renderCareReminder(notification: CareReminderNotification) = Unit
            override fun renderDeviceAlert(notification: DeviceAlertNotification) = Unit
            override fun renderDeviceUpdate(notification: DeviceUpdateNotification) = Unit
            override fun cancelCareReminder(ownerUid: String, taskId: Long) = Unit
            override fun cancelOwner(ownerUid: String) = Unit
        }

        renderer.cancelDeviceUpdate(OWNER_UID, DEVICE_UID)
    }

    private class RecordingRenderer(
        private val events: MutableList<String>
    ) : NotificationRenderer {
        override fun renderCareReminder(notification: CareReminderNotification) = Unit
        override fun renderDeviceAlert(notification: DeviceAlertNotification) = Unit
        override fun renderDeviceUpdate(notification: DeviceUpdateNotification) = Unit
        override fun cancelCareReminder(ownerUid: String, taskId: Long) = Unit

        override fun cancelDeviceUpdate(ownerUid: String, deviceUid: String) {
            events += "cancel-update:$ownerUid:$deviceUid"
        }

        override fun cancelOwner(ownerUid: String) = Unit
    }

    private companion object {
        const val OWNER_UID = "owner-a"
        const val DEVICE_UID = "device-a"
    }
}
