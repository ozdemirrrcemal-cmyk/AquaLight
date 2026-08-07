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
    fun enablingCommitsOwnerPreferenceThenEnsuresChannelsAndReconciles() = runTest {
        val fixture = Fixture(initialEnabled = false)

        fixture.useCase.setEnabled("owner-a", true)

        assertTrue(fixture.repository.isEnabled("owner-a"))
        assertEquals(
            listOf(
                "preference:true",
                "ensure-channels",
                "reconcile:owner-a",
                "background-schedule:owner-a:true"
            ),
            fixture.events
        )
    }

    @Test
    fun disablingCancelsBackgroundSchedulesAndVisibleNotificationsForRequestedOwner() = runTest {
        val fixture = Fixture(initialEnabled = true)

        fixture.useCase.setEnabled("owner-a", false)

        assertFalse(fixture.repository.isEnabled("owner-a"))
        assertEquals(
            listOf(
                "preference:false",
                "background-cancel:owner-a",
                "cancel-owner-schedules:owner-a",
                "cancel-owner-visible:owner-a"
            ),
            fixture.events
        )
    }

    @Test
    fun disabledPreferenceCannotBeResurrectedByBackgroundReconciliation() = runTest {
        val fixture = Fixture(initialEnabled = false)

        fixture.useCase.reconcileBackgroundWork("owner-a")

        assertEquals(listOf("background-cancel:owner-a"), fixture.events)
    }

    @Test
    fun activeSessionCanRefreshPeriodicWorkWithoutReplacingCurrentImmediateWork() = runTest {
        val fixture = Fixture(initialEnabled = true)

        fixture.useCase.reconcileBackgroundWork(
            ownerUid = "owner-a",
            enqueueImmediate = false
        )

        assertEquals(listOf("background-schedule:owner-a:false"), fixture.events)
    }

    @Test
    fun careReconciliationDoesNotScheduleFirmwareBackgroundWork() = runTest {
        val fixture = Fixture(initialEnabled = true)

        fixture.useCase.reconcileOwner("owner-a")

        assertEquals(listOf("ensure-channels", "reconcile:owner-a"), fixture.events)
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
    fun schedulingWhilePreferenceDisabledCancelsAlarmAndVisibleReminder() = runTest {
        val fixture = Fixture(initialEnabled = false)

        fixture.useCase.scheduleCareTask("owner-a", 42L)

        assertEquals(
            listOf("cancel-care-schedule:owner-a:42", "cancel-care-visible:owner-a:42"),
            fixture.events
        )
    }

    private class Fixture(initialEnabled: Boolean) {
        val events = mutableListOf<String>()
        val repository = FakeRepository(initialEnabled, events)
        val policy = FakePolicy(events)
        private val scheduler = FakeScheduler(events)
        private val renderer = FakeRenderer(events)
        private val backgroundWork = FakeBackgroundWork(events)
        val useCase = NotificationPreferenceUseCase(
            repository = repository,
            permissionPolicy = policy,
            scheduler = scheduler,
            renderer = renderer,
            backgroundWork = backgroundWork
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

    private class FakeBackgroundWork(
        private val events: MutableList<String>
    ) : NotificationBackgroundWorkController {
        override fun scheduleOwner(ownerUid: String, enqueueImmediate: Boolean) {
            events += "background-schedule:$ownerUid:$enqueueImmediate"
        }

        override fun cancelOwner(ownerUid: String) {
            events += "background-cancel:$ownerUid"
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
