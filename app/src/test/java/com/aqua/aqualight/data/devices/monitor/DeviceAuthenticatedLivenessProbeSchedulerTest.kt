package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAuthenticatedLivenessProbeSchedulerTest {

    @Test
    fun `one device cannot start a duplicate in flight probe`() = runTest {
        val scheduler = DeviceAuthenticatedLivenessProbeScheduler()
        val gate = CompletableDeferred<Boolean>()
        var starts = 0

        assertTrue(
            scheduler.schedule(
                scope = this,
                deviceUid = DEVICE_UID,
                execute = {
                    starts += 1
                    gate.await()
                },
                onRejected = {}
            )
        )
        assertFalse(
            scheduler.schedule(
                scope = this,
                deviceUid = DEVICE_UID,
                execute = { true },
                onRejected = {}
            )
        )

        runCurrent()
        assertEquals(1, starts)
        gate.complete(true)
        advanceUntilIdle()
        assertFalse(scheduler.isActive(DEVICE_UID))
    }

    @Test
    fun `background or route cancellation drops in flight probes without proof failure`() = runTest {
        val scheduler = DeviceAuthenticatedLivenessProbeScheduler()
        val gate = CompletableDeferred<Boolean>()
        var rejected = 0
        scheduler.schedule(
            scope = this,
            deviceUid = DEVICE_UID,
            execute = { gate.await() },
            onRejected = { rejected += 1 }
        )

        runCurrent()
        scheduler.cancelAll()
        advanceUntilIdle()

        assertFalse(scheduler.isActive(DEVICE_UID))
        assertEquals(0, rejected)
    }

    @Test
    fun `completed rejected probe clears cadence and scheduler ownership`() = runTest {
        val scheduler = DeviceAuthenticatedLivenessProbeScheduler()
        var rejected = 0
        scheduler.schedule(
            scope = this,
            deviceUid = DEVICE_UID,
            execute = { false },
            onRejected = { rejected += 1 }
        )

        advanceUntilIdle()

        assertEquals(1, rejected)
        assertFalse(scheduler.isActive(DEVICE_UID))
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIVENESS-SCHEDULER")
    }
}
