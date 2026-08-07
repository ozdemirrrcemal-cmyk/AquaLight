package com.aqua.aqualight.data.devices.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDeviceUpdateNotificationWorkCoordinatorTest {

    @Test
    fun enabledOwnerSchedulesImmediateAndPeriodicWork() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            enabled = true,
            events = events
        )

        coordinator.reconcileOwner("owner-a")

        assertEquals(listOf("schedule:owner-a"), events)
    }

    @Test
    fun disabledOwnerCancelsExistingWork() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            enabled = false,
            events = events
        )

        coordinator.reconcileOwner("owner-a")

        assertEquals(listOf("cancel:owner-a"), events)
    }

    @Test
    fun explicitOwnerCancellationDoesNotReadPreference() {
        val events = mutableListOf<String>()
        var preferenceReads = 0
        val coordinator = DefaultDeviceUpdateNotificationWorkCoordinator(
            isEnabled = {
                preferenceReads += 1
                true
            },
            scheduleWork = { ownerUid -> events += "schedule:$ownerUid" },
            cancelWork = { ownerUid -> events += "cancel:$ownerUid" }
        )

        coordinator.cancelOwner("owner-a")

        assertEquals(0, preferenceReads)
        assertEquals(listOf("cancel:owner-a"), events)
    }

    private fun coordinator(
        enabled: Boolean,
        events: MutableList<String>
    ): DefaultDeviceUpdateNotificationWorkCoordinator {
        return DefaultDeviceUpdateNotificationWorkCoordinator(
            isEnabled = { enabled },
            scheduleWork = { ownerUid -> events += "schedule:$ownerUid" },
            cancelWork = { ownerUid -> events += "cancel:$ownerUid" }
        )
    }
}
