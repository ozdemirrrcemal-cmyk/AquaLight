package com.aqua.aqualight.data.care.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CareReminderReconcileRuntimeTest {

    @Test
    fun authenticatedOwnerReconcilesExactlyOnce() = runTest {
        val events = mutableListOf<String>()

        val result = CareReminderReconcileRuntime(
            currentOwnerUid = { "owner-a" },
            reconcileOwner = { owner -> events += "reconcile:$owner" },
            cancelOwner = { owner -> events += "cancel:$owner" }
        ).run("owner-a")

        assertEquals(CareReminderReconcileRuntime.Result.COMPLETED, result)
        assertEquals(listOf("reconcile:owner-a"), events)
    }

    @Test
    fun unauthenticatedOrDifferentOwnerDoesNotTouchNotificationState() = runTest {
        val events = mutableListOf<String>()

        val result = CareReminderReconcileRuntime(
            currentOwnerUid = { "owner-b" },
            reconcileOwner = { events += "reconcile" },
            cancelOwner = { events += "cancel" }
        ).run("owner-a")

        assertEquals(
            CareReminderReconcileRuntime.Result.OWNER_NOT_ACTIVE,
            result
        )
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun accountSwitchDuringReconcileCancelsOutgoingOwner() = runTest {
        var activeOwner: String? = "owner-a"
        val events = mutableListOf<String>()

        val result = CareReminderReconcileRuntime(
            currentOwnerUid = { activeOwner },
            reconcileOwner = { owner ->
                events += "reconcile:$owner"
                activeOwner = "owner-b"
            },
            cancelOwner = { owner -> events += "cancel:$owner" }
        ).run("owner-a")

        assertEquals(CareReminderReconcileRuntime.Result.OWNER_CHANGED, result)
        assertEquals(
            listOf("reconcile:owner-a", "cancel:owner-a"),
            events
        )
    }
}
