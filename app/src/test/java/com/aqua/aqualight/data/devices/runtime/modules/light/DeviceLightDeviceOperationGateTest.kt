package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLightDeviceOperationGateTest {
    @Test
    fun `pending mutation supersedes earlier queued refresh without cancelling caller`() = runTest {
        val gate = DeviceLightDeviceOperationGate()
        val release = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { gate.withDevice(deviceUid) { release.await() } }
        var readDispatched = false
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            val result = gate.withRefresh(deviceUid) { readDispatched = true; "read" }
            assertTrue(currentCoroutineContext().isActive)
            result
        }
        val write = async(start = CoroutineStart.UNDISPATCHED) { gate.withMutation(deviceUid) { "ack" } }
        release.complete(Unit)

        owner.await()
        assertNull(refresh.await())
        assertFalse(readDispatched)
        assertEquals("ack", write.await())
        assertEquals("next read", gate.withRefresh(deviceUid) { "next read" })
    }

    @Test
    fun `cancelled queued mutation does not leave refresh permanently excluded`() = runTest {
        val gate = DeviceLightDeviceOperationGate()
        val release = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { gate.withDevice(deviceUid) { release.await() } }
        var writeDispatched = false
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withMutation(deviceUid) { writeDispatched = true }
        }
        write.cancelAndJoin()
        release.complete(Unit)
        owner.await()

        assertFalse(writeDispatched)
        assertEquals("read", gate.withRefresh(deviceUid) { "read" })
    }

    @Test
    fun `write waits for cancelled read cleanup but does not wait for read timeout`() = runTest {
        val gate = DeviceLightDeviceOperationGate()
        var cleanupComplete = false
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withRefresh(deviceUid) {
                try {
                    delay(READ_TIMEOUT_MILLIS)
                    "read"
                } finally {
                    withContext(NonCancellable) { delay(CLEANUP_MILLIS) }
                    cleanupComplete = true
                }
            }
        }
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withMutation(deviceUid) {
                assertTrue(cleanupComplete)
                testScheduler.currentTime
            }
        }
        testScheduler.runCurrent()
        assertFalse(write.isCompleted)
        testScheduler.advanceTimeBy(CLEANUP_MILLIS)
        testScheduler.runCurrent()

        assertNull(refresh.await())
        assertEquals(CLEANUP_MILLIS, write.await())
        assertTrue(testScheduler.currentTime < READ_TIMEOUT_MILLIS)
    }

    @Test
    fun `ordinary caller cancellation propagates and frees gate for subsequent write`() = runTest {
        val gate = DeviceLightDeviceOperationGate()
        var readFinished = false
        val caller = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withRefresh(deviceUid) {
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    readFinished = true
                }
            }
            error("Cancelled caller must not continue")
        }
        caller.cancelAndJoin()

        assertTrue(caller.isCancelled)
        assertTrue(readFinished)
        assertEquals("ack", gate.withMutation(deviceUid) { "ack" })
    }

    @Test
    fun `simultaneous preemption does not swallow caller cancellation`() = runTest {
        val gate = DeviceLightDeviceOperationGate()
        val caller = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withRefresh(deviceUid) { CompletableDeferred<Unit>().await() }
            error("Cancelled caller must not continue")
        }
        val write = async(start = CoroutineStart.UNDISPATCHED) { gate.withMutation(deviceUid) { "ack" } }
        caller.cancelAndJoin()

        assertTrue(caller.isCancelled)
        assertEquals("ack", write.await())
        assertEquals("read", gate.withRefresh(deviceUid) { "read" })
    }

    private companion object {
        val deviceUid = DeviceUid("operation-gate")
        const val READ_TIMEOUT_MILLIS = 8_000L
        const val CLEANUP_MILLIS = 50L
    }
}
