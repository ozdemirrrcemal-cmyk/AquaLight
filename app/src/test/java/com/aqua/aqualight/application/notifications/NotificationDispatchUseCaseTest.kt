package com.aqua.aqualight.application.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDispatchUseCaseTest {

    @Test
    fun eachCommandUsesItsOwnCategoryAndRendererMethod() = runTest {
        val fixture = Fixture(preferenceEnabled = true)

        assertEquals(
            NotificationDispatchResult.POSTED,
            fixture.useCase.dispatchCareReminder(
                CareReminderNotification(
                    ownerUid = "owner-a",
                    taskId = 7L,
                    kind = CareReminderKind.FEEDING,
                    title = "Feed",
                    message = "Feed aquarium"
                )
            )
        )
        assertEquals(
            NotificationDispatchResult.POSTED,
            fixture.useCase.dispatchDeviceAlert(
                DeviceAlertNotification(
                    ownerUid = "owner-a",
                    deviceUid = "device-1",
                    title = "Offline",
                    message = "Device is offline"
                )
            )
        )
        assertEquals(
            NotificationDispatchResult.POSTED,
            fixture.useCase.dispatchDeviceUpdate(
                DeviceUpdateNotification(
                    ownerUid = "owner-a",
                    deviceUid = "device-1",
                    title = "Firmware update",
                    message = "Installing",
                    progressPercent = 50,
                    ongoing = true
                )
            )
        )

        assertEquals(
            listOf(
                "ensure",
                "evaluate:CARE_REMINDERS",
                "render:care:owner-a:7",
                "ensure",
                "evaluate:DEVICE_ALERTS",
                "render:alert:owner-a:device-1",
                "ensure",
                "evaluate:DEVICE_UPDATES",
                "render:update:owner-a:device-1:50:true"
            ),
            fixture.events
        )
    }

    @Test
    fun disabledOwnerNeverEvaluatesSystemOrRenders() = runTest {
        val fixture = Fixture(preferenceEnabled = false)

        val result = fixture.useCase.dispatchDeviceAlert(
            DeviceAlertNotification(
                ownerUid = "owner-a",
                deviceUid = "device-1",
                title = "Alert",
                message = "Message"
            )
        )

        assertEquals(NotificationDispatchResult.OWNER_PREFERENCE_DISABLED, result)
        assertEquals(emptyList<String>(), fixture.events)
    }

    @Test
    fun blockedCategoryNeverCallsRenderer() = runTest {
        val fixture = Fixture(preferenceEnabled = true)
        fixture.policy.readiness[NotificationCategory.DEVICE_UPDATES] =
            NotificationDeliveryReadiness(
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelState = NotificationChannelState.BLOCKED
            )

        val result = fixture.useCase.dispatchDeviceUpdate(
            DeviceUpdateNotification(
                ownerUid = "owner-a",
                deviceUid = "device-1",
                title = "Firmware update",
                message = "Ready"
            )
        )

        assertEquals(NotificationDispatchResult.SYSTEM_BLOCKED, result)
        assertEquals(
            listOf("ensure", "evaluate:DEVICE_UPDATES"),
            fixture.events
        )
    }

    private class Fixture(preferenceEnabled: Boolean) {
        val events = mutableListOf<String>()
        private val repository = FakeRepository(preferenceEnabled)
        val policy = FakePolicy(events)
        private val renderer = FakeRenderer(events)
        val useCase = NotificationDispatchUseCase(repository, policy, renderer)
    }

    private class FakeRepository(initial: Boolean) : NotificationPreferenceRepository {
        private val enabled = MutableStateFlow(initial)

        override fun enabledFlow(ownerUid: String): Flow<Boolean> = enabled
        override suspend fun isEnabled(ownerUid: String): Boolean = enabled.value
        override suspend fun setEnabled(ownerUid: String, enabled: Boolean) {
            this.enabled.value = enabled
        }
    }

    private class FakePolicy(
        private val events: MutableList<String>
    ) : NotificationPermissionPolicy {
        val readiness = NotificationCategory.entries.associateWith {
            NotificationDeliveryReadiness(
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelState = NotificationChannelState.ENABLED
            )
        }.toMutableMap()

        override fun ensureChannels() {
            events += "ensure"
        }

        override fun evaluate(category: NotificationCategory): NotificationDeliveryReadiness {
            events += "evaluate:${category.name}"
            return readiness.getValue(category)
        }

        override fun channelId(category: NotificationCategory): String = category.name.lowercase()
    }

    private class FakeRenderer(
        private val events: MutableList<String>
    ) : NotificationRenderer {
        override fun renderCareReminder(notification: CareReminderNotification) {
            events += "render:care:${notification.ownerUid}:${notification.taskId}"
        }

        override fun renderDeviceAlert(notification: DeviceAlertNotification) {
            events += "render:alert:${notification.ownerUid}:${notification.deviceUid}"
        }

        override fun renderDeviceUpdate(notification: DeviceUpdateNotification) {
            events += "render:update:${notification.ownerUid}:${notification.deviceUid}:" +
                "${notification.progressPercent}:${notification.ongoing}"
        }

        override fun cancelCareReminder(ownerUid: String, taskId: Long) = Unit
        override fun cancelOwner(ownerUid: String) = Unit
    }
}
