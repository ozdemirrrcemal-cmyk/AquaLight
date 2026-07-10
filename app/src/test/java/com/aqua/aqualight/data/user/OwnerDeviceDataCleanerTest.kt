package com.aqua.aqualight.data.user

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerDeviceDataCleanerTest {

    @Test
    fun activeOwnerCleanup_executesEveryOwnerScopedOperation() = runBlocking {
        val calls = mutableListOf<String>()
        val cleaner = OwnerDeviceDataCleaner(
            stopActiveSession = {
                calls += "stop"
            },
            clearAssignments = { ownerUid ->
                calls += "assignments:$ownerUid"
            },
            clearKnownDevices = { ownerUid ->
                calls += "known:$ownerUid"
            },
            clearIgnoredDevices = { ownerUid ->
                calls += "ignored:$ownerUid"
            },
            clearCredentials = { ownerUid ->
                calls += "credentials:$ownerUid"
            }
        )

        val failures = cleaner.clear(
            ownerUid = " owner-a ",
            activeOwnerMatchesTarget = true
        )

        assertTrue(failures.isEmpty())
        assertEquals(
            listOf(
                "stop",
                "assignments:owner-a",
                "known:owner-a",
                "ignored:owner-a",
                "credentials:owner-a"
            ),
            calls
        )
    }

    @Test
    fun inactiveOwnerCleanup_doesNotStopCurrentSession() = runBlocking {
        val calls = mutableListOf<String>()
        val cleaner = OwnerDeviceDataCleaner(
            stopActiveSession = {
                calls += "stop"
            },
            clearAssignments = { ownerUid -> calls += "assignments:$ownerUid" },
            clearKnownDevices = { ownerUid -> calls += "known:$ownerUid" },
            clearIgnoredDevices = { ownerUid -> calls += "ignored:$ownerUid" },
            clearCredentials = { ownerUid -> calls += "credentials:$ownerUid" }
        )

        val failures = cleaner.clear(
            ownerUid = "owner-b",
            activeOwnerMatchesTarget = false
        )

        assertTrue(failures.isEmpty())
        assertEquals(
            listOf(
                "assignments:owner-b",
                "known:owner-b",
                "ignored:owner-b",
                "credentials:owner-b"
            ),
            calls
        )
    }

    @Test
    fun cleanupFailure_doesNotPreventRemainingOperations() = runBlocking {
        val calls = mutableListOf<String>()
        val cleaner = OwnerDeviceDataCleaner(
            stopActiveSession = {
                calls += "stop"
                error("stop failed")
            },
            clearAssignments = { ownerUid ->
                calls += "assignments:$ownerUid"
                error("assignment failed")
            },
            clearKnownDevices = { ownerUid -> calls += "known:$ownerUid" },
            clearIgnoredDevices = { ownerUid -> calls += "ignored:$ownerUid" },
            clearCredentials = { ownerUid -> calls += "credentials:$ownerUid" }
        )

        val failures = cleaner.clear(
            ownerUid = "owner-a",
            activeOwnerMatchesTarget = true
        )

        assertEquals(2, failures.size)
        assertEquals(
            listOf(
                OwnerDeviceCleanupOperation.STOP_ACTIVE_SESSION,
                OwnerDeviceCleanupOperation.CLEAR_ASSIGNMENTS
            ),
            failures.map { failure -> failure.operation }
        )
        assertEquals(
            listOf(
                "stop",
                "assignments:owner-a",
                "known:owner-a",
                "ignored:owner-a",
                "credentials:owner-a"
            ),
            calls
        )
    }
}
