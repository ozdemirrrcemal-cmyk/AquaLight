package com.aqua.aqualight.data.care.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CareReminderReconcileRuntimeTest {

    @Test
    fun authenticatedOwnerSynchronizesPreferenceAndReconciles() = runTest {
        var activeOwner: String? = "owner-a"
        val events = mutableListOf<String>()

        val result = CareReminderReconcileRuntime(
            currentOwnerUid = { activeOwner },
            loadOwnerPreference = { owner ->
                events += "load:$owner"
                true
            },
            syncActiveProjection = { enabled ->
                events += "projection:$enabled"
            },
            reconcileOwner = { owner ->
                events += "reconcile:$owner"
            },
            cancelOwner = { owner ->
                events += "cancel:$owner"
            }
        ).run("owner-a")

        assertEquals(CareReminderReconcileRuntime.Result.COMPLETED, result)
        assertEquals(
            listOf("load:owner-a", "projection:true", "reconcile:owner-a"),
            events
        )
        activeOwner = null
    }

    @Test
    fun unauthenticatedOrDifferentOwnerDoesNotReadStores() = runTest {
        val events = mutableListOf<String>()

        val result = CareReminderReconcileRuntime(
            currentOwnerUid = { "owner-b" },
            loadOwnerPreference = {
                events += "load"
                true
            },
            syncActiveProjection = { events += "projection" },
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
    fun accountSwitchDuringPreferenceProjectionCancelsOldOwner() = runTest {
        var activeOwner: String? = "owner-a"
        val events = mutableListOf<String>()

        val result = CareReminderReconcileRuntime(
            currentOwnerUid = { activeOwner },
            loadOwnerPreference = { true },
            syncActiveProjection = {
                events += "projection"
                activeOwner = "owner-b"
            },
            reconcileOwner = { events += "reconcile" },
            cancelOwner = { owner -> events += "cancel:$owner" }
        ).run("owner-a")

        assertEquals(CareReminderReconcileRuntime.Result.OWNER_CHANGED, result)
        assertEquals(listOf("projection", "cancel:owner-a"), events)
    }

    @Test
    fun accountSwitchAfterReconcileCancelsOldOwnerAlarms() = runTest {
        var activeOwner: String? = "owner-a"
        val events = mutableListOf<String>()

        val result = CareReminderReconcileRuntime(
            currentOwnerUid = { activeOwner },
            loadOwnerPreference = { true },
            syncActiveProjection = { events += "projection" },
            reconcileOwner = { owner ->
                events += "reconcile:$owner"
                activeOwner = "owner-b"
            },
            cancelOwner = { owner -> events += "cancel:$owner" }
        ).run("owner-a")

        assertEquals(CareReminderReconcileRuntime.Result.OWNER_CHANGED, result)
        assertEquals(
            listOf("projection", "reconcile:owner-a", "cancel:owner-a"),
            events
        )
    }
}
