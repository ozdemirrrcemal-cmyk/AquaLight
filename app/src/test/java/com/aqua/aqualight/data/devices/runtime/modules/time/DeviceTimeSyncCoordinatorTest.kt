package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimeSyncCoordinatorTest {

    @Test
    fun synchronizesOneLivePhoneSnapshotPerDeviceSession() {
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = {
                calls.incrementAndGet()
                success()
            }
        )
        val deviceUid = DeviceUid("device-time-once")

        val first = coordinator.syncPhoneNowIfNeeded(deviceUid)
        val second = coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertTrue(first.isSuccess)
        assertTrue(second.skipped)
        assertEquals(1, calls.get())
    }

    @Test
    fun forceRerunsCompletedSynchronizationButNeverDuplicatesAnInFlightCommand() {
        val enteredSync = CountDownLatch(1)
        val releaseSync = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = {
                calls.incrementAndGet()
                enteredSync.countDown()
                assertTrue(releaseSync.await(5, TimeUnit.SECONDS))
                success()
            }
        )
        val workers = Executors.newFixedThreadPool(2)
        val results = CopyOnWriteArrayList<DeviceTimeCommandResult>()
        val deviceUid = DeviceUid("device-time-force")

        workers.execute { results += coordinator.syncPhoneNowIfNeeded(deviceUid) }
        assertTrue(enteredSync.await(5, TimeUnit.SECONDS))
        workers.execute { results += coordinator.syncPhoneNowIfNeeded(deviceUid, force = true) }
        releaseSync.countDown()
        workers.shutdown()
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, calls.get())
        assertEquals(1, results.count(DeviceTimeCommandResult::isSuccess))
        assertEquals(1, results.count(DeviceTimeCommandResult::skipped))
        assertTrue(coordinator.syncPhoneNowIfNeeded(deviceUid, force = true).isSuccess)
        assertEquals(2, calls.get())
    }

    @Test
    fun sendFailureDoesNotMarkSessionSynchronizedAndAllowsRetry() {
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = {
                calls.incrementAndGet()
                DeviceTimeCommandResult(
                    sent = false,
                    action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
                    errorMessage = "runtime unavailable"
                )
            }
        )
        val deviceUid = DeviceUid("device-time-retry")

        val first = coordinator.syncPhoneNowIfNeeded(deviceUid)
        val second = coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertFalse(first.isSuccess)
        assertFalse(second.isSuccess)
        assertEquals(2, calls.get())
    }

    @Test
    fun concurrentRequestsRunOnlyOneSynchronizationCommand() {
        val enteredSync = CountDownLatch(1)
        val releaseSync = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = {
                calls.incrementAndGet()
                enteredSync.countDown()
                assertTrue(releaseSync.await(5, TimeUnit.SECONDS))
                success()
            }
        )
        val results = CopyOnWriteArrayList<DeviceTimeCommandResult>()
        val workers = Executors.newFixedThreadPool(2)
        val deviceUid = DeviceUid("device-time-race")

        workers.execute { results += coordinator.syncPhoneNowIfNeeded(deviceUid) }
        assertTrue(enteredSync.await(5, TimeUnit.SECONDS))
        workers.execute { results += coordinator.syncPhoneNowIfNeeded(deviceUid) }
        releaseSync.countDown()
        workers.shutdown()
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, calls.get())
        assertEquals(1, results.count(DeviceTimeCommandResult::isSuccess))
        assertEquals(1, results.count(DeviceTimeCommandResult::skipped))
    }

    @Test
    fun clearingSessionMemoryAllowsADeviceToSynchronizeAgain() {
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = {
                calls.incrementAndGet()
                success()
            }
        )
        val deviceUid = DeviceUid("device-time-clear")

        assertTrue(coordinator.syncPhoneNowIfNeeded(deviceUid).isSuccess)
        coordinator.clearSessionMemory(deviceUid)
        assertTrue(coordinator.syncPhoneNowIfNeeded(deviceUid).isSuccess)

        assertEquals(2, calls.get())
    }

    private fun success(): DeviceTimeCommandResult = DeviceTimeCommandResult(
        sent = true,
        action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
        messageId = "message-id"
    )
}
