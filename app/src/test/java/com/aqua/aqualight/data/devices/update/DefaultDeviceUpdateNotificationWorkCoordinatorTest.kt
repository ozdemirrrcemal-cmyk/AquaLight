package com.aqua.aqualight.data.devices.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDeviceUpdateNotificationWorkCoordinatorTest {

    @Test
    fun notificationsAndAutomaticChecksEnabledSchedulesWork() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            notificationsEnabled = true,
            automaticChecksEnabled = true,
            events = events
        )

        coordinator.reconcileOwner("owner-a")

        assertEquals(listOf("schedule:owner-a"), events)
    }

    @Test
    fun notificationsDisabledCancelsWorkEvenWhenAutomaticChecksEnabled() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            notificationsEnabled = false,
            automaticChecksEnabled = true,
            events = events
        )

        coordinator.reconcileOwner("owner-a")

        assertEquals(listOf("cancel:owner-a"), events)
    }

    @Test
    fun automaticChecksDisabledCancelsWorkEvenWhenNotificationsEnabled() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            notificationsEnabled = true,
            automaticChecksEnabled = false,
            events = events
        )

        coordinator.reconcileOwner("owner-a")

        assertEquals(listOf("cancel:owner-a"), events)
    }

    @Test
    fun explicitOwnerCancellationDoesNotReadPolicy() {
        val events = mutableListOf<String>()
        var notificationPreferenceReads = 0
        var automaticCheckPreferenceReads = 0
        val coordinator = DefaultDeviceUpdateNotificationWorkCoordinator(
            areNotificationsEnabled = {
                notificationPreferenceReads += 1
                true
            },
            isAutomaticCheckEnabled = {
                automaticCheckPreferenceReads += 1
                true
            },
            scheduleWork = { ownerUid -> events += "schedule:$ownerUid" },
            cancelWork = { ownerUid -> events += "cancel:$ownerUid" }
        )

        coordinator.cancelOwner("owner-a")

        assertEquals(0, notificationPreferenceReads)
        assertEquals(0, automaticCheckPreferenceReads)
        assertEquals(listOf("cancel:owner-a"), events)
    }

    private fun coordinator(
        notificationsEnabled: Boolean,
        automaticChecksEnabled: Boolean,
        events: MutableList<String>
    ): DefaultDeviceUpdateNotificationWorkCoordinator {
        return DefaultDeviceUpdateNotificationWorkCoordinator(
            areNotificationsEnabled = { notificationsEnabled },
            isAutomaticCheckEnabled = { automaticChecksEnabled },
            scheduleWork = { ownerUid -> events += "schedule:$ownerUid" },
            cancelWork = { ownerUid -> events += "cancel:$ownerUid" }
        )
    }
}
