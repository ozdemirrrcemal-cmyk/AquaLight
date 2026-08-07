package com.aqua.aqualight.application.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferenceUseCaseTest {

    @Test
    fun enablingCommitsPreferenceBeforeDeviceAndCareReconciliation() = runTest {
        val fixture = Fixture(initialEnabled = false)

        fixture.useCase.setEnabled("owner-a", true)

        assertTrue(fixture.repository.isEnabled("owner-a"))
        assertEquals(
            listOf(
                "preference:true",
                "ensure-channels",
                "reconcile-device-update-work:owner-a",
                "reconcile:owner-a"
            ),
            fixture.events
        )
    }

    @Test
    fun disablingCancelsDeviceWorkSchedulesAndVisibleNotifications() = runTest {
        val fixture = Fixture(initialEnabled = true)

        fixture.useCase.setEnabled("owner-a", false)

        assertFalse(fixture.repository.isEnabled("owner-a"))
        assertEquals(
            listOf(
                "preference:false",
                "cancel-device-update-work:owner-a",
                "cancel-owner-schedules:owner-a",
                "cancel-owner-visible:owner-a"
            ),
            fixture.events
        )
    }

    @Test
    fun snapshotKeepsPreferencePermissionAppAndEachChannelIndependent() = runTest {
        val fixture = Fixture(initialEnabled = true)
        fixture.policy.readinessByCategory[NotificationCategory.CARE_REMINDERS] =
            deliveryReadiness(
                runtime = true,
                app = true,
                channel = NotificationChannelState.ENABLED
            )
        fixture.policy.readinessByCategory[NotificationCategory.DEVICE_ALERTS] =
            deliveryReadiness(
                runtime = true,
                app = true,
                channel = NotificationChannelState.BLOCKED
            )
        fixture.policy.readinessByCategory[NotificationCategory.DEVICE_UPDATES] =
            deliveryReadiness(
                runtime = false,
                app = true,
                channel = NotificationChannelState.ENABLED
            )

        val snapshot = fixture.useCase.snapshot("owner-a")

        assertTrue(snapshot.ownerPreferenceEnabled)
        assertTrue(snapshot.readiness(NotificationCategory.CARE_REMINDERS).canDeliver)
        assertFalse(snapshot.readiness(NotificationCategory.DEVICE_ALERTS).canDeliver)
        assertFalse(snapshot.readiness(NotificationCategory.DEVICE_UPDATES).canDeliver)
        assertFalse(snapshot.allCategoriesDeliverable)
    }

    @Test
    fun enabledCareReconciliationDoesNotRescheduleFirmwareDiscovery() = runTest {
        val fixture = Fixture(initialEnabled = true)

        fixture.useCase.reconcileOwner("owner-a")

        assertEquals(
            listOf("ensure-channels", "reconcile:owner-a"),
            fixture.events
        )
    }

    @Test
    fun disabledReconciliationCancelsFirmwareWorkAndVisibleState() = runTest {
        val fixture = Fixture(initialEnabled = false)

        fixture.useCase.reconcileOwner("owner-a")

        assertEquals(
            listOf(
                "cancel-device-update-work:owner-a",
                "cancel-owner-schedules:owner-a",
                "cancel-owner-visible:owner-a"
            ),
            fixture.events
        )
    }

    @Test
    fun schedulingWhilePreferenceDisabledCancelsAlarmAndVisibleReminder() = runTest {
        val fixture = Fixture(initialEnabled = false)

        fixture.useCase.scheduleCareTask("owner-a", 42L)

        assertEquals(
            listOf("cancel-care-schedule:owner-a:42", "cancel-care-visible:owner-a:42"),
            fixture.events
        )
    }

    @Test
    fun ownerCancellationStopsDeviceWorkBeforeOtherNotificationState() = runTest {
        val fixture = Fixture(initialEnabled = true)

        fixture.useCase.cancelOwner("owner-a")

        assertEquals(
            listOf(
                "cancel-device-update-work:owner-a",
                "cancel-owner-schedules:owner-a",
                "cancel-owner-visible:owner-a"
            ),
            fixture.events
        )
    }

    private class Fixture(initialEnabled: Boolean) {
        val events = mutableListOf<String>()
        val repository = FakeRepository(initialEnabled, events)
        val policy = FakePolicy(events)
        private val scheduler = FakeScheduler(events)
        private val deviceWork = FakeDeviceUpdateWorkCoordinator(events)
        private val renderer = FakeRenderer(events)
        val useCase = NotificationPreferenceUseCase(
            repository = repository,
            permissionPolicy = policy,
            scheduler = scheduler,
            deviceUpdateWorkCoordinator = deviceWork,
            renderer = renderer
        )
    }

    private class FakeRepository(
        initialEnabled: Boolean,
        private val events: MutableList<String>
    ) : NotificationPreferenceRepository {
        private val values = mutableMapOf("owner-a" to MutableStateFlow(initialEnabled))

        override fun enabledFlow(ownerUid: String): Flow<Boolean> {
            return values.getOrPut(ownerUid) { MutableStateFlow(false) }
        }

        override suspend fun isEnabled(ownerUid: String): Boolean {
            return values.getOrPut(ownerUid) { MutableStateFlow(false) }.value
        }

        override suspend fun setEnabled(ownerUid: String, enabled: Boolean) {
            values.getOrPut(ownerUid) { MutableStateFlow(false) }.value = enabled
            events += "preference:$enabled"
        }
    }

    private class FakePolicy(
        private val events: MutableList<String>
    ) : NotificationPermissionPolicy {
        val readinessByCategory = NotificationCategory.entries.associateWith {
            deliveryReadiness(true, true, NotificationChannelState.ENABLED)
        }.toMutableMap()

        override fun ensureChannels() {
            events += "ensure-channels"
        }

        override fun evaluate(category: NotificationCategory): NotificationDeliveryReadiness {
            return readinessByCategory.getValue(category)
        }

        override fun channelId(category: NotificationCategory): String = category.name.lowercase()
    }

    private class FakeScheduler(
        private val events: MutableList<String>
    ) : NotificationScheduler {
        override suspend fun scheduleCareTask(ownerUid: String, taskId: Long) {
            events += "schedule-care:$ownerUid:$taskId"
        }

        override suspend fun cancelCareTask(ownerUid: String, taskId: Long) {
            events += "cancel-care-schedule:$ownerUid:$taskId"
        }

        override suspend fun reconcileOwner(ownerUid: String) {
            events += "reconcile:$ownerUid"
        }

        override suspend fun cancelOwner(ownerUid: String) {
            events += "cancel-owner-schedules:$ownerUid"
        }
    }

    private class FakeDeviceUpdateWorkCoordinator(
        private val events: MutableList<String>
    ) : DeviceUpdateNotificationWorkCoordinator {
        override suspend fun reconcileOwner(ownerUid: String) {
            events += "reconcile-device-update-work:$ownerUid"
        }

        override fun cancelOwner(ownerUid: String) {
            events += "cancel-device-update-work:$ownerUid"
        }
    }

    private class FakeRenderer(
        private val events: MutableList<String>
    ) : NotificationRenderer {
        override fun renderCareReminder(notification: CareReminderNotification) = Unit
        override fun renderDeviceAlert(notification: DeviceAlertNotification) = Unit
        override fun renderDeviceUpdate(notification: DeviceUpdateNotification) = Unit

        override fun cancelCareReminder(ownerUid: String, taskId: Long) {
            events += "cancel-care-visible:$ownerUid:$taskId"
        }

        override fun cancelOwner(ownerUid: String) {
            events += "cancel-owner-visible:$ownerUid"
        }
    }

    private companion object {
        fun deliveryReadiness(
            runtime: Boolean,
            app: Boolean,
            channel: NotificationChannelState
        ): NotificationDeliveryReadiness {
            return NotificationDeliveryReadiness(
                runtimePermissionGranted = runtime,
                appNotificationsEnabled = app,
                channelState = channel
            )
        }
    }
}
